package com.pixierge.api.assets;

import org.springframework.core.io.PathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import static com.pixierge.api.assets.AssetConstants.FILE_STATUS_ACTIVE;
import static com.pixierge.api.assets.AssetConstants.IDENTITY_STATUS_PENDING;
import static com.pixierge.api.assets.AssetConstants.IMAGE_MIME_PREFIX;

@Service
class ThumbnailService {

    private static final String FORMAT = "jpg";
    private static final String CONTENT_TYPE = "image/jpeg";
    private static final String GENERATOR_VERSION = "java-imageio-v1";
    private static final String CONFIG_VERSION = "default-v1";
    private static final int TINY_WIDTH = 48;
    private static final int TINY_HEIGHT = 36;
    private static final int GRID_WIDTH = 320;
    private static final int GRID_HEIGHT = 240;
    private static final int PREVIEW_WIDTH = 1600;
    private static final int PREVIEW_HEIGHT = 1200;
    private static final int GENERATION_LOCK_STRIPES = 256;
    private static final int MAX_CONCURRENT_GENERATIONS = 2;
    /** Soft cap on decoded pixels before the final downscale (~8MP). */
    private static final long MAX_DECODE_PIXELS = 8_000_000L;
    private static final String THUMBNAIL_STATUS_MISSING = "missing";
    private static final String THUMBNAIL_STATUS_READY = "ready";

    private final ThumbnailRepository thumbnailRepository;
    private final StorageProperties storageProperties;
    private final Object[] generationLocks = new Object[GENERATION_LOCK_STRIPES];
    private final Semaphore generationPermits = new Semaphore(MAX_CONCURRENT_GENERATIONS);

    ThumbnailService(ThumbnailRepository thumbnailRepository, StorageProperties storageProperties) {
        this.thumbnailRepository = thumbnailRepository;
        this.storageProperties = storageProperties;
        Arrays.setAll(generationLocks, ignored -> new Object());
    }

    @Transactional
    public ThumbnailResponseResource tinyThumbnail(AssetDetailResponse asset) {
        return resolve(asset, "tiny", TINY_WIDTH, TINY_HEIGHT);
    }

    @Transactional
    public ThumbnailResponseResource gridThumbnail(AssetDetailResponse asset) {
        return resolve(asset, "grid", GRID_WIDTH, GRID_HEIGHT);
    }

    @Transactional
    public ThumbnailResponseResource previewThumbnail(AssetDetailResponse asset) {
        return resolve(asset, "preview", PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    @Transactional
    public ThumbnailAdminActionResponse rebuildMissing(AssetService assetService) {
        int processed = 0;
        int failed = 0;
        for (AssetDetailResponse asset : assetService.listConfirmedAssets()) {
            try {
                resolve(asset, "tiny", TINY_WIDTH, TINY_HEIGHT);
                resolve(asset, "grid", GRID_WIDTH, GRID_HEIGHT);
                resolve(asset, "preview", PREVIEW_WIDTH, PREVIEW_HEIGHT);
                processed++;
            } catch (ResponseStatusException ignored) {
                failed++;
            }
        }
        return new ThumbnailAdminActionResponse(processed, failed);
    }

    @Transactional
    public ThumbnailAdminActionResponse purgeStale() {
        List<ThumbnailRepository.ThumbnailRow> stale = thumbnailRepository.findStaleRows(GENERATOR_VERSION, CONFIG_VERSION);
        int processed = 0;
        int failed = 0;
        for (ThumbnailRepository.ThumbnailRow row : stale) {
            try {
                Path cachedPath = resolveCachePath(Path.of(row.relativePath()));
                Files.deleteIfExists(cachedPath);
                thumbnailRepository.deleteById(row.id());
                processed++;
            } catch (IOException | ResponseStatusException exception) {
                failed++;
            }
        }
        return new ThumbnailAdminActionResponse(processed, failed);
    }

    @Transactional(readOnly = true)
    public Map<String, ThumbnailBrowseSummary> browseSummaries(List<String> contentHashes) {
        List<String> confirmedHashes = contentHashes.stream()
                .filter(hash -> hash != null && !hash.startsWith("provisional:"))
                .distinct()
                .toList();
        if (confirmedHashes.isEmpty()) {
            return Map.of();
        }

        Map<String, ThumbnailRepository.ThumbnailRow> gridRows = thumbnailRepository.findReadyRowsByContentHashes(
                confirmedHashes,
                "grid",
                GRID_WIDTH,
                GRID_HEIGHT,
                FORMAT,
                GENERATOR_VERSION,
                CONFIG_VERSION
        );
        Map<String, ThumbnailRepository.ThumbnailRow> tinyRows = thumbnailRepository.findReadyRowsByContentHashes(
                confirmedHashes,
                "tiny",
                TINY_WIDTH,
                TINY_HEIGHT,
                FORMAT,
                GENERATOR_VERSION,
                CONFIG_VERSION
        );

        Map<String, ThumbnailBrowseSummary> summaries = new LinkedHashMap<>();
        for (String contentHash : confirmedHashes) {
            ThumbnailRepository.ThumbnailRow grid = gridRows.get(contentHash);
            ThumbnailRepository.ThumbnailRow tiny = tinyRows.get(contentHash);
            ThumbnailRepository.ThumbnailCacheInput gridInput = cacheInput(contentHash, "grid", GRID_WIDTH, GRID_HEIGHT);
            summaries.put(contentHash, new ThumbnailBrowseSummary(
                    grid == null ? THUMBNAIL_STATUS_MISSING : THUMBNAIL_STATUS_READY,
                    gridInput.cacheKey(),
                    tiny == null ? null : tiny.placeholder()
            ));
        }
        return summaries;
    }

    private ThumbnailResponseResource resolve(AssetDetailResponse asset, String type, int width, int height) {
        if (IDENTITY_STATUS_PENDING.equals(asset.identityStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thumbnail unavailable while identity is pending");
        }
        AssetFileOccurrenceResponse activeFile = asset.files().stream()
                .filter(file -> FILE_STATUS_ACTIVE.equals(file.status()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active asset file not found"));
        Path originalPath = Path.of(activeFile.path()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(originalPath) || !Files.isReadable(originalPath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset file is unavailable");
        }

        ThumbnailRepository.ThumbnailCacheInput cacheInput = cacheInput(asset.contentHash(), type, width, height);
        Object generationLock = generationLocks[Math.floorMod(cacheInput.cacheKey().hashCode(), generationLocks.length)];
        synchronized (generationLock) {
            return resolveLocked(asset, originalPath, cacheInput, "tiny".equals(type));
        }
    }

    private ThumbnailResponseResource resolveLocked(
            AssetDetailResponse asset,
            Path originalPath,
            ThumbnailRepository.ThumbnailCacheInput cacheInput,
            boolean includePlaceholder
    ) {
        Optional<ThumbnailRepository.ThumbnailRow> existing = thumbnailRepository.findByCacheInput(cacheInput);
        if (existing.isPresent()) {
            Path cachedPath = resolveCachePath(Path.of(existing.get().relativePath()));
            if (Files.isRegularFile(cachedPath) && Files.isReadable(cachedPath)) {
                return toResponse(cachedPath, cacheInput.cacheKey());
            }
            thumbnailRepository.markMissing(existing.get().id());
        }

        Path relative = relativePath(cacheInput);
        Path outputPath = resolveCachePath(relative);
        GeneratedThumbnail generated = generateThumbnail(
                originalPath,
                outputPath,
                cacheInput.width(),
                cacheInput.height(),
                includePlaceholder
        );
        try {
            long byteSize = Files.size(outputPath);
            thumbnailRepository.upsertReadyRow(asset.id(), cacheInput, relative.toString(), byteSize, generated.placeholder());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Thumbnail generation failed");
        }
        return toResponse(outputPath, cacheInput.cacheKey());
    }

    private ThumbnailResponseResource toResponse(Path path, String cacheKey) {
        try {
            return new ThumbnailResponseResource(
                    new PathResource(path),
                    Files.size(path),
                    CONTENT_TYPE,
                    "\"" + cacheKey + "\"",
                    OffsetDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneOffset.UTC)
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thumbnail file is unavailable");
        }
    }

    ThumbnailRepository.ThumbnailCacheInput cacheInput(String contentHash, String type, int width, int height) {
        String keyMaterial = String.join("|", contentHash, type, String.valueOf(width), String.valueOf(height), FORMAT, GENERATOR_VERSION, CONFIG_VERSION);
        return new ThumbnailRepository.ThumbnailCacheInput(
                contentHash,
                type,
                width,
                height,
                FORMAT,
                GENERATOR_VERSION,
                CONFIG_VERSION,
                digest(keyMaterial)
        );
    }

    Path relativePath(ThumbnailRepository.ThumbnailCacheInput input) {
        return Path.of(
                "cache",
                "thumbnails",
                "v1",
                input.cacheKey().substring(0, 2),
                input.cacheKey().substring(2, 4),
                input.cacheKey() + "." + input.format()
        );
    }

    private GeneratedThumbnail generateThumbnail(Path source, Path destination, int maxWidth, int maxHeight, boolean includePlaceholder) {
        try {
            generationPermits.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Thumbnail generation interrupted");
        }
        try {
            BufferedImage original = readScaledImage(source, maxWidth, maxHeight);
            double scale = Math.min((double) maxWidth / original.getWidth(), (double) maxHeight / original.getHeight());
            scale = Math.min(scale, 1.0d);
            int targetWidth = Math.max(1, (int) Math.round(original.getWidth() * scale));
            int targetHeight = Math.max(1, (int) Math.round(original.getHeight() * scale));
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }

            Path temporary = null;
            try {
                Files.createDirectories(destination.getParent());
                Path relative = storageRoot().relativize(destination);
                resolveCachePath(relative);
                temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
                if (!ImageIO.write(resized, FORMAT, temporary.toFile())) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Thumbnail generation failed");
                }
                moveAtomically(temporary, destination);
                temporary = null;
                return new GeneratedThumbnail(includePlaceholder ? placeholderFor(resized) : null);
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Thumbnail generation failed");
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                    }
                }
            }
        } finally {
            generationPermits.release();
        }
    }

    /**
     * Decodes only as much of the source as needed for the target size, using ImageReader
     * source subsampling so large camera JPEGs do not fully expand into heap.
     */
    private BufferedImage readScaledImage(Path source, int maxWidth, int maxHeight) {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset file is unavailable");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Asset file cannot be previewed");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                if (sourceWidth <= 0 || sourceHeight <= 0) {
                    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Asset file cannot be previewed");
                }
                int subsample = sourceSubsample(sourceWidth, sourceHeight, maxWidth, maxHeight);
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsample > 1) {
                    param.setSourceSubsampling(subsample, subsample, 0, 0);
                }
                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Asset file cannot be previewed");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset file is unavailable");
        }
    }

    static int sourceSubsample(int sourceWidth, int sourceHeight, int maxWidth, int maxHeight) {
        int decodeWidth = Math.max(1, maxWidth * 2);
        int decodeHeight = Math.max(1, maxHeight * 2);
        int subsample = Math.max(
                1,
                Math.max(
                        (int) Math.ceil((double) sourceWidth / decodeWidth),
                        (int) Math.ceil((double) sourceHeight / decodeHeight)
                )
        );
        while ((long) (sourceWidth / subsample) * (sourceHeight / subsample) > MAX_DECODE_PIXELS) {
            subsample++;
        }
        return subsample;
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String placeholderFor(BufferedImage image) {
        long red = 0;
        long green = 0;
        long blue = 0;
        int sampleCount = image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                red += (rgb >> 16) & 0xff;
                green += (rgb >> 8) & 0xff;
                blue += rgb & 0xff;
            }
        }
        int averageRed = Math.toIntExact(red / sampleCount);
        int averageGreen = Math.toIntExact(green / sampleCount);
        int averageBlue = Math.toIntExact(blue / sampleCount);
        return "linear-gradient(135deg, "
                + rgbCss(lighten(averageRed), lighten(averageGreen), lighten(averageBlue))
                + ", "
                + rgbCss(averageRed, averageGreen, averageBlue)
                + " 52%, "
                + rgbCss(darken(averageRed), darken(averageGreen), darken(averageBlue))
                + ")";
    }

    private int lighten(int value) {
        return Math.min(255, value + 28);
    }

    private int darken(int value) {
        return Math.max(0, value - 34);
    }

    private String rgbCss(int red, int green, int blue) {
        return "rgb(" + red + ", " + green + ", " + blue + ")";
    }

    Path resolveCachePath(Path relativePath) {
        if (relativePath.isAbsolute()) {
            throw invalidStoragePath();
        }
        Path root = storageRoot();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw invalidStoragePath();
        }

        try {
            Files.createDirectories(root);
            Path realRoot = root.toRealPath();
            Path existing = target;
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            if (existing == null || !existing.toRealPath().startsWith(realRoot)) {
                throw invalidStoragePath();
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Thumbnail storage is unavailable");
        }
        return target;
    }

    private ResponseStatusException invalidStoragePath() {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid thumbnail storage path");
    }

    private Path storageRoot() {
        return Path.of(storageProperties.getRoot()).toAbsolutePath().normalize();
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    record ThumbnailBrowseSummary(String status, String cacheKey, String placeholder) {
        static ThumbnailBrowseSummary missing() {
            return new ThumbnailBrowseSummary(THUMBNAIL_STATUS_MISSING, null, null);
        }
    }

    private record GeneratedThumbnail(String placeholder) {
    }
}
