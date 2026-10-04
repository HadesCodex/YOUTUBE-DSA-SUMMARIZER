package com.dsasummariser.service;

import com.dsasummariser.exception.SummariseException;
import com.dsasummariser.model.Complexity;
import com.dsasummariser.model.LectureSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns the model's JSON text into a LectureSummary. The response schema already forces the shape,
 * but the docs say to validate values anyway, so we check what the schema cannot guarantee.
 * This is plain Jackson plus null checks: no hand-written JSON parser.
 */
@Component
public class SummaryParser {

    static final int REVISION_LINES = 5;

    /** Exactly the fields the schema asks for. isDsaLecture is Boolean so a missing value shows up as null. */
    private record RawSummary(
            Boolean isDsaLecture,
            String notDsaReason,
            String topic,
            String problemStatement,
            List<String> algorithmSteps,
            String pseudocode,
            Complexity timeComplexity,
            Complexity spaceComplexity,
            List<String> edgeCases,
            List<String> revisionSummary) {}

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public LectureSummary parse(String json) {
        RawSummary raw;
        try {
            raw = mapper.readValue(json, RawSummary.class);
        } catch (Exception e) {
            throw invalid();
        }
        if (raw == null || raw.isDsaLecture() == null) {
            throw invalid();
        }

        if (!raw.isDsaLecture()) {
            String reason = raw.notDsaReason() == null || raw.notDsaReason().isBlank()
                    ? "The text does not look like a DSA lecture."
                    : raw.notDsaReason().trim();
            throw new SummariseException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_A_DSA_LECTURE", reason);
        }

        boolean complete = notBlank(raw.topic()) && raw.problemStatement() != null && raw.pseudocode() != null
                && raw.algorithmSteps() != null && raw.edgeCases() != null
                && validComplexity(raw.timeComplexity()) && validComplexity(raw.spaceComplexity())
                && raw.revisionSummary() != null && raw.revisionSummary().size() == REVISION_LINES;
        if (!complete) {
            throw invalid();
        }
        return new LectureSummary(raw.topic().trim(), raw.problemStatement(), raw.algorithmSteps(), raw.pseudocode(),
                raw.timeComplexity(), raw.spaceComplexity(), raw.edgeCases(), raw.revisionSummary());
    }

    private boolean validComplexity(Complexity c) {
        return c != null && notBlank(c.value()) && c.explanation() != null;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private SummariseException invalid() {
        return new SummariseException(HttpStatus.BAD_GATEWAY, "INVALID_MODEL_RESPONSE",
                "Gemini returned a response we could not read. Try again.");
    }
}
