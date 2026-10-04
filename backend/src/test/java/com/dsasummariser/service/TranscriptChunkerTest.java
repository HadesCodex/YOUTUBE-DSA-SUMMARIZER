package com.dsasummariser.service;

import com.dsasummariser.model.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptChunkerTest {

    private final TranscriptChunker chunker = new TranscriptChunker();

    /** n words w0 w1 w2 ..., with a full stop added to each word whose index is in sentenceEnds. */
    private static String words(int n, int... sentenceEnds) {
        return IntStream.range(0, n)
                .mapToObj(i -> "w" + i + (contains(sentenceEnds, i) ? "." : ""))
                .collect(Collectors.joining(" "));
    }

    private static boolean contains(int[] array, int value) {
        for (int x : array) {
            if (x == value) return true;
        }
        return false;
    }

    private static int totalWords(List<Chunk> chunks) {
        return chunks.stream().mapToInt(Chunk::wordCount).sum();
    }

    @Test
    void emptyAndNullInputGiveNoChunks() {
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   \n ").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
    }

    @Test
    void veryShortTranscriptIsOneChunk() {
        List<Chunk> chunks = chunker.chunk("binary search halves the range");
        assertEquals(1, chunks.size());
        assertEquals(5, chunks.get(0).wordCount());
        assertEquals(0, chunks.get(0).index());
    }

    @Test
    void unpunctuatedTextBreaksAtTargetSize() {
        // 1500 words with no punctuation: 650 + 650 + 200 (the 200-word tail is big enough to stay).
        List<Chunk> chunks = chunker.chunk(words(1500));
        assertEquals(3, chunks.size());
        assertEquals(650, chunks.get(0).wordCount());
        assertEquals(650, chunks.get(1).wordCount());
        assertEquals(200, chunks.get(2).wordCount());
    }

    @Test
    void breaksAtFirstSentenceEndAfterTarget() {
        // Word index 699 ends a sentence, so the first chunk is 700 words, not 650.
        List<Chunk> chunks = chunker.chunk(words(1200, 699));
        assertEquals(700, chunks.get(0).wordCount());
        assertTrue(chunks.get(0).text().endsWith("w699."));
    }

    @Test
    void sentenceEndBeyondMaxIsIgnored() {
        // The only full stop is at word 900, past the 800-word maximum, so we break at the target.
        List<Chunk> chunks = chunker.chunk(words(1500, 900));
        assertEquals(650, chunks.get(0).wordCount());
    }

    @Test
    void sentenceEndBeforeTargetDoesNotCauseAnEarlyBreak() {
        // Full stops at word 100 and 300 are ignored: chunks should still be about target size.
        List<Chunk> chunks = chunker.chunk(words(1500, 100, 300));
        assertEquals(650, chunks.get(0).wordCount());
    }

    @Test
    void tinyTailIsMergedIntoPreviousChunk() {
        // 650 + 650 + 50: the 50-word tail is merged, giving 650 + 700.
        List<Chunk> chunks = chunker.chunk(words(1350));
        assertEquals(2, chunks.size());
        assertEquals(700, chunks.get(1).wordCount());
        assertEquals(1, chunks.get(1).index());
    }

    @Test
    void oneHugeParagraphIsSplitWithNoWordsLostOrDuplicated() {
        String huge = words(10_000);
        List<Chunk> chunks = chunker.chunk(huge);

        assertEquals(10_000, totalWords(chunks));
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
            assertTrue(chunks.get(i).wordCount() <= 1000, "chunk " + i + " too large");
        }
        String rejoined = chunks.stream().map(Chunk::text).collect(Collectors.joining(" "));
        assertEquals(huge, rejoined);
    }

    @Test
    void extraWhitespaceIsNormalised() {
        List<Chunk> chunks = chunker.chunk("  one   two\n\nthree\t four ");
        assertEquals("one two three four", chunks.get(0).text());
        assertEquals(4, chunks.get(0).wordCount());
    }
}
