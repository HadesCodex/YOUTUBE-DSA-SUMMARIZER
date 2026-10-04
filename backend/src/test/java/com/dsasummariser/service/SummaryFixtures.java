package com.dsasummariser.service;

/** Canned model output and Gemini responses shared by the offline tests. */
final class SummaryFixtures {

    private SummaryFixtures() {}

    /** A valid summary exactly as the model would return it. */
    static final String VALID_SUMMARY_JSON = """
            {
              "isDsaLecture": true,
              "notDsaReason": "",
              "topic": "Binary search",
              "problemStatement": "Find a target in a sorted array.",
              "algorithmSteps": ["Set low and high.", "Compute mid.", "Shrink the range."],
              "pseudocode": "while low <= high:\\n  mid = (low + high) / 2",
              "timeComplexity": { "value": "O(log n)", "explanation": "The range halves each step." },
              "spaceComplexity": { "value": "O(1)", "explanation": "Only a few variables." },
              "edgeCases": ["Empty array", "Target missing"],
              "revisionSummary": ["Line 1", "Line 2", "Line 3", "Line 4", "Line 5"]
            }
            """;

    /** Same, but the model decided the text is not a DSA lecture. */
    static final String NOT_DSA_JSON = """
            {
              "isDsaLecture": false,
              "notDsaReason": "This is a cooking tutorial.",
              "topic": "", "problemStatement": "", "algorithmSteps": [], "pseudocode": "",
              "timeComplexity": { "value": "", "explanation": "" },
              "spaceComplexity": { "value": "", "explanation": "" },
              "edgeCases": [], "revisionSummary": []
            }
            """;

    /** Wraps model text in a Gemini Interactions response, with a thought step before the answer. */
    static String interactionResponse(String modelText) {
        String escaped = modelText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return """
                {
                  "status": "completed",
                  "steps": [
                    { "type": "thought", "content": [ { "type": "text", "text": "hidden reasoning" } ] },
                    { "type": "model_output", "content": [ { "type": "text", "text": "%s" } ] }
                  ],
                  "usage": {
                    "total_input_tokens": 1200, "total_output_tokens": 300, "total_thought_tokens": 50,
                    "total_cached_tokens": 0, "total_tokens": 1550
                  }
                }
                """.formatted(escaped);
    }
}
