package com.dsasummariser.model;

import java.util.List;

/** The structured summary the frontend shows. revisionSummary always has exactly 5 lines. */
public record LectureSummary(
        String topic,
        String problemStatement,
        List<String> algorithmSteps,
        String pseudocode,
        Complexity timeComplexity,
        Complexity spaceComplexity,
        List<String> edgeCases,
        List<String> revisionSummary
) {}
