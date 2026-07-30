package com.pixierge.api.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobCreate;
import com.pixierge.api.background.BackgroundJobRecord;
import com.pixierge.api.background.BackgroundJobService;
import com.pixierge.api.background.FileActivityService;
import com.pixierge.api.scans.ScanJobTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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

    @BeforeAll
    static void useHeadlessImageProcessing() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        assetRepository = new FakeAssetRepository();
        backgroundJobService = new RecordingBackgroundJobService();
        service = new MetadataEnrichmentService(
                assetRepository,
                backgroundJobService,
                FileActivityService.noop(),
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
        assertThat(job.concurrencyKey()).isEqualTo(ScanJobTypes.ASSET_METADATA_BACKFILL + ":" + ASSET_ID);
        AssetMetadataJobPayload payload = objectMapper.readValue(job.payloadJson(), AssetMetadataJobPayload.class);
        assertThat(payload.assetId()).isEqualTo(ASSET_ID);
        assertThat(payload.assetFileId()).isEqualTo(ASSET_FILE_ID);
        assertThat(payload.sizeBytes()).isEqualTo(Files.size(file));
    }

    @Test
    void enqueueMetadataBackfillCountsRejectedJobs() throws Exception {
        Path first = Files.writeString(tempDir.resolve("first.jpg"), "image");
        Path second = Files.writeString(tempDir.resolve("second.jpg"), "image");
        assetRepository.candidates = List.of(
                candidate(UUID.randomUUID(), UUID.randomUUID(), first, "image"),
                candidate(UUID.randomUUID(), UUID.randomUUID(), second, "image")
        );
        backgroundJobService.rejectEveryOtherJob = true;

        AdminBatchActionResponse response = service.enqueueMetadataBackfill(5);

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(backgroundJobService.jobs).hasSize(1);
    }

    @Test
    void queuedMetadataIgnoresMissingOrStaleCandidates() throws Exception {
        Path file = Files.writeString(tempDir.resolve("stale.txt"), "not media");
        AssetRepository.MetadataCandidateRow candidate = candidate(file, "document");
        assetRepository.activeCandidates.put(ASSET_FILE_ID, candidate);

        service.extractQueuedMetadata(new AssetMetadataJobPayload(
                ASSET_ID,
                UUID.randomUUID(),
                candidate.normalizedPath(),
                candidate.fileName(),
                candidate.sizeBytes(),
                candidate.modifiedAt(),
                candidate.mediaType()
        ));
        service.extractQueuedMetadata(new AssetMetadataJobPayload(
                ASSET_ID,
                ASSET_FILE_ID,
                candidate.normalizedPath(),
                candidate.fileName(),
                candidate.sizeBytes() + 1L,
                candidate.modifiedAt(),
                candidate.mediaType()
        ));

        assertThat(assetRepository.metadataUpdates).isEmpty();
        assertThat(assetRepository.searchUpserts).isEmpty();
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
        assertThat(update.metadataExtractionDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(assetRepository.searchUpserts).containsExactly(ASSET_ID);
    }

    @Test
    void queuedPhotoWithoutExifDimensionsUsesImageIoFallback() throws Exception {
        Path file = tempDir.resolve("without-exif.jpg");
        BufferedImage image = new BufferedImage(7, 5, BufferedImage.TYPE_INT_RGB);
        assertThat(ImageIO.write(image, "jpg", file.toFile())).isTrue();
        AssetRepository.MetadataCandidateRow candidate = candidate(file, "image");
        assetRepository.activeCandidates.put(ASSET_FILE_ID, candidate);

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
                .containsExactly("processing", "extracted");
        AssetRepository.MetadataUpdate update = assetRepository.metadataUpdates.get(1);
        assertThat(update.width()).isEqualTo(7);
        assertThat(update.height()).isEqualTo(5);
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

    @Test
    void recoveryCountsMalformedStaleActiveAndRejectedDeadLetterJobs() throws Exception {
        Path staleFile = Files.writeString(tempDir.resolve("stale-recovery.jpg"), "stale");
        Path activeFile = Files.writeString(tempDir.resolve("active-recovery.jpg"), "active");
        Path rejectedFile = Files.writeString(tempDir.resolve("rejected-recovery.jpg"), "rejected");
        AssetRepository.MetadataCandidateRow stale = candidate(UUID.randomUUID(), UUID.randomUUID(), staleFile, "image");
        AssetRepository.MetadataCandidateRow active = candidate(UUID.randomUUID(), UUID.randomUUID(), activeFile, "image");
        AssetRepository.MetadataCandidateRow rejected = candidate(UUID.randomUUID(), UUID.randomUUID(), rejectedFile, "image");
        assetRepository.activeCandidates.put(stale.assetFileId(), stale);
        assetRepository.activeCandidates.put(active.assetFileId(), active);
        assetRepository.activeCandidates.put(rejected.assetFileId(), rejected);
        backgroundJobService.activeDedupePrefixes.add(ScanJobTypes.ASSET_METADATA_BACKFILL + ":" + active.assetId() + ":");
        backgroundJobService.rejectAllJobs = true;
        backgroundJobService.deadLetterJobs = List.of(
                deadLetterJob("{not-json"),
                deadLetterJob(new AssetMetadataJobPayload(
                        stale.assetId(),
                        stale.assetFileId(),
                        stale.normalizedPath(),
                        stale.fileName(),
                        stale.sizeBytes() + 10L,
                        stale.modifiedAt(),
                        stale.mediaType()
                )),
                deadLetterJob(new AssetMetadataJobPayload(
                        active.assetId(),
                        active.assetFileId(),
                        active.normalizedPath(),
                        active.fileName(),
                        active.sizeBytes(),
                        active.modifiedAt(),
                        active.mediaType()
                )),
                deadLetterJob(new AssetMetadataJobPayload(
                        rejected.assetId(),
                        rejected.assetFileId(),
                        rejected.normalizedPath(),
                        rejected.fileName(),
                        rejected.sizeBytes(),
                        rejected.modifiedAt(),
                        rejected.mediaType()
                ))
        );

        AdminBatchActionResponse response = service.recoverDeadLetterMetadata();

        assertThat(response.processedCount()).isZero();
        assertThat(response.failedCount()).isEqualTo(3);
        assertThat(assetRepository.failedAssetIds).containsExactly(active.assetId(), rejected.assetId());
        assertThat(backgroundJobService.jobs).isEmpty();
    }

    private AssetRepository.MetadataCandidateRow candidate(Path file, String mediaType) throws Exception {
        return candidate(ASSET_ID, ASSET_FILE_ID, file, mediaType);
    }

    private AssetRepository.MetadataCandidateRow candidate(UUID assetId, UUID assetFileId, Path file, String mediaType) throws Exception {
        return new AssetRepository.MetadataCandidateRow(
                assetId,
                assetFileId,
                file.toString(),
                file.toString(),
                file.getFileName().toString(),
                Files.size(file),
                MODIFIED_AT,
                mediaType
        );
    }

    private BackgroundJobRecord deadLetterJob(AssetMetadataJobPayload payload) throws Exception {
        return deadLetterJob(objectMapper.writeValueAsString(payload));
    }

    private BackgroundJobRecord deadLetterJob(String payloadJson) {
        return new BackgroundJobRecord(
                UUID.randomUUID(),
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                payloadJson,
                "dead_letter",
                0,
                3,
                3,
                MODIFIED_AT,
                null,
                null,
                ScanJobTypes.ASSET_METADATA_BACKFILL,
                "metadata:" + UUID.randomUUID(),
                null,
                "error",
                "failed",
                MODIFIED_AT,
                MODIFIED_AT,
                MODIFIED_AT
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
        private final List<String> activeDedupePrefixes = new ArrayList<>();
        private List<BackgroundJobRecord> deadLetterJobs = List.of();
        private boolean rejectEveryOtherJob;
        private boolean rejectAllJobs;

        private RecordingBackgroundJobService() {
            super(null, new ImmediateTransactionTemplate());
        }

        @Override
        public UUID enqueue(BackgroundJobCreate create) {
            if (rejectAllJobs || (rejectEveryOtherJob && jobs.size() % 2 == 1)) {
                throw new IllegalStateException("queue rejected");
            }
            jobs.add(create);
            return UUID.randomUUID();
        }

        @Override
        public List<BackgroundJobRecord> deadLetterJobs(String jobType, int limit) {
            return deadLetterJobs;
        }

        @Override
        public boolean hasActiveJobs(String jobType, String dedupeKeyPrefix, UUID excludedJobId) {
            return activeDedupePrefixes.contains(dedupeKeyPrefix);
        }
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
