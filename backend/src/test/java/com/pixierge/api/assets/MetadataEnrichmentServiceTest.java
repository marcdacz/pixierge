package com.pixierge.api.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobCreate;
import com.pixierge.api.background.BackgroundJobRecord;
import com.pixierge.api.background.BackgroundJobService;
import com.pixierge.api.scans.ScanJobTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataEnrichmentServiceTest {

    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID ASSET_FILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000602");
    private static final OffsetDateTime MODIFIED_AT = OffsetDateTime.parse("2026-07-13T00:00:00Z");

    @TempDir
    private Path tempDir;

    private FakeAssetRepository assetRepository;
    private RecordingBackgroundJobService backgroundJobService;
    private MetadataEnrichmentService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        assetRepository = new FakeAssetRepository();
        backgroundJobService = new RecordingBackgroundJobService();
        service = new MetadataEnrichmentService(
                assetRepository,
                backgroundJobService,
                new ImmediateTransactionTemplate(),
                objectMapper,
                "missing-ffprobe-for-test",
                1
        );
    }

    @Test
    void enqueueMetadataBackfillCreatesDurableMetadataJobs() throws Exception {
        Path file = Files.writeString(tempDir.resolve("clip.mp4"), "video");
        assetRepository.candidates = List.of(candidate(file, "video"));

        AdminBatchActionResponse response = service.enqueueMetadataBackfill(10);

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        BackgroundJobCreate job = backgroundJobService.jobs.getFirst();
        assertThat(job.jobType()).isEqualTo(ScanJobTypes.ASSET_METADATA_BACKFILL);
        assertThat(job.concurrencyKey()).isEqualTo(ScanJobTypes.ASSET_METADATA_BACKFILL);
        AssetMetadataJobPayload payload = objectMapper.readValue(job.payloadJson(), AssetMetadataJobPayload.class);
        assertThat(payload.assetId()).isEqualTo(ASSET_ID);
        assertThat(payload.assetFileId()).isEqualTo(ASSET_FILE_ID);
        assertThat(payload.sizeBytes()).isEqualTo(Files.size(file));
    }

    @Test
    void queuedUnsupportedMetadataIsPersistedAndSearchIsRefreshed() throws Exception {
        Path file = Files.writeString(tempDir.resolve("notes.txt"), "not media");
        AssetRepository.MetadataCandidateRow candidate = candidate(file, "document");
        assetRepository.activeCandidates.put(ASSET_FILE_ID, candidate);
        assetRepository.searchTextByAsset.put(ASSET_ID, "notes txt");

        service.extractQueuedMetadata(new AssetMetadataJobPayload(
                ASSET_ID,
                ASSET_FILE_ID,
                candidate.normalizedPath(),
                candidate.fileName(),
                candidate.sizeBytes(),
                candidate.modifiedAt(),
                candidate.mediaType()
        ));

        assertThat(assetRepository.metadataUpdates)
                .extracting(AssetRepository.MetadataUpdate::extractionStatus)
                .containsExactly("processing", "unsupported");
        AssetRepository.MetadataUpdate update = assetRepository.metadataUpdates.get(1);
        assertThat(update.errorCode()).isEqualTo("unsupported_media");
        assertThat(update.sourceFileSize()).isEqualTo(Files.size(file));
        assertThat(assetRepository.searchUpserts).containsExactly(ASSET_ID);
    }

    @Test
    void recoveryRequeuesDeadLetterMetadataAndMakesItRetryable() throws Exception {
        Path file = Files.writeString(tempDir.resolve("recover.jpg"), "not an image");
        AssetRepository.MetadataCandidateRow candidate = candidate(file, "image");
        assetRepository.activeCandidates.put(ASSET_FILE_ID, candidate);
        backgroundJobService.deadLetterJobs = List.of(new BackgroundJobRecord(
                UUID.randomUUID(),
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                objectMapper.writeValueAsString(new AssetMetadataJobPayload(
                        ASSET_ID,
                        ASSET_FILE_ID,
                        candidate.normalizedPath(),
                        candidate.fileName(),
                        candidate.sizeBytes(),
                        candidate.modifiedAt(),
                        candidate.mediaType()
                )),
                "dead_letter",
                0,
                3,
                3,
                MODIFIED_AT,
                null,
                null,
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                "metadata:" + ASSET_ID,
                null,
                "NullPointerException",
                "keywords were missing",
                MODIFIED_AT,
                MODIFIED_AT,
                MODIFIED_AT
        ));

        AdminBatchActionResponse response = service.recoverDeadLetterMetadata();

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(assetRepository.failedAssetIds).containsExactly(ASSET_ID);
        assertThat(backgroundJobService.jobs).singleElement().satisfies(job ->
                assertThat(job.jobType()).isEqualTo(ScanJobTypes.ASSET_METADATA_BACKFILL));
    }

    private AssetRepository.MetadataCandidateRow candidate(Path file, String mediaType) throws Exception {
        return new AssetRepository.MetadataCandidateRow(
                ASSET_ID,
                ASSET_FILE_ID,
                file.toString(),
                file.toString(),
                file.getFileName().toString(),
                Files.size(file),
                MODIFIED_AT,
                mediaType
        );
    }

    private static final class FakeAssetRepository extends AssetRepository {
        private List<MetadataCandidateRow> candidates = List.of();
        private final Map<UUID, MetadataCandidateRow> activeCandidates = new LinkedHashMap<>();
        private final List<MetadataUpdate> metadataUpdates = new ArrayList<>();
        private final List<UUID> failedAssetIds = new ArrayList<>();
        private final Map<UUID, String> searchTextByAsset = new LinkedHashMap<>();
        private final List<UUID> searchUpserts = new ArrayList<>();

        private FakeAssetRepository() {
            super(null, null);
        }

        @Override
        List<MetadataCandidateRow> listMetadataCandidates(
                int limit,
                String extractor,
                String extractorVersion,
                int schemaVersion
        ) {
            return candidates.stream().limit(limit).toList();
        }

        @Override
        Optional<MetadataCandidateRow> findActiveMetadataCandidate(UUID assetId, UUID assetFileId) {
            return Optional.ofNullable(activeCandidates.get(assetFileId));
        }

        @Override
        void upsertMetadata(MetadataUpdate update) {
            metadataUpdates.add(update);
        }

        @Override
        void markMetadataFailed(UUID assetId, String errorCode, String errorMessage, OffsetDateTime now) {
            failedAssetIds.add(assetId);
        }

        @Override
        String searchableTextForAsset(UUID assetId) {
            return searchTextByAsset.get(assetId);
        }

        @Override
        void upsertSearchDocument(UUID assetId, String searchableText, OffsetDateTime now) {
            searchUpserts.add(assetId);
        }
    }

    private static final class RecordingBackgroundJobService extends BackgroundJobService {
        private final List<BackgroundJobCreate> jobs = new ArrayList<>();
        private List<BackgroundJobRecord> deadLetterJobs = List.of();

        private RecordingBackgroundJobService() {
            super(null, new ImmediateTransactionTemplate());
        }

        @Override
        public UUID enqueue(BackgroundJobCreate create) {
            jobs.add(create);
            return UUID.randomUUID();
        }

        @Override
        public List<BackgroundJobRecord> deadLetterJobs(String jobType, int limit) {
            return deadLetterJobs;
        }

        @Override
        public boolean hasActiveJobs(String jobType, String dedupeKeyPrefix, UUID excludedJobId) {
            return false;
        }
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
