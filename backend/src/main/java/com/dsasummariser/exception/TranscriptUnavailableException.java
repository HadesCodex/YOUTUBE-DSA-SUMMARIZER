package com.dsasummariser.exception;

/**
 * The transcript could not be obtained. Every case is HTTP 422, but each carries its own stable error code
 * so the frontend can show the right help without reading the message text:
 *   NO_ENGLISH_CAPTIONS   the video has captions, but none in English
 *   NO_CAPTIONS           the video has no captions at all
 *   VIDEO_UNAVAILABLE     private, removed, age-restricted or blocked
 *   YOUTUBE_REFUSED       YouTube refused this server (HTTP 403/429, or a bot check)
 *   YOUTUBE_UNREACHABLE   timeout, network failure or another YouTube error
 *   TRANSCRIPT_UNAVAILABLE  anything else (empty or unreadable transcript); also the default
 */
public class TranscriptUnavailableException extends RuntimeException {

    public static final String DEFAULT_CODE = "TRANSCRIPT_UNAVAILABLE";

    private final String code;

    public TranscriptUnavailableException(String message) {
        this(DEFAULT_CODE, message);
    }

    public TranscriptUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
