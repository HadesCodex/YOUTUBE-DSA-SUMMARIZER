package com.dsasummariser.model;

/**
 * A chunk plus the parts of its score, kept separate so we can always explain
 * "why did this chunk score X": total = keywordScore + stepScore + problemScore - penalty.
 */
public record ScoredChunk(Chunk chunk, double keywordScore, double stepScore, double problemScore, double penalty) {

    public double total() {
        return keywordScore + stepScore + problemScore - penalty;
    }
}
