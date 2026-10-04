package com.dsasummariser.service;

import com.dsasummariser.model.Chunk;
import com.dsasummariser.model.ScoredChunk;
import com.dsasummariser.model.TranscriptStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionResultTest {

    private static ScoredChunk chunk(int index) {
        return new ScoredChunk(new Chunk(index, "c" + index, 10), 1, 0, 0, 0);
    }

    /** A result that kept the given chunk indexes out of `created` chunks. */
    private static SelectionResult kept(int created, int... indexes) {
        List<ScoredChunk> kept = java.util.Arrays.stream(indexes).mapToObj(SelectionResultTest::chunk).toList();
        return new SelectionResult(kept, new TranscriptStats(created * 10, created, kept.size(), kept.size() * 10));
    }

    @Test
    void wholeLectureHasNoMarkers() {
        assertEquals("c0 c1 c2", kept(3, 0, 1, 2).keptText());
    }

    @Test
    void adjacentChunksAreJoinedWithASpace() {
        assertEquals("[...]\n\nc1 c2\n\n[...]", kept(4, 1, 2).keptText());
    }

    @Test
    void aGapBetweenChunksGetsAMarker() {
        assertEquals("c0\n\n[...]\n\nc2", kept(3, 0, 2).keptText());
    }

    @Test
    void skippedStartGetsALeadingMarker() {
        assertEquals("[...]\n\nc1 c2", kept(3, 1, 2).keptText());
    }

    @Test
    void skippedEndGetsATrailingMarker() {
        assertEquals("c0 c1\n\n[...]", kept(3, 0, 1).keptText());
    }

    @Test
    void severalGapsEachGetAMarker() {
        assertEquals("[...]\n\nc1\n\n[...]\n\nc3\n\n[...]\n\nc5\n\n[...]", kept(7, 1, 3, 5).keptText());
    }

    @Test
    void nothingKeptGivesEmptyText() {
        assertEquals("", kept(3).keptText());
    }
}
