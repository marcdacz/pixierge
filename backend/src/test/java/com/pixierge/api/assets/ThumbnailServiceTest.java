package com.pixierge.api.assets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailServiceTest {

    @TempDir
    private Path tempDir;

    private FakeThumbnailRepository repository;
    private ThumbnailService service;

    @BeforeAll
    static void useHeadlessImageProcessing() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void setUp() {
        repository = new FakeThumbnailRepository();
        StorageProperties properties = new StorageProperties();
        properties.setRoot(tempDir.resolve("storage").toString());
        service = new ThumbnailService(repository, properties);
    }

    @Test
    void cacheKeysAndShardedPathsAreStable() {
        ThumbnailRepository.ThumbnailCacheInput first = service.cacheInput("content-hash", "grid", 320, 240);
        ThumbnailRepository.ThumbnailCacheInput second = service.cacheInput("content-hash", "grid", 320, 240);
        ThumbnailRepository.ThumbnailCacheInput preview = service.cacheInput("content-hash", "preview", 1600, 1200);

        assertThat(first.cacheKey()).isEqualTo(second.cacheKey()).hasSize(64);
        assertThat(preview.cacheKey()).isNotEqualTo(first.cacheKey());
        assertThat(service.relativePath(first).toString()).isEqualTo(
                Path.of(
                        "cache",
                        "thumbnails",
                        "v1",
                        first.cacheKey().substring(0, 2),
                        first.cacheKey().substring(2, 4),
                        first.cacheKey() + ".jpg"
                ).toString()
        );
    }

    @Test
    void browseSummaryIncludesExpectedKeyBeforeGeneration() {
        ThumbnailService.ThumbnailBrowseSummary summary = service.browseSummaries(List.of("content-hash")).get("content-hash");

        assertThat(summary.status()).isEqualTo("missing");
        assertThat(summary.cacheKey()).isEqualTo(service.cacheInput("content-hash", "grid", 320, 240).cacheKey());
    }

    @Test
    void storagePathsCannotEscapeTheConfiguredRoot() throws Exception {
        Path valid = service.resolveCachePath(Path.of("cache", "thumbnails", "image.jpg"));
        assertThat(valid.normalize().startsWith(tempDir.resolve("storage").normalize())).isTrue();

        assertThatThrownBy(() -> service.resolveCachePath(Path.of("..", "outside.jpg")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.createSymbolicLink(tempDir.resolve("storage").resolve("linked-cache"), outside);
        assertThatThrownBy(() -> service.resolveCachePath(Path.of("linked-cache", "outside.jpg")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void provisionalAssetDoesNotCreateAStableThumbnail() {
        AssetDetailResponse pending = asset("provisional:pending", "pending", tempDir.resolve("pending.jpg"));

        assertThatThrownBy(() -> service.gridThumbnail(pending))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(repository.operationCount()).isZero();
    }

    @Test
    void missingCachedFileIsRegenerated() throws Exception {
        Path source = createImage("source.jpg");
        AssetDetailResponse asset = asset("confirmed-hash", "confirmed", source);

        service.gridThumbnail(asset);

        ThumbnailRepository.ThumbnailCacheInput input = service.cacheInput("confirmed-hash", "grid", 320, 240);
        Path relative = service.relativePath(input);
        Path generated = service.resolveCachePath(relative);
        assertThat(generated).isRegularFile();
        assertThat(repository.upsertCount()).isEqualTo(1);

        Files.delete(generated);
        service.gridThumbnail(asset);

        assertThat(generated).isRegularFile();
        assertThat(repository.missingRowIds()).hasSize(1);
        assertThat(repository.upsertCount()).isEqualTo(2);
    }

    @Test
    void concurrentRequestsGenerateAndPersistOnce() throws Exception {
        Path source = createImage("concurrent.jpg");
        AssetDetailResponse asset = asset("concurrent-hash", "confirmed", source);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ThumbnailResponseResource> first = executor.submit(() -> {
                start.await();
                return service.gridThumbnail(asset);
            });
            Future<ThumbnailResponseResource> second = executor.submit(() -> {
                start.await();
                return service.gridThumbnail(asset);
            });
            start.countDown();

            assertThat(first.get().etag()).isEqualTo(second.get().etag());
        }

        assertThat(repository.upsertCount()).isEqualTo(1);
    }

    @Test
    void sourceSubsampleAvoidsFullResolutionDecode() {
        assertThat(ThumbnailService.sourceSubsample(6000, 4000, 48, 36)).isGreaterThan(1);
        assertThat(ThumbnailService.sourceSubsample(6000, 4000, 320, 240)).isGreaterThan(1);
        assertThat(ThumbnailService.sourceSubsample(6000, 4000, 1600, 1200)).isGreaterThan(1);
        assertThat(ThumbnailService.sourceSubsample(640, 480, 320, 240)).isEqualTo(1);
    }

    @Test
    void largeSourceImageProducesBoundedGridThumbnail() throws Exception {
        Path source = createImage("large-source.jpg", 4000, 3000);
        AssetDetailResponse asset = asset("large-hash", "confirmed", source, 4000, 3000);

        ThumbnailResponseResource response = service.gridThumbnail(asset);

        assertThat(response.contentType()).isEqualTo("image/jpeg");
        BufferedImage thumbnail = ImageIO.read(response.resource().getInputStream());
        assertThat(thumbnail.getWidth()).isLessThanOrEqualTo(320);
        assertThat(thumbnail.getHeight()).isLessThanOrEqualTo(240);
        assertThat(repository.upsertCount()).isEqualTo(1);
    }

    @Test
    void purgeRetainsRowsWhenTheStoredPathIsUnsafe() {
        ThumbnailRepository.ThumbnailCacheInput input = service.cacheInput("stale-hash", "grid", 320, 240);
        ThumbnailRepository.ThumbnailRow stale = row(UUID.randomUUID(), input, Path.of("..", "outside.jpg"));
        repository.setStaleRows(List.of(stale));

        ThumbnailAdminActionResponse response = service.purgeStale();

        assertThat(response.processedCount()).isZero();
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(repository.deletedRowIds()).doesNotContain(stale.id());
    }

    private Path createImage(String fileName) throws Exception {
        return createImage(fileName, 640, 480);
    }

    private Path createImage(String fileName, int width, int height) throws Exception {
        Path path = tempDir.resolve(fileName);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        assertThat(ImageIO.write(image, "jpg", path.toFile())).isTrue();
        return path;
    }

    private AssetDetailResponse asset(String contentHash, String identityStatus, Path source) {
        return asset(contentHash, identityStatus, source, 640, 480);
    }

    private AssetDetailResponse asset(String contentHash, String identityStatus, Path source, int width, int height) {
        UUID assetId = UUID.randomUUID();
        return new AssetDetailResponse(
                assetId,
                contentHash,
                identityStatus,
                "image/jpeg",
                "available",
                1,
                new AssetMetadataResponse(null, width, height, "jpg", "image/jpeg", "extracted", null, null),
                List.of(new AssetFileOccurrenceResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Test Library",
                        source.toString(),
                        source.getParent().toString(),
                        source.getFileName().toString(),
                        1,
                        OffsetDateTime.now(),
                        "active"
                )),
                List.of()
        );
    }

    private ThumbnailRepository.ThumbnailRow row(
            UUID assetId,
            ThumbnailRepository.ThumbnailCacheInput input,
            Path relativePath
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ThumbnailRepository.ThumbnailRow(
                UUID.randomUUID(),
                assetId,
                input.contentHash(),
                input.thumbnailType(),
                input.width(),
                input.height(),
                input.format(),
                input.generatorVersion(),
                input.configVersion(),
                input.cacheKey(),
                relativePath.toString(),
                1,
                null,
                "ready",
                null,
                now,
                now
        );
    }

    private final class FakeThumbnailRepository extends ThumbnailRepository {
        private final Map<ThumbnailCacheInput, ThumbnailRow> rows = new ConcurrentHashMap<>();
        private final Set<UUID> missingRowIds = ConcurrentHashMap.newKeySet();
        private final Set<UUID> deletedRowIds = ConcurrentHashMap.newKeySet();
        private final AtomicInteger operations = new AtomicInteger();
        private final AtomicInteger upserts = new AtomicInteger();
        private volatile List<ThumbnailRow> staleRows = List.of();

        private FakeThumbnailRepository() {
            super(null);
        }

        @Override
        Optional<ThumbnailRow> findByCacheInput(ThumbnailCacheInput input) {
            operations.incrementAndGet();
            return Optional.ofNullable(rows.get(input));
        }

        @Override
        Map<String, ThumbnailRow> findReadyRowsByContentHashes(
                Collection<String> contentHashes,
                String thumbnailType,
                int width,
                int height,
                String format,
                String generatorVersion,
                String configVersion
        ) {
            operations.incrementAndGet();
            Map<String, ThumbnailRow> matching = new LinkedHashMap<>();
            rows.values().stream()
                    .filter(row -> contentHashes.contains(row.contentHash()))
                    .filter(row -> row.thumbnailType().equals(thumbnailType))
                    .filter(row -> row.width() == width && row.height() == height)
                    .filter(row -> row.format().equals(format))
                    .filter(row -> row.generatorVersion().equals(generatorVersion))
                    .filter(row -> row.configVersion().equals(configVersion))
                    .filter(row -> row.status().equals("ready"))
                    .forEach(row -> matching.put(row.contentHash(), row));
            return matching;
        }

        @Override
        List<ThumbnailRow> findStaleRows(String generatorVersion, String configVersion) {
            operations.incrementAndGet();
            return staleRows;
        }

        @Override
        void upsertReadyRow(UUID assetId, ThumbnailCacheInput input, String relativePath, long byteSize, String placeholder) {
            operations.incrementAndGet();
            upserts.incrementAndGet();
            rows.put(input, new ThumbnailRow(
                    UUID.randomUUID(),
                    assetId,
                    input.contentHash(),
                    input.thumbnailType(),
                    input.width(),
                    input.height(),
                    input.format(),
                    input.generatorVersion(),
                    input.configVersion(),
                    input.cacheKey(),
                    relativePath,
                    byteSize,
                    placeholder,
                    "ready",
                    null,
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            ));
        }

        @Override
        void markMissing(UUID rowId) {
            operations.incrementAndGet();
            missingRowIds.add(rowId);
        }

        @Override
        void deleteById(UUID rowId) {
            operations.incrementAndGet();
            deletedRowIds.add(rowId);
        }

        int operationCount() {
            return operations.get();
        }

        int upsertCount() {
            return upserts.get();
        }

        Set<UUID> missingRowIds() {
            return missingRowIds;
        }

        Set<UUID> deletedRowIds() {
            return deletedRowIds;
        }

        void setStaleRows(List<ThumbnailRow> staleRows) {
            this.staleRows = staleRows;
        }
    }
}
