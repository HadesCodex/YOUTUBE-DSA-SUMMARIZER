package com.dsasummariser.service;

import com.dsasummariser.model.ScoredChunk;
import com.dsasummariser.model.TranscriptStats;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps the best chunks that fit in a word budget, then puts them back in lecture order.
 *
 * Plain top-score selection clusters where the teacher is most keyword-dense (one topic),
 * so selection has two steps:
 *   1. Split the chunks by position into SECTIONS parts and guarantee the best chunk of each part,
 *      but only if its score is above zero (a weak or filler-only part does not waste a slot).
 *   2. Fill the rest of the budget with the highest-scoring chunks overall.
 * Both steps are greedy by score: simple and good enough for about 10-30 chunks.
 */
@Component
public class ChunkSelector {

    /** Maximum words we are willing to send to the LLM. */
    public static final int WORD_BUDGET = 5200;

    /** The budget holds about 8 chunks of 650 words: 4 are reserved for coverage, leaving about 4 free picks. */
    public static final int SECTIONS = 4;

    private static final Comparator<ScoredChunk> BY_SCORE_DESC =
            Comparator.comparingDouble(ScoredChunk::total).reversed();

    public SelectionResult select(List<ScoredChunk> chunks) {
        return select(chunks, WORD_BUDGET);
    }

    public SelectionResult select(List<ScoredChunk> chunks, int budget) {
        int totalWords = chunks.stream().mapToInt(s -> s.chunk().wordCount()).sum();

        List<ScoredChunk> kept = new ArrayList<>();
        if (totalWords <= budget) {
            // Short lecture: nothing to filter.
            kept.addAll(chunks);
        } else {
            int used = 0;

            // Step 1: guaranteed best chunk of each section, highest score first.
            List<ScoredChunk> guaranteed = bestOfEachSection(chunks);
            guaranteed.sort(BY_SCORE_DESC);
            used = addWhileFits(guaranteed, kept, used, budget);

            // Step 2: fill what is left of the budget by global score (kept ones are skipped).
            List<ScoredChunk> byScore = new ArrayList<>(chunks);
            byScore.sort(BY_SCORE_DESC);
            addWhileFits(byScore, kept, used, budget);

            if (kept.isEmpty()) {
                // Every chunk is bigger than the budget: better to send the best one than nothing.
                kept.add(byScore.get(0));
            }
            // Restore the order the lecturer said things in.
            kept.sort(Comparator.comparingInt(s -> s.chunk().index()));
        }

        int wordsSent = kept.stream().mapToInt(s -> s.chunk().wordCount()).sum();
        return new SelectionResult(kept, new TranscriptStats(totalWords, chunks.size(), kept.size(), wordsSent));
    }

    /**
     * Splits the chunk list into SECTIONS parts of (nearly) equal chunk count and returns the
     * highest-scoring chunk of each part, skipping parts where that best score is not above zero.
     * Never uses more sections than there are chunks, so every section has at least one chunk.
     */
    private List<ScoredChunk> bestOfEachSection(List<ScoredChunk> chunks) {
        int n = chunks.size();
        int sections = Math.min(SECTIONS, n);
        List<ScoredChunk> best = new ArrayList<>();
        for (int s = 0; s < sections; s++) {
            int from = s * n / sections;
            int to = (s + 1) * n / sections;   // exclusive
            ScoredChunk top = chunks.get(from);
            for (int i = from + 1; i < to; i++) {
                if (chunks.get(i).total() > top.total()) {   // strict ">" keeps the earlier chunk on a tie
                    top = chunks.get(i);
                }
            }
            if (top.total() > 0) {
                best.add(top);
            }
        }
        return best;
    }

    /**
     * Adds candidates in the given order while they fit in the budget, skipping any already kept.
     * A candidate that does not fit is skipped, because a smaller one later may still fit.
     * Returns the new total of words used.
     */
    private int addWhileFits(List<ScoredChunk> candidates, List<ScoredChunk> kept, int used, int budget) {
        for (ScoredChunk candidate : candidates) {
            int words = candidate.chunk().wordCount();
            if (!kept.contains(candidate) && used + words <= budget) {
                kept.add(candidate);
                used += words;
            }
        }
        return used;
    }
}
