package com.pixierge.api.scans;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class MediaFileSupport {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "tif", "tiff",
            "mp4", "mov", "m4v", "avi", "mkv"
    );

    private MediaFileSupport() {
    }

    static boolean isSupportedMedia(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && SUPPORTED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static String mediaType(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".mp4") || fileName.endsWith(".mov") || fileName.endsWith(".m4v")
                || fileName.endsWith(".avi") || fileName.endsWith(".mkv")) {
            return "video";
        }
        return "image";
    }
}
