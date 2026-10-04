package com.dsasummariser.model;

/**
 * Token counts reported by Gemini for the one call that produced the summary. thoughtTokens are the
 * model's hidden "thinking" tokens, which are billed like output tokens.
 */
public record TokenUsage(int inputTokens, int outputTokens, int thoughtTokens, int totalTokens) {}
