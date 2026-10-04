package com.dsasummariser.service;

/**
 * Plug-in point for fetching a transcript from YouTube.
 * Implement this with whichever approach works (Java library, or calling YouTube caption data yourself).
 */
public interface TranscriptFetcher {
    String fetch(String videoId);
}
