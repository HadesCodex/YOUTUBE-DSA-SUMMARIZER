package com.dsasummariser.exception;

import org.springframework.http.HttpStatus;

/**
 * Anything that goes wrong while producing the summary (missing key, Gemini errors, bad output).
 * Carries the HTTP status and a stable error code for GlobalExceptionHandler. The message is
 * always our own fixed text: upstream details are logged, never returned, and never include the key.
 *
 * transientFailure marks the only failures worth retrying (Gemini HTTP 503, or a connect/read timeout).
 * Key problems, bad requests, blocked output and malformed output are never transient.
 */
public class SummariseException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean transientFailure;

    public SummariseException(HttpStatus status, String code, String message) {
        this(status, code, message, false);
    }

    public SummariseException(HttpStatus status, String code, String message, boolean transientFailure) {
        super(message);
        this.status = status;
        this.code = code;
        this.transientFailure = transientFailure;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isTransientFailure() {
        return transientFailure;
    }
}
