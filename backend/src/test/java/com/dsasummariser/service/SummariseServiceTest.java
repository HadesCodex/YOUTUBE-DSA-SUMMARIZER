package com.dsasummariser.service;

import com.dsasummariser.exception.SummariseException;
import com.dsasummariser.model.Chunk;
import com.dsasummariser.model.ScoredChunk;
import com.dsasummariser.model.SummariseRequest;
import com.dsasummariser.model.SummariseResponse;
import com.dsasummariser.model.TokenUsage;
import com.dsasummariser.model.TranscriptStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The orchestration only: the transcript step and Gemini are both mocked, so nothing touches the network. */
@ExtendWith(OutputCaptureExtension.class)
class SummariseServiceTest {

    private static final String FULL_TRANSCRIPT = "full transcript with every chunk of the lecture";
    private static final TokenUsage USAGE = new TokenUsage(5200, 700, 90, 5990);
    private static final SummariseRequest REQUEST = new SummariseRequest("https://youtu.be/abcdefghijk", null);

    private final TranscriptService transcriptService = mock(TranscriptService.class);
    private final GeminiClient geminiClient = mock(GeminiClient.class);

    private static ScoredChunk scored(int index, String text) {
        return new ScoredChunk(new Chunk(index, text, 650), 5, 0, 0, 0);
    }

    /** 5 chunks of 650 words were created; the selector kept chunks 1 and 3. */
    private PreparedTranscript prepared() {
        SelectionResult selection = new SelectionResult(
                List.of(scored(1, "kept one"), scored(3, "kept three")),
                new TranscriptStats(3250, 5, 2, 1300));
        return new PreparedTranscript("abcdefghijk", FULL_TRANSCRIPT, 3250, "fetched", selection);
    }

    private SummariseService service(boolean filterEnabled) {
        when(transcriptService.prepare(REQUEST)).thenReturn(prepared());
        return new SummariseService(transcriptService, geminiClient, new SummaryParser(), filterEnabled);
    }

    private void geminiReturns(String modelJson) {
        when(geminiClient.summarise(anyString())).thenReturn(new GeminiResult(modelJson, USAGE, "gemini-3.8-flash", 1));
    }

    @Test
    void sendsOnlyTheFilteredTextWithGapMarkersWhenFilteringIsOn() {
        geminiReturns(SummaryFixtures.VALID_SUMMARY_JSON);

        service(true).summarise(REQUEST);

        verify(geminiClient).summarise("[...]\n\nkept one\n\n[...]\n\nkept three\n\n[...]");
    }

    @Test
    void buildsTheResponseWithStatsUsageAndSummaryAndNoTranscript() {
        geminiReturns(SummaryFixtures.VALID_SUMMARY_JSON);

        SummariseResponse response = service(true).summarise(REQUEST);

        assertEquals("abcdefghijk", response.videoId());
        assertEquals(3250, response.wordCount());
        assertEquals("fetched", response.source());
        assertEquals(new TranscriptStats(3250, 5, 2, 1300), response.stats());
        assertEquals(USAGE, response.usage());
        assertEquals("gemini-3.8-flash", response.modelUsed());
        assertEquals("Binary search", response.summary().topic());
        assertEquals(5, response.summary().revisionSummary().size());
    }

    @Test
    void sendsTheWholeTranscriptWhenFilteringIsSwitchedOff() {
        geminiReturns(SummaryFixtures.VALID_SUMMARY_JSON);

        SummariseResponse response = service(false).summarise(REQUEST);

        verify(geminiClient).summarise(FULL_TRANSCRIPT);
        // Stats must describe what was really sent: everything.
        assertEquals(new TranscriptStats(3250, 5, 5, 3250), response.stats());
    }

    @Test
    void logsTokenUsageNextToTheWordCounts(CapturedOutput output) {
        geminiReturns(SummaryFixtures.VALID_SUMMARY_JSON);

        service(true).summarise(REQUEST);

        String log = output.getAll();
        assertTrue(log.contains("video=abcdefghijk"));
        assertTrue(log.contains("modelUsed=gemini-3.8-flash"));
        assertTrue(log.contains("attempts=1"));
        assertTrue(log.contains("filter=true"));
        assertTrue(log.contains("totalWords=3250"));
        assertTrue(log.contains("wordsSent=1300"));
        assertTrue(log.contains("inputTokens=5200"));
        assertTrue(log.contains("outputTokens=700"));
        assertTrue(log.contains("thoughtTokens=90"));
        assertTrue(log.contains("totalTokens=5990"));
    }

    @Test
    void logsFilterOffRunsWithTheFullWordCount(CapturedOutput output) {
        geminiReturns(SummaryFixtures.VALID_SUMMARY_JSON);

        service(false).summarise(REQUEST);

        assertTrue(output.getAll().contains("filter=false"));
        assertTrue(output.getAll().contains("wordsSent=3250"));
    }

    @Test
    void notADsaLectureBecomes422ButTokensAreStillLogged(CapturedOutput output) {
        geminiReturns(SummaryFixtures.NOT_DSA_JSON);

        SummariseException ex = assertThrows(SummariseException.class, () -> service(true).summarise(REQUEST));

        assertEquals("NOT_A_DSA_LECTURE", ex.getCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertTrue(output.getAll().contains("inputTokens=5200"), "tokens were spent, so they must be logged");
    }

    @Test
    void malformedModelOutputIsRejected() {
        geminiReturns("not json");

        SummariseException ex = assertThrows(SummariseException.class, () -> service(true).summarise(REQUEST));

        assertEquals("INVALID_MODEL_RESPONSE", ex.getCode());
    }

    @Test
    void geminiErrorsPassThroughUnchanged() {
        SummariseException failure = new SummariseException(HttpStatus.TOO_MANY_REQUESTS, "GEMINI_RATE_LIMITED", "slow down");
        when(geminiClient.summarise(anyString())).thenThrow(failure);

        SummariseException ex = assertThrows(SummariseException.class, () -> service(true).summarise(REQUEST));

        assertEquals("GEMINI_RATE_LIMITED", ex.getCode());
    }
}
