package com.pixierge.api.assets;

import static com.pixierge.api.assets.AssetConstants.EXTRACTION_STATUS_EXTRACTED;
import static com.pixierge.api.assets.AssetConstants.EXTRACTION_STATUS_FAILED;
import static com.pixierge.api.assets.AssetConstants.EXTRACTION_STATUS_PROCESSING;
import static com.pixierge.api.assets.AssetConstants.EXTRACTION_STATUS_UNSUPPORTED;

import com.adobe.internal.xmp.XMPConst;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobCreate;
import com.pixierge.api.background.BackgroundJobRecord;
import com.pixierge.api.background.BackgroundJobService;
import com.pixierge.api.background.FileActivityService;
import com.pixierge.api.scans.ScanJobTypes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MetadataEnrichmentService {

  private static final String EXTRACTOR = "pixierge-metadata";
  private static final String EXTRACTOR_VERSION = "metadata-extractor-2.18.0+ffprobe";
  private static final String SOURCE_VERSION = "metadata-enrichment-v1";
  private static final int SCHEMA_VERSION = 1;
  private static final int DEFAULT_BACKFILL_BATCH_SIZE = 500;
  private static final int STDOUT_LIMIT_BYTES = 2 * 1024 * 1024;
  private static final int STDERR_LIMIT_BYTES = 16 * 1024;
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  private final AssetRepository assetRepository;
  private final BackgroundJobService backgroundJobService;
  private final FileActivityService fileActivityService;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;
  private final String ffprobePath;
  private final Duration ffprobeTimeout;

  public MetadataEnrichmentService(
      AssetRepository assetRepository,
      BackgroundJobService backgroundJobService,
      FileActivityService fileActivityService,
      TransactionTemplate transactionTemplate,
      ObjectMapper objectMapper,
      @Value("${pixierge.metadata.ffprobe.path:ffprobe}") String ffprobePath,
      @Value("${pixierge.metadata.ffprobe.timeout-seconds:10}") int ffprobeTimeoutSeconds) {
    this.assetRepository = assetRepository;
    this.backgroundJobService = backgroundJobService;
    this.fileActivityService = fileActivityService;
    this.transactionTemplate = transactionTemplate;
    this.objectMapper = objectMapper;
    this.ffprobePath = ffprobePath == null || ffprobePath.isBlank() ? "ffprobe" : ffprobePath;
    this.ffprobeTimeout = Duration.ofSeconds(Math.max(1, ffprobeTimeoutSeconds));
  }

  public AdminBatchActionResponse enqueueMetadataBackfill() {
    return enqueueMetadataBackfill(DEFAULT_BACKFILL_BATCH_SIZE);
  }

  public AdminBatchActionResponse enqueueMetadataBackfill(int limit) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<AssetRepository.MetadataCandidateRow> candidates =
        transactionTemplate.execute(
            status ->
                assetRepository.listMetadataCandidates(
                    Math.max(1, limit), EXTRACTOR, EXTRACTOR_VERSION, SCHEMA_VERSION));
    int enqueued = 0;
    int failed = 0;
    for (AssetRepository.MetadataCandidateRow candidate : candidates) {
      try {
        backgroundJobService.enqueue(metadataJob(candidate, now));
        enqueued++;
      } catch (RuntimeException exception) {
        failed++;
      }
    }
    return new AdminBatchActionResponse(enqueued, failed);
  }

  public void extractQueuedMetadata(AssetMetadataJobPayload payload, UUID jobId) {
    AssetRepository.MetadataCandidateRow candidate =
        transactionTemplate.execute(status -> claim(payload));
    if (candidate == null) {
      return;
    }

    MetadataResult result;
    long extractionStartedAt = System.nanoTime();
    try {
      result = extract(candidate);
    } catch (RuntimeException exception) {
      transactionTemplate.executeWithoutResult(
          status ->
              assetRepository.markMetadataFailed(
                  candidate.assetId(),
                  exception.getClass().getSimpleName(),
                  exception.getMessage(),
                  OffsetDateTime.now(ZoneOffset.UTC)));
      fileActivityService.record(
          candidate.assetId(),
          candidate.normalizedPath(),
          EXTRACTION_STATUS_FAILED,
          OffsetDateTime.now(ZoneOffset.UTC),
          exception.getMessage(),
          null,
          jobId,
          "Metadata extraction");
      throw exception;
    }
    transactionTemplate.executeWithoutResult(
        status -> {
          OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
          assetRepository.upsertMetadata(
              toUpdate(
                  candidate,
                  result,
                  now,
                  java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                      System.nanoTime() - extractionStartedAt)));
          assetRepository.upsertSearchDocument(
              candidate.assetId(),
              assetRepository.searchableTextForAsset(candidate.assetId()),
              now);
          fileActivityService.record(
              candidate.assetId(),
              candidate.normalizedPath(),
              result.status(),
              now,
              result.errorMessage(),
              java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                  System.nanoTime() - extractionStartedAt),
              jobId,
              "Metadata extraction");
        });
  }

  public void extractQueuedMetadata(AssetMetadataJobPayload payload) {
    extractQueuedMetadata(payload, null);
  }

  public AdminBatchActionResponse recoverDeadLetterMetadata() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    int recovered = 0;
    int failed = 0;
    for (BackgroundJobRecord job :
        backgroundJobService.deadLetterJobs(
            ScanJobTypes.ASSET_METADATA_BACKFILL, DEFAULT_BACKFILL_BATCH_SIZE)) {
      AssetMetadataJobPayload payload = metadataPayload(job);
      AssetRepository.MetadataCandidateRow candidate =
          transactionTemplate.execute(status -> recoveryCandidate(payload, now));
      if (candidate == null) {
        failed++;
        continue;
      }
      String dedupeKeyPrefix =
          ScanJobTypes.ASSET_METADATA_BACKFILL + ":" + candidate.assetId() + ":";
      if (backgroundJobService.hasActiveJobs(
          ScanJobTypes.ASSET_METADATA_BACKFILL, dedupeKeyPrefix, null)) {
        continue;
      }
      try {
        backgroundJobService.enqueue(metadataJob(candidate, now));
        recovered++;
      } catch (RuntimeException exception) {
        failed++;
      }
    }
    return new AdminBatchActionResponse(recovered, failed);
  }

  private AssetRepository.MetadataCandidateRow claim(AssetMetadataJobPayload payload) {
    AssetRepository.MetadataCandidateRow candidate =
        assetRepository
            .findActiveMetadataCandidate(payload.assetId(), payload.assetFileId())
            .orElse(null);
    if (!matchesPayload(candidate, payload)) {
      return null;
    }
    assetRepository.upsertMetadata(
        toProcessingUpdate(candidate, OffsetDateTime.now(ZoneOffset.UTC)));
    return candidate;
  }

  private AssetRepository.MetadataCandidateRow recoveryCandidate(
      AssetMetadataJobPayload payload, OffsetDateTime now) {
    AssetRepository.MetadataCandidateRow candidate =
        assetRepository
            .findActiveMetadataCandidate(payload.assetId(), payload.assetFileId())
            .orElse(null);
    if (!matchesPayload(candidate, payload)) {
      return null;
    }
    assetRepository.markMetadataFailed(
        candidate.assetId(),
        "metadata_recovery",
        "Requeued after a dead-letter metadata extraction",
        now);
    return candidate;
  }

  private boolean matchesPayload(
      AssetRepository.MetadataCandidateRow candidate, AssetMetadataJobPayload payload) {
    return candidate != null
        && candidate.normalizedPath().equals(payload.normalizedPath())
        && candidate.fileName().equals(payload.fileName())
        && candidate.sizeBytes() == payload.sizeBytes()
        && sameInstant(candidate.modifiedAt(), payload.modifiedAt());
  }

  private AssetMetadataJobPayload metadataPayload(BackgroundJobRecord job) {
    try {
      return objectMapper.readValue(job.payloadJson(), AssetMetadataJobPayload.class);
    } catch (JsonProcessingException exception) {
      return new AssetMetadataJobPayload(null, null, null, null, 0L, null, null);
    }
  }

  private BackgroundJobCreate metadataJob(
      AssetRepository.MetadataCandidateRow candidate, OffsetDateTime now) {
    try {
      String payload =
          objectMapper.writeValueAsString(
              new AssetMetadataJobPayload(
                  candidate.assetId(),
                  candidate.assetFileId(),
                  candidate.normalizedPath(),
                  candidate.fileName(),
                  candidate.sizeBytes(),
                  candidate.modifiedAt(),
                  candidate.mediaType()));
      return new BackgroundJobCreate(
          ScanJobTypes.ASSET_METADATA_BACKFILL,
          payload,
          -10,
          3,
          now,
          ScanJobTypes.ASSET_METADATA_BACKFILL + ":" + candidate.assetId(),
          ScanJobTypes.ASSET_METADATA_BACKFILL
              + ":"
              + candidate.assetId()
              + ":"
              + candidate.sizeBytes()
              + ":"
              + candidate.modifiedAt().toInstant().toEpochMilli());
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Could not create metadata job", exception);
    }
  }

  private MetadataResult extract(AssetRepository.MetadataCandidateRow candidate) {
    Path path = Path.of(candidate.normalizedPath()).toAbsolutePath().normalize();
    String mimeType = probeContentType(path);
    if (candidate.mediaType() != null
        && candidate.mediaType().toLowerCase(Locale.ROOT).startsWith("video")) {
      return extractVideo(candidate, path, mimeType);
    }
    if (isVideoPath(path)) {
      return extractVideo(candidate, path, mimeType);
    }
    if (isPhotoPath(path) || (mimeType != null && mimeType.startsWith("image/"))) {
      return extractPhoto(candidate, path, mimeType);
    }
    return MetadataResult.unsupported(
        candidate.modifiedAt(),
        extension(candidate.fileName()),
        mimeType,
        "unsupported_media",
        null);
  }

  private MetadataResult extractPhoto(
      AssetRepository.MetadataCandidateRow candidate, Path path, String mimeType) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());
      ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
      IptcDirectory iptc = metadata.getFirstDirectoryOfType(IptcDirectory.class);
      PhotoText xmp = xmpText(metadata);
      ImageDimensions dimensions = dimensions(ifd0, subIfd, null);
      if (dimensions.width() == null || dimensions.height() == null) {
        dimensions = dimensions(ifd0, subIfd, imageIoDimensions(path));
      }
      GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
      GeoLocation location = gps == null ? null : gps.getGeoLocation();
      List<String> keywords = keywords(iptc, xmp);

      return new MetadataResult(
          EXTRACTION_STATUS_EXTRACTED,
          null,
          null,
          firstDate(subIfd, candidate.modifiedAt()),
          dimensions.width(),
          dimensions.height(),
          integer(ifd0, ExifDirectoryBase.TAG_ORIENTATION),
          extension(candidate.fileName()),
          mimeType,
          string(ifd0, ExifDirectoryBase.TAG_MAKE),
          string(ifd0, ExifDirectoryBase.TAG_MODEL),
          string(subIfd, ExifDirectoryBase.TAG_LENS_MODEL),
          rationalDouble(subIfd, ExifDirectoryBase.TAG_FOCAL_LENGTH),
          rationalDouble(subIfd, ExifDirectoryBase.TAG_FNUMBER),
          rationalString(subIfd, ExifDirectoryBase.TAG_EXPOSURE_TIME),
          firstInteger(
              subIfd, ExifDirectoryBase.TAG_ISO_EQUIVALENT, ExifDirectoryBase.TAG_ISO_SPEED),
          location == null || location.isZero() ? null : location.getLatitude(),
          location == null || location.isZero() ? null : location.getLongitude(),
          firstNonBlank(
              xmp.title(),
              string(iptc, IptcDirectory.TAG_OBJECT_NAME),
              string(ifd0, ExifDirectoryBase.TAG_IMAGE_DESCRIPTION)),
          firstNonBlank(xmp.description(), string(iptc, IptcDirectory.TAG_CAPTION)),
          String.join("\n", keywords),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    } catch (ImageProcessingException exception) {
      ImageDimensions fallback = imageIoDimensions(path);
      if (fallback.width() != null && fallback.height() != null) {
        return new MetadataResult(
            EXTRACTION_STATUS_EXTRACTED,
            null,
            null,
            candidate.modifiedAt(),
            fallback.width(),
            fallback.height(),
            null,
            extension(candidate.fileName()),
            mimeType,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
      }
      return MetadataResult.unsupported(
          candidate.modifiedAt(),
          extension(candidate.fileName()),
          mimeType,
          "photo_metadata_unsupported",
          exception.getMessage());
    } catch (IOException | SecurityException exception) {
      return MetadataResult.failed(
          candidate.modifiedAt(),
          extension(candidate.fileName()),
          mimeType,
          "photo_metadata_failed",
          exception.getMessage());
    }
  }

  private MetadataResult extractVideo(
      AssetRepository.MetadataCandidateRow candidate, Path path, String mimeType) {
    Process process = null;
    try {
      process =
          new ProcessBuilder(
                  ffprobePath,
                  "-v",
                  "error",
                  "-print_format",
                  "json",
                  "-show_format",
                  "-show_streams",
                  path.toString())
              .start();
      Process running = process;
      CompletableFuture<String> stdout =
          CompletableFuture.supplyAsync(
              () -> readBounded(running.getInputStream(), STDOUT_LIMIT_BYTES));
      CompletableFuture<String> stderr =
          CompletableFuture.supplyAsync(
              () -> readBounded(running.getErrorStream(), STDERR_LIMIT_BYTES));
      boolean exited = running.waitFor(ffprobeTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!exited) {
        running.destroyForcibly();
        return MetadataResult.failed(
            candidate.modifiedAt(),
            extension(candidate.fileName()),
            mimeType,
            "ffprobe_timeout",
            "ffprobe timed out");
      }
      String output = stdout.join();
      String error = stderr.join();
      if (running.exitValue() != 0) {
        return MetadataResult.failed(
            candidate.modifiedAt(),
            extension(candidate.fileName()),
            mimeType,
            "ffprobe_failed",
            blankToNull(error));
      }
      return videoResult(candidate, mimeType, objectMapper.readTree(output));
    } catch (IOException exception) {
      return MetadataResult.unsupported(
          candidate.modifiedAt(),
          extension(candidate.fileName()),
          mimeType,
          "ffprobe_unavailable",
          exception.getMessage());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return MetadataResult.failed(
          candidate.modifiedAt(),
          extension(candidate.fileName()),
          mimeType,
          "ffprobe_interrupted",
          "ffprobe was interrupted");
    } catch (RuntimeException exception) {
      return MetadataResult.failed(
          candidate.modifiedAt(),
          extension(candidate.fileName()),
          mimeType,
          "ffprobe_parse_failed",
          exception.getMessage());
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  private MetadataResult videoResult(
      AssetRepository.MetadataCandidateRow candidate, String mimeType, JsonNode root) {
    JsonNode format = root.path("format");
    JsonNode video = null;
    JsonNode audio = null;
    for (JsonNode stream : root.path("streams")) {
      String codecType = text(stream, "codec_type");
      if (video == null && "video".equals(codecType)) {
        video = stream;
      } else if (audio == null && "audio".equals(codecType)) {
        audio = stream;
      }
    }
    OffsetDateTime capturedAt =
        firstDate(
            parseOffsetDateTime(text(format.path("tags"), "creation_time")),
            video == null ? null : parseOffsetDateTime(text(video.path("tags"), "creation_time")),
            candidate.modifiedAt());
    return new MetadataResult(
        EXTRACTION_STATUS_EXTRACTED,
        null,
        null,
        capturedAt,
        intValue(video, "width"),
        intValue(video, "height"),
        null,
        extension(candidate.fileName()),
        mimeType,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "",
        durationMs(format, video),
        rotation(video),
        text(format, "format_name"),
        video == null ? null : text(video, "codec_name"),
        audio == null ? null : text(audio, "codec_name"),
        video == null
            ? null
            : firstNonBlank(text(video, "avg_frame_rate"), text(video, "r_frame_rate")),
        bitrate(format, video),
        audio != null);
  }

  private AssetRepository.MetadataUpdate toProcessingUpdate(
      AssetRepository.MetadataCandidateRow candidate, OffsetDateTime now) {
    return new AssetRepository.MetadataUpdate(
        candidate.assetId(),
        null,
        null,
        null,
        null,
        extension(candidate.fileName()),
        null,
        null,
        null,
        SOURCE_VERSION,
        EXTRACTION_STATUS_PROCESSING,
        now,
        null,
        null,
        EXTRACTOR,
        EXTRACTOR_VERSION,
        SCHEMA_VERSION,
        candidate.sizeBytes(),
        candidate.modifiedAt(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private AssetRepository.MetadataUpdate toUpdate(
      AssetRepository.MetadataCandidateRow candidate,
      MetadataResult result,
      OffsetDateTime now,
      long extractionDurationMs) {
    return new AssetRepository.MetadataUpdate(
        candidate.assetId(),
        result.capturedAt(),
        result.width(),
        result.height(),
        result.orientation(),
        result.fileExtension(),
        result.mimeType(),
        result.cameraMake(),
        result.cameraModel(),
        SOURCE_VERSION,
        result.status(),
        now,
        result.errorCode(),
        result.errorMessage(),
        EXTRACTOR,
        EXTRACTOR_VERSION,
        SCHEMA_VERSION,
        candidate.sizeBytes(),
        candidate.modifiedAt(),
        result.lensModel(),
        result.focalLength(),
        result.aperture(),
        result.exposureTime(),
        result.iso(),
        result.latitude(),
        result.longitude(),
        result.title(),
        result.description(),
        result.keywords(),
        result.durationMs(),
        result.displayRotation(),
        result.container(),
        result.videoCodec(),
        result.audioCodec(),
        result.frameRate(),
        result.bitrate(),
        result.hasAudio(),
        extractionDurationMs);
  }

  private String probeContentType(Path path) {
    try {
      return Files.probeContentType(path);
    } catch (IOException | SecurityException exception) {
      return null;
    }
  }

  private ImageDimensions dimensions(
      ExifIFD0Directory ifd0, ExifSubIFDDirectory subIfd, ImageDimensions fallback) {
    Integer width =
        firstInteger(
            subIfd, ExifDirectoryBase.TAG_EXIF_IMAGE_WIDTH, ExifDirectoryBase.TAG_IMAGE_WIDTH);
    Integer height =
        firstInteger(
            subIfd, ExifDirectoryBase.TAG_EXIF_IMAGE_HEIGHT, ExifDirectoryBase.TAG_IMAGE_HEIGHT);
    if (width == null) {
      width = integer(ifd0, ExifDirectoryBase.TAG_IMAGE_WIDTH);
    }
    if (height == null) {
      height = integer(ifd0, ExifDirectoryBase.TAG_IMAGE_HEIGHT);
    }
    return new ImageDimensions(
        width == null && fallback != null ? fallback.width() : width,
        height == null && fallback != null ? fallback.height() : height);
  }

  private ImageDimensions imageIoDimensions(Path path) {
    try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
      if (input == null) {
        return new ImageDimensions(null, null);
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        return new ImageDimensions(null, null);
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
      } finally {
        reader.dispose();
      }
    } catch (IOException | SecurityException exception) {
      return new ImageDimensions(null, null);
    }
  }

  private PhotoText xmpText(Metadata metadata) {
    XmpDirectory xmp = metadata.getFirstDirectoryOfType(XmpDirectory.class);
    XMPMeta meta = xmp == null ? null : xmp.getXMPMeta();
    if (meta == null) {
      return new PhotoText(null, null, List.of());
    }
    List<String> keywords = new ArrayList<>();
    try {
      int count = meta.countArrayItems(XMPConst.NS_DC, "subject");
      for (int index = 1; index <= count; index++) {
        keywords.add(blankToNull(meta.getArrayItem(XMPConst.NS_DC, "subject", index).getValue()));
      }
    } catch (XMPException ignored) {
      // Metadata varies widely; partial XMP extraction is still useful.
    }
    return new PhotoText(
        localized(meta, XMPConst.NS_DC, "title"),
        localized(meta, XMPConst.NS_DC, "description"),
        keywords.stream().filter(value -> value != null && !value.isBlank()).toList());
  }

  private String localized(XMPMeta meta, String namespace, String property) {
    try {
      var value = meta.getLocalizedText(namespace, property, null, XMPConst.X_DEFAULT);
      return value == null ? null : blankToNull(value.getValue());
    } catch (XMPException exception) {
      return null;
    }
  }

  private List<String> keywords(IptcDirectory iptc, PhotoText xmp) {
    Set<String> values = new LinkedHashSet<>();
    values.addAll(xmp.keywords());
    Collection<String> iptcKeywords = iptc == null ? null : iptc.getKeywords();
    if (iptcKeywords != null) {
      values.addAll(iptcKeywords);
    }
    return values.stream()
        .map(this::blankToNull)
        .filter(value -> value != null && !value.isBlank())
        .toList();
  }

  private OffsetDateTime firstDate(ExifSubIFDDirectory subIfd, OffsetDateTime fallback) {
    Date date = subIfd == null ? null : subIfd.getDateOriginal(UTC);
    if (date == null && subIfd != null) {
      date = subIfd.getDateDigitized(UTC);
    }
    return date == null ? fallback : OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
  }

  private OffsetDateTime firstDate(
      OffsetDateTime first, OffsetDateTime second, OffsetDateTime fallback) {
    if (first != null) {
      return first;
    }
    return second == null ? fallback : second;
  }

  private OffsetDateTime parseOffsetDateTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private Integer firstInteger(Directory directory, int... tags) {
    for (int tag : tags) {
      Integer value = integer(directory, tag);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private Integer integer(Directory directory, int tag) {
    return directory == null ? null : directory.getInteger(tag);
  }

  private String string(Directory directory, int tag) {
    return directory == null ? null : blankToNull(directory.getString(tag));
  }

  private Double rationalDouble(Directory directory, int tag) {
    Rational value = directory == null ? null : directory.getRational(tag);
    return value == null ? null : value.doubleValue();
  }

  private String rationalString(Directory directory, int tag) {
    Rational value = directory == null ? null : directory.getRational(tag);
    return value == null ? null : value.toSimpleString(true);
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      String normalized = blankToNull(value);
      if (normalized != null) {
        return normalized;
      }
    }
    return null;
  }

  private String text(JsonNode node, String field) {
    if (node == null
        || node.isMissingNode()
        || node.path(field).isMissingNode()
        || node.path(field).isNull()) {
      return null;
    }
    return blankToNull(node.path(field).asText());
  }

  private Integer intValue(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
      return null;
    }
    return node.path(field).canConvertToInt() ? node.path(field).asInt() : null;
  }

  private Long longValue(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private Long durationMs(JsonNode format, JsonNode video) {
    String value = firstNonBlank(text(format, "duration"), text(video, "duration"));
    if (value == null) {
      return null;
    }
    try {
      return Math.round(Double.parseDouble(value) * 1000.0d);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private Long bitrate(JsonNode format, JsonNode video) {
    Long value = longValue(format, "bit_rate");
    return value == null ? longValue(video, "bit_rate") : value;
  }

  private Integer rotation(JsonNode video) {
    if (video == null) {
      return null;
    }
    Integer tagRotation = parseInteger(text(video.path("tags"), "rotate"));
    if (tagRotation != null) {
      return tagRotation;
    }
    for (JsonNode sideData : video.path("side_data_list")) {
      Integer rotation = parseInteger(text(sideData, "rotation"));
      if (rotation != null) {
        return rotation;
      }
    }
    return null;
  }

  private Integer parseInteger(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String readBounded(InputStream input, int limitBytes) {
    try (InputStream stream = input;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int total = 0;
      int read;
      while ((read = stream.read(buffer)) >= 0) {
        int remaining = limitBytes - total;
        if (remaining <= 0) {
          break;
        }
        int accepted = Math.min(read, remaining);
        output.write(buffer, 0, accepted);
        total += accepted;
      }
      return output.toString(StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return "";
    }
  }

  private boolean sameInstant(OffsetDateTime first, OffsetDateTime second) {
    return first != null
        && second != null
        && first.toInstant().toEpochMilli() == second.toInstant().toEpochMilli();
  }

  private boolean isPhotoPath(Path path) {
    String extension = extension(path.getFileName() == null ? "" : path.getFileName().toString());
    return Set.of("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "tif", "tiff")
        .contains(extension);
  }

  private boolean isVideoPath(Path path) {
    String extension = extension(path.getFileName() == null ? "" : path.getFileName().toString());
    return Set.of("mp4", "mov", "m4v", "avi", "mkv").contains(extension);
  }

  private String extension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return null;
    }
    return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record ImageDimensions(Integer width, Integer height) {}

  private record PhotoText(String title, String description, List<String> keywords) {}

  private record MetadataResult(
      String status,
      String errorCode,
      String errorMessage,
      OffsetDateTime capturedAt,
      Integer width,
      Integer height,
      Integer orientation,
      String fileExtension,
      String mimeType,
      String cameraMake,
      String cameraModel,
      String lensModel,
      Double focalLength,
      Double aperture,
      String exposureTime,
      Integer iso,
      Double latitude,
      Double longitude,
      String title,
      String description,
      String keywords,
      Long durationMs,
      Integer displayRotation,
      String container,
      String videoCodec,
      String audioCodec,
      String frameRate,
      Long bitrate,
      Boolean hasAudio) {
    static MetadataResult unsupported(
        OffsetDateTime capturedAt,
        String fileExtension,
        String mimeType,
        String errorCode,
        String errorMessage) {
      return empty(
          EXTRACTION_STATUS_UNSUPPORTED,
          capturedAt,
          fileExtension,
          mimeType,
          errorCode,
          errorMessage);
    }

    static MetadataResult failed(
        OffsetDateTime capturedAt,
        String fileExtension,
        String mimeType,
        String errorCode,
        String errorMessage) {
      return empty(
          EXTRACTION_STATUS_FAILED, capturedAt, fileExtension, mimeType, errorCode, errorMessage);
    }

    private static MetadataResult empty(
        String status,
        OffsetDateTime capturedAt,
        String fileExtension,
        String mimeType,
        String errorCode,
        String errorMessage) {
      return new MetadataResult(
          status,
          errorCode,
          errorMessage,
          capturedAt,
          null,
          null,
          null,
          fileExtension,
          mimeType,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          "",
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }
  }
}
