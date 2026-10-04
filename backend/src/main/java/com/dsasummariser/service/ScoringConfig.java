package com.dsasummariser.service;

import java.util.List;

/**
 * Every tunable number and word list for chunk scoring lives here, and nowhere else.
 * To change what counts as "important", edit this file only.
 *
 * All phrases must be lowercase. Matching is on whole words, and a trailing "s" is
 * allowed (so "array" also matches "arrays").
 */
public record ScoringConfig(
        List<String> strongKeywords,   // core DSA concepts, worth strongWeight each
        List<String> weakKeywords,     // common DSA vocabulary, worth weakWeight each
        List<String> stepMarkers,      // "first", "then", ... step-by-step explanation language
        List<String> fillerPhrases,    // channel plugs and chit-chat
        List<String> problemPhrases,   // the lecturer explaining what the problem asks
        double strongWeight,
        double weakWeight,
        double stepWeight,
        double fillerWeight,
        double problemWeight,
        int edgeTrimWords              // first chunk: ignore its first N words; last chunk: ignore its last N words
) {

    public static ScoringConfig defaults() {
        return new ScoringConfig(
                List.of(
                        "time complexity", "space complexity", "edge case", "base case", "brute force",
                        "optimal", "optimised", "optimized", "approach", "dry run", "intuition",
                        "recursion", "recursive", "two pointer", "sliding window", "binary search",
                        "dynamic programming", "memoization", "backtracking", "greedy", "divide and conquer",
                        "worst case", "best case", "average case", "o of n", "o of 1", "o of log n",
                        "log n", "logarithmic", "log base", "n square", "n squared", "pseudo code", "invariant",
                        "bottom up", "top down",
                        "lower bound", "upper bound", "pivot", "partition"),
                List.of(
                        "array", "pointer", "iterate", "iteration", "loop", "index", "variable", "condition",
                        "return", "sorted", "sort", "linked list", "tree", "node", "graph", "stack", "queue",
                        "heap", "hash map", "element", "complexity", "algorithm", "function", "mid", "low", "high"),
                List.of(
                        "first", "second", "third", "then", "next", "finally", "lastly", "step",
                        "after that", "so now", "once", "let's see", "now we"),
                List.of(
                        "subscribe", "like the video", "comment below", "welcome back", "instagram",
                        "linkedin", "telegram", "playlist", "channel", "thank you", "so yeah",
                        "you know", "in the description", "share this", "bell icon", "hope you guys"),
                List.of(
                        "your task", "you are given", "we are given", "given a", "given an", "problem statement",
                        "the problem", "they are asking", "is asking", "asked to", "we need to find",
                        "you need to find", "you have to find", "constraints", "for example", "input", "output"),
                3.0, 1.0, 1.0, 2.0, 2.0, 150);
    }
}
