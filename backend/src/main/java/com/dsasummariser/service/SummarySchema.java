package com.dsasummariser.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The JSON Schema we give Gemini's response_format, so it must reply with exactly this shape.
 * Every field is required so the model cannot silently leave one out. Gemini still can return
 * values that break our rules (for example 4 revision lines), so SummaryParser re-checks them.
 */
final class SummarySchema {

    private SummarySchema() {}

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "isDsaLecture": { "type": "boolean", "description": "true only if the text is a DSA or programming lecture." },
                "notDsaReason": { "type": "string", "description": "Why it is not a DSA lecture; empty string when it is." },
                "topic": { "type": "string", "description": "Short title of the topic, for example 'Binary search'." },
                "problemStatement": { "type": "string", "description": "The problem the lecture solves, 1 to 3 sentences." },
                "algorithmSteps": { "type": "array", "items": { "type": "string" }, "description": "Steps of the approach, in order." },
                "pseudocode": { "type": "string", "description": "Language-neutral pseudocode of the final approach." },
                "timeComplexity": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string", "description": "For example O(log n)." },
                    "explanation": { "type": "string", "description": "One sentence." }
                  },
                  "required": ["value", "explanation"]
                },
                "spaceComplexity": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string", "description": "For example O(1)." },
                    "explanation": { "type": "string", "description": "One sentence." }
                  },
                  "required": ["value", "explanation"]
                },
                "edgeCases": { "type": "array", "items": { "type": "string" }, "description": "Edge cases the lecture mentions." },
                "revisionSummary": {
                  "type": "array",
                  "items": { "type": "string" },
                  "minItems": 5,
                  "maxItems": 5,
                  "description": "Exactly 5 short revision lines."
                }
              },
              "required": ["isDsaLecture", "notDsaReason", "topic", "problemStatement", "algorithmSteps", "pseudocode",
                           "timeComplexity", "spaceComplexity", "edgeCases", "revisionSummary"]
            }
            """;

    /** Parsed once; it never changes. */
    static final JsonNode SCHEMA = parse();

    private static JsonNode parse() {
        try {
            return new ObjectMapper().readTree(SCHEMA_JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Built-in summary schema is not valid JSON", e);
        }
    }
}
