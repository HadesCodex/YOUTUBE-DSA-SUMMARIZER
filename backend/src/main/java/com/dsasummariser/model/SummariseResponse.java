package com.dsasummariser.model;

/**
 * What POST /api/summarise returns. The full transcript is deliberately not included
 * (it can be 20,000+ words); stats describe how much of it was sent to Gemini.
 * modelUsed is the model that actually produced the summary (the fallback model if the main one failed).
 */
public record SummariseResponse(
        String videoId,
        int wordCount,
        String source,          // "fetched" or "pasted"
        String modelUsed,
        TranscriptStats stats,
        TokenUsage usage,
        LectureSummary summary
) {}
