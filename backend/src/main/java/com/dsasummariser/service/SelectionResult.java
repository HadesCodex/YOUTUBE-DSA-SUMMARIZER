package com.dsasummariser.service;

import com.dsasummariser.model.ScoredChunk;
import com.dsasummariser.model.TranscriptStats;

import java.util.List;

/** The chunks we keep (in original order) and the numbers describing the filtering. */
public record SelectionResult(List<ScoredChunk> kept, TranscriptStats stats) {

    /**
     * The text that is sent to Gemini. Chunks that were next to each other in the lecture are joined with a
     * space; wherever text was skipped (between chunks, or before the first / after the last) a [...] marker
     * is inserted so the model knows the text is not continuous.
     */
    public String keptText() {
        StringBuilder text = new StringBuilder();
        int previousIndex = -1;
        for (ScoredChunk scored : kept) {
            int index = scored.chunk().index();
            if (text.length() == 0) {
                if (index > 0) {
                    text.append(PromptBuilder.GAP_MARKER).append("\n\n");
                }
            } else if (index == previousIndex + 1) {
                text.append(' ');
            } else {
                text.append("\n\n").append(PromptBuilder.GAP_MARKER).append("\n\n");
            }
            text.append(scored.chunk().text());
            previousIndex = index;
        }
        if (!kept.isEmpty() && previousIndex < stats.chunksCreated() - 1) {
            text.append("\n\n").append(PromptBuilder.GAP_MARKER);
        }
        return text.toString();
    }
}
