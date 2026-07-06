package com.pixierge.api.scans;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanSupportTest {

    @Test
    void matcherHonorsNestedLibraryExclusions() {
        Path root = Path.of("/photos/family");
        ExclusionMatcher matcher = new ExclusionMatcher(List.of(
                "**/@eaDir/**",
                "**/#recycle/**",
                "**/._*"
        ));

        assertThat(matcher.matches(root, root.resolve("@eaDir/thumb.jpg"))).isTrue();
        assertThat(matcher.matches(root, root.resolve("trips/@eaDir/thumb.jpg"))).isTrue();
        assertThat(matcher.matches(root, root.resolve("#recycle/deleted.jpg"))).isTrue();
        assertThat(matcher.matches(root, root.resolve("trips/._beach.jpg"))).isTrue();
        assertThat(matcher.matches(root, root.resolve("trips/beach.jpg"))).isFalse();
    }

    @Test
    void mediaSupportAcceptsPhotosAndVideosOnly() {
        assertThat(MediaFileSupport.isSupportedMedia(Path.of("beach.JPG"))).isTrue();
        assertThat(MediaFileSupport.isSupportedMedia(Path.of("clip.mov"))).isTrue();
        assertThat(MediaFileSupport.isSupportedMedia(Path.of("notes.txt"))).isFalse();
        assertThat(MediaFileSupport.mediaType(Path.of("clip.mp4"))).isEqualTo("video");
        assertThat(MediaFileSupport.mediaType(Path.of("beach.heic"))).isEqualTo("image");
    }
}
