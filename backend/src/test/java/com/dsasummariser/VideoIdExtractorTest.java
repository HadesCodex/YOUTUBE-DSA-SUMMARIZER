package com.dsasummariser;

import com.dsasummariser.exception.InvalidUrlException;
import com.dsasummariser.service.VideoIdExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoIdExtractorTest {

    private final VideoIdExtractor extractor = new VideoIdExtractor();

    @Test
    void parsesWatchUrl() {
        assertEquals("dQw4w9WgXcQ", extractor.extract("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    void parsesWatchUrlWithExtraParams() {
        assertEquals("dQw4w9WgXcQ", extractor.extract("https://www.youtube.com/watch?list=abc&v=dQw4w9WgXcQ&t=10s"));
    }

    @Test
    void parsesShortUrl() {
        assertEquals("dQw4w9WgXcQ", extractor.extract("https://youtu.be/dQw4w9WgXcQ?si=xyz"));
    }

    @Test
    void parsesShortsUrl() {
        assertEquals("dQw4w9WgXcQ", extractor.extract("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
    }

    @Test
    void rejectsNonYoutubeUrl() {
        assertThrows(InvalidUrlException.class, () -> extractor.extract("https://example.com/video"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(InvalidUrlException.class, () -> extractor.extract("  "));
    }
}
