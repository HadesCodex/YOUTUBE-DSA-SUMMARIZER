package com.dsasummariser.service;

import com.dsasummariser.model.Chunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits a transcript into chunks of roughly 650 words.
 *
 * YouTube auto-captions usually have NO punctuation, so we cannot rely on sentence ends.
 * Rule: aim for TARGET words; if a sentence end (. ? !) appears between TARGET and MAX words,
 * break there; otherwise break at TARGET. A tiny leftover tail is merged into the last chunk
 * (so that final chunk can be a little over MAX_WORDS, never more than about 1000).
 */
@Component
public class TranscriptChunker {

    static final int TARGET_WORDS = 650;
    static final int MAX_WORDS = 800;
    static final int MIN_TAIL_WORDS = 200;

    public List<Chunk> chunk(String transcript) {
        List<Chunk> chunks = new ArrayList<>();
        if (transcript == null || transcript.isBlank()) {
            return chunks;
        }
        String[] words = transcript.trim().split("\\s+");

        int start = 0;
        while (start < words.length) {
            int end = findChunkEnd(words, start);
            chunks.add(makeChunk(chunks.size(), words, start, end));
            start = end;
        }

        mergeTinyTail(chunks);
        return chunks;
    }

    /** Returns the exclusive end index of the chunk that begins at start. */
    private int findChunkEnd(String[] words, int start) {
        int remaining = words.length - start;
        if (remaining <= TARGET_WORDS) {
            return words.length;
        }
        int target = start + TARGET_WORDS;
        int limit = Math.min(start + MAX_WORDS, words.length);
        // Look for the first sentence end at or after the target, but never past MAX_WORDS.
        for (int i = target - 1; i < limit; i++) {
            if (endsSentence(words[i])) {
                return i + 1;
            }
        }
        return target;
    }

    private boolean endsSentence(String word) {
        return word.endsWith(".") || word.endsWith("?") || word.endsWith("!");
    }

    /** If the final chunk is very small, glue it onto the one before it. */
    private void mergeTinyTail(List<Chunk> chunks) {
        if (chunks.size() < 2) {
            return;
        }
        Chunk last = chunks.get(chunks.size() - 1);
        if (last.wordCount() >= MIN_TAIL_WORDS) {
            return;
        }
        Chunk previous = chunks.get(chunks.size() - 2);
        String merged = previous.text() + " " + last.text();
        chunks.remove(chunks.size() - 1);
        chunks.set(chunks.size() - 1, new Chunk(previous.index(), merged, previous.wordCount() + last.wordCount()));
    }

    private Chunk makeChunk(int index, String[] words, int start, int end) {
        String text = String.join(" ", Arrays.copyOfRange(words, start, end));
        return new Chunk(index, text, end - start);
    }
}
