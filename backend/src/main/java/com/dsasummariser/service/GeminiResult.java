package com.dsasummariser.service;

import com.dsasummariser.model.TokenUsage;

/**
 * What GeminiClient gives back: the model's raw JSON text, the token counts, the model that actually
 * answered, and how many HTTP calls it took (1 normally; 2 after a retry; 3 after a fallback).
 */
public record GeminiResult(String text, TokenUsage usage, String modelUsed, int attempts) {}
