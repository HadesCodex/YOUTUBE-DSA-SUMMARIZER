package com.dsasummariser.service;

import com.dsasummariser.exception.SummariseException;
import com.dsasummariser.model.LectureSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SummaryParserTest {

    private final SummaryParser parser = new SummaryParser();

    private SummariseException failureFor(String json) {
        return assertThrows(SummariseException.class, () -> parser.parse(json));
    }

    private void assertInvalidResponse(String json) {
        SummariseException ex = failureFor(json);
        assertEquals("INVALID_MODEL_RESPONSE", ex.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void parsesAValidSummary() {
        LectureSummary s = parser.parse(SummaryFixtures.VALID_SUMMARY_JSON);
        assertEquals("Binary search", s.topic());
        assertEquals(3, s.algorithmSteps().size());
        assertEquals("O(log n)", s.timeComplexity().value());
        assertEquals("O(1)", s.spaceComplexity().value());
        assertEquals(2, s.edgeCases().size());
        assertEquals(5, s.revisionSummary().size());
    }

    @Test
    void nonDsaLectureBecomesA422WithTheModelsReason() {
        SummariseException ex = failureFor(SummaryFixtures.NOT_DSA_JSON);
        assertEquals("NOT_A_DSA_LECTURE", ex.getCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("This is a cooking tutorial.", ex.getMessage());
    }

    @Test
    void nonDsaLectureWithoutAReasonGetsADefaultMessage() {
        SummariseException ex = failureFor("{\"isDsaLecture\": false}");
        assertEquals("NOT_A_DSA_LECTURE", ex.getCode());
        assertEquals("The text does not look like a DSA lecture.", ex.getMessage());
    }

    @Test
    void rejectsInvalidJson() {
        assertInvalidResponse("this is not json");
        assertInvalidResponse("{\"isDsaLecture\": true, ");
        assertInvalidResponse("");
    }

    @Test
    void rejectsWhenIsDsaLectureIsMissing() {
        assertInvalidResponse(SummaryFixtures.VALID_SUMMARY_JSON.replace("\"isDsaLecture\": true,", ""));
    }

    @Test
    void rejectsMissingOrBlankTopic() {
        assertInvalidResponse(SummaryFixtures.VALID_SUMMARY_JSON.replace("\"topic\": \"Binary search\",", ""));
        assertInvalidResponse(SummaryFixtures.VALID_SUMMARY_JSON.replace("\"Binary search\"", "\"  \""));
    }

    @Test
    void rejectsMissingComplexity() {
        assertInvalidResponse(SummaryFixtures.VALID_SUMMARY_JSON.replace(
                "\"timeComplexity\": { \"value\": \"O(log n)\", \"explanation\": \"The range halves each step.\" },", ""));
        assertInvalidResponse(SummaryFixtures.VALID_SUMMARY_JSON.replace("\"O(1)\"", "\"\""));
    }

    @Test
    void rejectsWrongNumberOfRevisionLines() {
        assertInvalidResponse(SummaryFixtures.VALID_SUMMARY_JSON.replace(", \"Line 5\"", ""));
        assertInvalidResponse(SummaryFixtures.VALID_SUMMARY_JSON.replace("\"Line 5\"", "\"Line 5\", \"Line 6\""));
    }

    @Test
    void rejectsMissingLists() {
        assertInvalidResponse(SummaryFixtures.VALID_SUMMARY_JSON.replace(
                "\"edgeCases\": [\"Empty array\", \"Target missing\"],", ""));
    }

    @Test
    void allowsEmptyListsAndNotCoveredText() {
        String json = SummaryFixtures.VALID_SUMMARY_JSON
                .replace("[\"Empty array\", \"Target missing\"]", "[]")
                .replace("\"Only a few variables.\"", "\"Not covered in the provided portion of the lecture\"");
        LectureSummary s = parser.parse(json);
        assertEquals(0, s.edgeCases().size());
        assertEquals("Not covered in the provided portion of the lecture", s.spaceComplexity().explanation());
    }

    @Test
    void ignoresExtraFieldsTheModelMightAdd() {
        String json = SummaryFixtures.VALID_SUMMARY_JSON.replace("\"topic\":", "\"extra\": 1, \"topic\":");
        assertEquals("Binary search", parser.parse(json).topic());
    }
}
