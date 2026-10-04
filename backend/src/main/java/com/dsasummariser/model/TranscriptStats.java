package com.dsasummariser.model;

/** The numbers describing how much text the filter removed. */
public record TranscriptStats(int totalWords, int chunksCreated, int chunksKept, int wordsSent) {}
