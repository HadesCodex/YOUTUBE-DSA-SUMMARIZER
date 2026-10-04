package com.dsasummariser.service;

import com.dsasummariser.model.Chunk;
import com.dsasummariser.model.ScoredChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkScorerTest {

    // Tiny word lists and round weights so every expected number can be checked by hand.
    // Weights: strong 3, weak 1, step 1, filler 2, problem 2. The edge trim is 10 words here (150 in production)
    // so that 100-word test chunks can show the first/last 10 words being ignored.
    private final ScoringConfig config = new ScoringConfig(
            List.of("time complexity"), List.of("array"), List.of("first"), List.of("subscribe"),
            List.of("your task"),
            3.0, 1.0, 1.0, 2.0, 2.0, 10);
    private final ChunkScorer scorer = new ChunkScorer(config);

    /** A chunk of exactly wordCount words: the given text, padded with the neutral word "zzz". */
    private static Chunk chunk(int index, String text, int wordCount) {
        int used = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        String padding = IntStream.range(0, wordCount - used).mapToObj(i -> "zzz").collect(Collectors.joining(" "));
        return new Chunk(index, (text + " " + padding).trim(), wordCount);
    }

    private ScoredChunk scoreMiddle(String text, int wordCount) {
        return scorer.score(chunk(1, text, wordCount), 5);   // index 1 of 5: not intro or outro
    }

    @Test
    void strongKeywordCountsStrongWeightPer100Words() {
        // 1 hit * 3 points, in exactly 100 words -> 3.0
        assertEquals(3.0, scoreMiddle("time complexity", 100).keywordScore(), 1e-9);
    }

    @Test
    void weakKeywordCountsWeakWeight() {
        assertEquals(2.0, scoreMiddle("array array", 100).keywordScore(), 1e-9);
    }

    @Test
    void scoreIsPerHundredWordsSoChunkSizeDoesNotMatter() {
        double in100 = scoreMiddle("time complexity", 100).keywordScore();
        double in200 = scoreMiddle("time complexity", 200).keywordScore();
        assertEquals(in100 / 2, in200, 1e-9);
    }

    @Test
    void matchingIgnoresCaseAndPunctuation() {
        assertEquals(3.0, scoreMiddle("The Time Complexity, here.", 100).keywordScore(), 1e-9);
    }

    @Test
    void pluralMatchesButSubstringsDoNot() {
        assertEquals(1.0, scoreMiddle("arrays", 100).keywordScore(), 1e-9);
        assertEquals(0.0, scoreMiddle("arrayish", 100).keywordScore(), 1e-9);
        assertEquals(0.0, scoreMiddle("subarray", 100).keywordScore(), 1e-9);
    }

    @Test
    void stepMarkersAddToStepScore() {
        assertEquals(2.0, scoreMiddle("first first", 100).stepScore(), 1e-9);
    }

    @Test
    void fillerPhrasesArePenalised() {
        assertEquals(2.0, scoreMiddle("please subscribe", 100).penalty(), 1e-9);
    }

    @Test
    void problemStatementPhrasesCountProblemWeight() {
        assertEquals(2.0, scoreMiddle("your task is to place cows", 100).problemScore(), 1e-9);
        assertEquals(0.0, scoreMiddle("array", 100).problemScore(), 1e-9);
    }

    // ----- intro/outro: the first and last chunk are scored on their body (outer words ignored) -----

    private static final String TEN_NEUTRAL_WORDS = "zzz zzz zzz zzz zzz zzz zzz zzz zzz zzz";

    /** A chunk of wordCount words that starts with `head`, ends with `tail` and is neutral "zzz" in between. */
    private static Chunk edgeChunk(int index, int wordCount, String head, String tail) {
        int fixed = (head.isBlank() ? 0 : head.trim().split(" ").length) + (tail.isBlank() ? 0 : tail.trim().split(" ").length);
        String middle = IntStream.range(0, wordCount - fixed).mapToObj(i -> "zzz").collect(Collectors.joining(" "));
        return new Chunk(index, (head + " " + middle + " " + tail).trim().replaceAll(" +", " "), wordCount);
    }

    @Test
    void firstChunkIgnoresItsFirstWordsWherePlugsSit() {
        // "subscribe" and the keyword both sit in the first 10 words of chunk 0, so neither is seen.
        ScoredChunk s = scorer.score(edgeChunk(0, 100, "welcome subscribe time complexity", ""), 5);
        assertEquals(0.0, s.penalty(), 1e-9);
        assertEquals(0.0, s.keywordScore(), 1e-9);
    }

    @Test
    void lastChunkIgnoresItsLastWordsWhereSignOffsSit() {
        ScoredChunk s = scorer.score(edgeChunk(4, 100, "", "thanks subscribe bye"), 5);
        assertEquals(0.0, s.penalty(), 1e-9);
    }

    @Test
    void firstChunkIsNotTrimmedAtItsEndAndLastChunkIsNotTrimmedAtItsStart() {
        // Filler in the part that is NOT ignored still counts: 1 hit in the 90-word body = 2 * 100/90.
        assertEquals(2.0 * 100 / 90, scorer.score(edgeChunk(0, 100, "", "subscribe"), 5).penalty(), 1e-9);
        assertEquals(2.0 * 100 / 90, scorer.score(edgeChunk(4, 100, "subscribe", ""), 5).penalty(), 1e-9);
    }

    @Test
    void edgeChunksAreScoredPerHundredWordsOfTheirBody() {
        // Chunk 0: 10 ignored words, then "time complexity" in a 90-word body: 3 points * 100/90.
        Chunk c = new Chunk(0, TEN_NEUTRAL_WORDS + " time complexity "
                + IntStream.range(0, 88).mapToObj(i -> "zzz").collect(Collectors.joining(" ")), 100);
        assertEquals(3.0 * 100 / 90, scorer.score(c, 5).keywordScore(), 1e-9);
    }

    @Test
    void middleChunksAreNeverTrimmed() {
        assertEquals(2.0, scorer.score(edgeChunk(2, 100, "subscribe", ""), 5).penalty(), 1e-9);
        assertEquals(2.0, scorer.score(edgeChunk(2, 100, "", "subscribe"), 5).penalty(), 1e-9);
    }

    @Test
    void nothingIsTrimmedWhenThereAreFewerThanThreeChunks() {
        // With 1 or 2 chunks there is no real middle, so first/last are not treated as intro/outro.
        assertEquals(2.0, scorer.score(edgeChunk(0, 100, "subscribe", ""), 1).penalty(), 1e-9);
        assertEquals(2.0, scorer.score(edgeChunk(1, 100, "", "subscribe"), 2).penalty(), 1e-9);
    }

    @Test
    void neverIgnoresMoreThanHalfOfAChunk() {
        // 12 words, trim is 10 but capped at 6: the body is the last 6 words, which hold the keyword.
        Chunk c = new Chunk(0, "zzz zzz zzz zzz zzz zzz array zzz zzz zzz zzz zzz", 12);
        assertEquals(1.0 * 100 / 6, scorer.score(c, 5).keywordScore(), 1e-9);
    }

    @Test
    void theWholeChunkIsStillReportedEvenThoughOnlyTheBodyIsScored() {
        Chunk c = edgeChunk(0, 100, "welcome", "bye");
        ScoredChunk s = scorer.score(c, 5);
        assertEquals(100, s.chunk().wordCount());
        assertEquals(c.text(), s.chunk().text());
    }

    @Test
    void anIntroWithRealTeachingScoresWellButAPureSignOffDoesNot() {
        // Intro chunk: plug in the ignored head, teaching in the body.
        Chunk teaching = new Chunk(0, "welcome subscribe zzz zzz zzz zzz zzz zzz zzz zzz time complexity "
                + IntStream.range(0, 88).mapToObj(i -> "zzz").collect(Collectors.joining(" ")), 100);
        // Outro chunk that is plug all the way through.
        Chunk plugOnly = new Chunk(4, IntStream.range(0, 100).mapToObj(i -> "subscribe").collect(Collectors.joining(" ")), 100);

        assertEquals(true, scorer.score(teaching, 5).total() > 0);
        assertEquals(true, scorer.score(plugOnly, 5).total() < 0);
    }

    @Test
    void defaultEdgeTrimIs150Words() {
        assertEquals(150, ScoringConfig.defaults().edgeTrimWords());
    }

    @Test
    void totalCombinesAllParts() {
        // keywords 3 + 1 = 4, steps 1, problem 2, penalty 2 -> 4 + 1 + 2 - 2 = 5
        ScoredChunk s = scoreMiddle("time complexity array first your task subscribe", 100);
        assertEquals(4.0, s.keywordScore(), 1e-9);
        assertEquals(1.0, s.stepScore(), 1e-9);
        assertEquals(2.0, s.problemScore(), 1e-9);
        assertEquals(2.0, s.penalty(), 1e-9);
        assertEquals(5.0, s.total(), 1e-9);
    }

    @Test
    void chunkWithNoMatchesScoresZero() {
        assertEquals(0.0, scoreMiddle("", 100).total(), 1e-9);
    }

    @Test
    void emptyChunkListGivesEmptyResult() {
        assertEquals(0, scorer.scoreAll(List.of()).size());
    }

    @Test
    void zeroWordChunkDoesNotDivideByZero() {
        assertEquals(0.0, scorer.score(new Chunk(1, "", 0), 5).total(), 1e-9);
    }

    @Test
    void defaultConfigScoresRealDsaSentenceAboveSmallTalk() {
        ChunkScorer defaults = new ChunkScorer();
        ScoredChunk dsa = defaults.score(new Chunk(1,
                "first we use two pointers on the sorted array then the time complexity is o of n", 16), 5);
        ScoredChunk chatter = defaults.score(new Chunk(1,
                "hey guys welcome back to the channel please subscribe and hit the bell icon", 14), 5);
        assertEquals(true, dsa.total() > chatter.total());
    }

    @Test
    void defaultConfigTreatsLogarithmicAndLogBaseAsStrongKeywords() {
        ChunkScorer defaults = new ChunkScorer();
        // One strong hit (weight 3) per 100 words. Neither phrase is matched by any other list.
        assertEquals(3.0, defaults.score(chunk(1, "the time is logarithmic", 100), 5).keywordScore(), 1e-9);
        assertEquals(3.0, defaults.score(chunk(1, "we take log base two", 100), 5).keywordScore(), 1e-9);
    }
}
