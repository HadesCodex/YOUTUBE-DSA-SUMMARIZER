package com.dsasummariser.service;

import com.dsasummariser.model.Chunk;
import com.dsasummariser.model.ScoredChunk;
import com.dsasummariser.model.TranscriptStats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkSelectorTest {

    private final ChunkSelector selector = new ChunkSelector();

    /** A scored chunk with the given position, size and total score (all of it from keywords). */
    private static ScoredChunk sc(int index, int words, double score) {
        return new ScoredChunk(new Chunk(index, "chunk" + index, words), score, 0, 0, 0);
    }

    private static List<Integer> indexes(SelectionResult result) {
        return result.kept().stream().map(s -> s.chunk().index()).toList();
    }

    @Test
    void emptyInputGivesEmptyResult() {
        SelectionResult result = selector.select(List.of());
        assertEquals(List.of(), indexes(result));
        assertEquals(new TranscriptStats(0, 0, 0, 0), result.stats());
    }

    @Test
    void transcriptWithinBudgetKeepsEverything() {
        SelectionResult result = selector.select(List.of(sc(0, 500, 1), sc(1, 500, 9)), 1000);
        assertEquals(List.of(0, 1), indexes(result));
        assertEquals(new TranscriptStats(1000, 2, 2, 1000), result.stats());
    }

    @Test
    void keepsHighestScoresThenRestoresOriginalOrder() {
        // Budget fits two 500-word chunks. Scores say keep indexes 3 and 1, but output must be 1 then 3.
        List<ScoredChunk> chunks = List.of(sc(0, 500, 2), sc(1, 500, 8), sc(2, 500, 1), sc(3, 500, 9));
        SelectionResult result = selector.select(chunks, 1000);
        assertEquals(List.of(1, 3), indexes(result));
        assertEquals(new TranscriptStats(2000, 4, 2, 1000), result.stats());
    }

    @Test
    void skipsAChunkThatDoesNotFitButStillTakesSmallerLaterOnes() {
        // Budget 1000: take A (800), skip B (800 would exceed), then C (200) still fits.
        List<ScoredChunk> chunks = List.of(sc(0, 800, 10), sc(1, 800, 9), sc(2, 200, 1));
        SelectionResult result = selector.select(chunks, 1000);
        assertEquals(List.of(0, 2), indexes(result));
        assertEquals(1000, result.stats().wordsSent());
    }

    @Test
    void whenNothingFitsKeepsTheSingleBestChunk() {
        List<ScoredChunk> chunks = List.of(sc(0, 500, 1), sc(1, 500, 7), sc(2, 500, 3));
        SelectionResult result = selector.select(chunks, 100);
        assertEquals(List.of(1), indexes(result));
        assertEquals(500, result.stats().wordsSent());
    }

    @Test
    void negativeScoresAreStillRankedCorrectly() {
        List<ScoredChunk> chunks = List.of(sc(0, 500, -4), sc(1, 500, -1), sc(2, 500, -9));
        assertEquals(List.of(1), indexes(selector.select(chunks, 600)));
    }

    @Test
    void ties_keepEarlierChunkBecauseSortIsStable() {
        List<ScoredChunk> chunks = List.of(sc(0, 500, 5), sc(1, 500, 5));
        assertEquals(List.of(0), indexes(selector.select(chunks, 600)));
    }

    @Test
    void defaultBudgetAndSectionCountAreNamedConstants() {
        // 10 chunks of 650 words = 6500 words; scores equal the index (chunk 0 scores 0, so it is never "positive").
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            chunks.add(sc(i, 650, i));
        }
        SelectionResult result = selector.select(chunks);
        assertEquals(5200, ChunkSelector.WORD_BUDGET);
        assertEquals(4, ChunkSelector.SECTIONS);
        // Sections are chunks [0,1], [2,3,4], [5,6], [7,8,9]; their best are 1, 4, 6, 9 (all guaranteed).
        // floor(5200 / 650) = 8 chunks fit, so the 4 free slots go to the best remaining: 8, 7, 5 and 3.
        assertEquals(List.of(1, 3, 4, 5, 6, 7, 8, 9), indexes(result));
        assertEquals(new TranscriptStats(6500, 10, 8, 5200), result.stats());
    }

    @Test
    void guaranteesTheBestChunkOfEachSectionEvenWhenOneRegionScoresHighest() {
        // 8 chunks of 500 words, budget fits 4. Plain top-4 would be 0,1,2,3 (all from the first half).
        // Sections are [0,1] [2,3] [4,5] [6,7]; their best chunks are 0, 2, 5, 7.
        double[] scores = {10, 9, 8, 7, 1, 2, 1, 3};
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            chunks.add(sc(i, 500, scores[i]));
        }
        assertEquals(List.of(0, 2, 5, 7), indexes(selector.select(chunks, 2000)));
    }

    @Test
    void remainingBudgetAfterGuaranteedChunksIsFilledByGlobalScore() {
        double[] scores = {10, 9, 8, 7, 1, 2, 1, 3};
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            chunks.add(sc(i, 500, scores[i]));
        }
        // Budget fits 5: the 4 guaranteed (0, 2, 5, 7) plus the best remaining chunk, which is 1 (score 9).
        assertEquals(List.of(0, 1, 2, 5, 7), indexes(selector.select(chunks, 2500)));
    }

    @Test
    void neverUsesMoreSectionsThanChunks() {
        // 1 and 2 chunks (fewer than SECTIONS = 4) must not crash or create empty sections.
        SelectionResult one = selector.select(List.of(sc(0, 3000, 5)), 1000);
        assertEquals(List.of(0), indexes(one));   // nothing fits, so the single best chunk is kept

        SelectionResult two = selector.select(List.of(sc(0, 600, 5), sc(1, 600, 4)), 700);
        assertEquals(List.of(0), indexes(two));   // both are guaranteed (one per section); only the best fits

        SelectionResult three = selector.select(List.of(sc(0, 500, 3), sc(1, 500, 2), sc(2, 500, 1)), 1000);
        assertEquals(List.of(0, 1), indexes(three));
    }

    @Test
    void guaranteedChunksExceedingTheBudgetAreAddedInScoreOrder() {
        // Four sections of one 1000-word chunk each, budget 2500: only two fit.
        // They must be the two highest scores (9 and 7), not the first two by position.
        List<ScoredChunk> chunks = List.of(sc(0, 1000, 5), sc(1, 1000, 9), sc(2, 1000, 7), sc(3, 1000, 6));
        SelectionResult result = selector.select(chunks, 2500);
        assertEquals(List.of(1, 2), indexes(result));
        assertEquals(2000, result.stats().wordsSent());
    }

    @Test
    void sectionWithNoPositiveChunkIsSkipped() {
        // Sections [0,1] [2,3] [4,5] [6,7]. Section 2 has scores -3 and 0: its best is 0, not above zero,
        // so it gets no guaranteed slot. The free slot goes to the next-best chunk overall (chunk 1, score 4).
        double[] scores = {5, 4, -3, 0, 6, 1, 2, 3};
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            chunks.add(sc(i, 500, scores[i]));
        }
        List<Integer> kept = indexes(selector.select(chunks, 2000));
        assertEquals(List.of(0, 1, 4, 7), kept);
        assertEquals(false, kept.contains(2) || kept.contains(3));
    }

    @Test
    void allNegativeScoresStillFillTheBudgetByScore() {
        // No section qualifies for a guarantee, but step 2 still picks the best available.
        List<ScoredChunk> chunks = List.of(sc(0, 500, -5), sc(1, 500, -1), sc(2, 500, -3), sc(3, 500, -2));
        assertEquals(List.of(1, 3), indexes(selector.select(chunks, 1000)));
    }

    @Test
    void transcriptWithinBudgetKeepsEverythingEvenWithManySections() {
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            chunks.add(sc(i, 100, i - 4));   // includes negative scores: still all kept
        }
        SelectionResult result = selector.select(chunks, 1000);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), indexes(result));
        assertEquals(new TranscriptStats(800, 8, 8, 800), result.stats());
    }

    @Test
    void keptTextJoinsChunksInOrder() {
        SelectionResult result = selector.select(List.of(sc(0, 10, 1), sc(1, 10, 2)), 100);
        assertEquals("chunk0 chunk1", result.keptText());
    }
}
