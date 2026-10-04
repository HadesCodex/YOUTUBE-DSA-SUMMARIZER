package com.dsasummariser.service;

import com.dsasummariser.model.Chunk;
import com.dsasummariser.model.ScoredChunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Scores each chunk with explainable parts (all measured per 100 words, so chunks of
 * different sizes compare fairly):
 *   keywordScore = (strong hits * strongWeight + weak hits * weakWeight) per 100 words
 *   stepScore    = step-marker hits * stepWeight per 100 words
 *   problemScore = problem-statement phrase hits * problemWeight per 100 words
 *   penalty      = filler hits * fillerWeight per 100 words
 * total = keywordScore + stepScore + problemScore - penalty. Word lists and weights live in ScoringConfig.
 *
 * Intros and outros: there is NO position penalty. Instead the first and the last chunk are scored on their
 * "body": the first chunk ignores its first edgeTrimWords words and the last chunk ignores its last
 * edgeTrimWords words, which is where plugs and sign-offs sit ("welcome back", "subscribe"). A chunk that
 * mixes real teaching with a plug is therefore judged by its teaching, while a chunk that is only plug
 * has little body left to score. The whole chunk is still what gets sent if it is selected.
 */
@Component
public class ChunkScorer {

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9' ]");
    private static final Pattern SPACES = Pattern.compile(" +");

    private final ScoringConfig config;

    @Autowired
    public ChunkScorer() {
        this(ScoringConfig.defaults());
    }

    public ChunkScorer(ScoringConfig config) {
        this.config = config;
    }

    public List<ScoredChunk> scoreAll(List<Chunk> chunks) {
        List<ScoredChunk> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
            scored.add(score(chunk, chunks.size()));
        }
        return scored;
    }

    ScoredChunk score(Chunk chunk, int totalChunks) {
        // With fewer than 3 chunks there is no real "middle", so first/last is not treated as intro/outro.
        boolean edgeChunk = totalChunks >= 3 && (chunk.index() == 0 || chunk.index() == totalChunks - 1);
        String scoredText = edgeChunk ? body(chunk) : chunk.text();
        int scoredWords = edgeChunk ? countWords(scoredText) : chunk.wordCount();

        String text = normalise(scoredText);
        double per100 = scoredWords == 0 ? 0 : 100.0 / scoredWords;

        double keywordScore = (countAll(text, config.strongKeywords()) * config.strongWeight()
                + countAll(text, config.weakKeywords()) * config.weakWeight()) * per100;
        double stepScore = countAll(text, config.stepMarkers()) * config.stepWeight() * per100;
        double problemScore = countAll(text, config.problemPhrases()) * config.problemWeight() * per100;
        double penalty = countAll(text, config.fillerPhrases()) * config.fillerWeight() * per100;
        return new ScoredChunk(chunk, keywordScore, stepScore, problemScore, penalty);
    }

    /**
     * The chunk without its outer edge: the first chunk loses its first edgeTrimWords words, the last chunk
     * loses its last edgeTrimWords words. At most half of the chunk is ever ignored, so a short chunk still
     * has enough text left to score sensibly.
     */
    private String body(Chunk chunk) {
        String[] words = chunk.text().split(" ");
        int trim = Math.min(config.edgeTrimWords(), words.length / 2);
        if (trim <= 0) {
            return chunk.text();
        }
        boolean isFirst = chunk.index() == 0;
        int from = isFirst ? trim : 0;
        int to = isFirst ? words.length : words.length - trim;
        return String.join(" ", Arrays.copyOfRange(words, from, to));
    }

    private int countWords(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }

    /** Lowercase, punctuation to spaces, single spaces, padded with a space at each end so " word " matches whole words. */
    private String normalise(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String cleaned = NON_WORD.matcher(lower).replaceAll(" ");
        return " " + SPACES.matcher(cleaned).replaceAll(" ").trim() + " ";
    }

    private int countAll(String text, List<String> phrases) {
        int total = 0;
        for (String phrase : phrases) {
            // " array " matches the word; " arrays " matches its plural.
            total += countOccurrences(text, " " + phrase + " ");
            total += countOccurrences(text, " " + phrase + "s ");
        }
        return total;
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        // Step by length-1 so two matches may share one space, as in " first first ".
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length() - 1)) {
            count++;
        }
        return count;
    }
}
