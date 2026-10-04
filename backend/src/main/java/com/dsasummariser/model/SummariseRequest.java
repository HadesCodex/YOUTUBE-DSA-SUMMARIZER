package com.dsasummariser.model;

import jakarta.validation.constraints.NotBlank;

/**
 * url        - the YouTube link (required, used to extract the video ID)
 * transcript - optional pasted transcript; if present we skip fetching (fallback option)
 */
public record SummariseRequest(
        @NotBlank(message = "url must not be blank") String url,
        String transcript
) {}
