package com.dsasummariser.model;

/** A complexity claim such as "O(log n)" plus the lecturer's reasoning for it. */
public record Complexity(String value, String explanation) {}
