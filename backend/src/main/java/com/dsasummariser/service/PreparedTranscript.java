package com.dsasummariser.service;

/** A transcript that has been fetched (or pasted) and already chunked, scored and filtered. */
public record PreparedTranscript(String videoId, String transcript, int wordCount, String source,
                                 SelectionResult selection) {}
