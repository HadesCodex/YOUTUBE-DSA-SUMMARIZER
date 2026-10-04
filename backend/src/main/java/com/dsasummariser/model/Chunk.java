package com.dsasummariser.model;

/** A slice of the transcript. index is its position in the original order (0-based). */
public record Chunk(int index, String text, int wordCount) {}
