package com.dsasummariser.controller;

import com.dsasummariser.exception.GlobalExceptionHandler;
import com.dsasummariser.exception.InvalidUrlException;
import com.dsasummariser.exception.SummariseException;
import com.dsasummariser.exception.TranscriptUnavailableException;
import com.dsasummariser.model.Complexity;
import com.dsasummariser.model.LectureSummary;
import com.dsasummariser.model.SummariseResponse;
import com.dsasummariser.model.TokenUsage;
import com.dsasummariser.model.TranscriptStats;
import com.dsasummariser.service.SummariseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP layer only: the real controller and exception handler, with the service mocked. */
class SummariseControllerTest {

    private final SummariseService service = mock(SummariseService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SummariseController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private org.springframework.test.web.servlet.ResultActions call(String body) throws Exception {
        return mvc.perform(post("/api/summarise").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static final String VALID_BODY = "{\"url\":\"https://youtu.be/abcdefghijk\"}";

    @Test
    void successResponseHasSummaryUsageAndStatsButNoTranscript() throws Exception {
        LectureSummary summary = new LectureSummary("Binary search", "Find x.", List.of("step"), "code",
                new Complexity("O(log n)", "halves"), new Complexity("O(1)", "constant"),
                List.of("empty"), List.of("1", "2", "3", "4", "5"));
        when(service.summarise(any())).thenReturn(new SummariseResponse("abcdefghijk", 6325, "fetched", "gemini-3.5-flash-lite",
                new TranscriptStats(6325, 10, 6, 3900), new TokenUsage(5200, 700, 90, 5990), summary));

        call(VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value("abcdefghijk"))
                .andExpect(jsonPath("$.wordCount").value(6325))
                .andExpect(jsonPath("$.source").value("fetched"))
                .andExpect(jsonPath("$.stats.totalWords").value(6325))
                .andExpect(jsonPath("$.stats.chunksCreated").value(10))
                .andExpect(jsonPath("$.stats.chunksKept").value(6))
                .andExpect(jsonPath("$.stats.wordsSent").value(3900))
                .andExpect(jsonPath("$.modelUsed").value("gemini-3.5-flash-lite"))
                .andExpect(jsonPath("$.usage.inputTokens").value(5200))
                .andExpect(jsonPath("$.usage.outputTokens").value(700))
                .andExpect(jsonPath("$.usage.thoughtTokens").value(90))
                .andExpect(jsonPath("$.usage.totalTokens").value(5990))
                .andExpect(jsonPath("$.summary.topic").value("Binary search"))
                .andExpect(jsonPath("$.summary.timeComplexity.value").value("O(log n)"))
                .andExpect(jsonPath("$.summary.revisionSummary.length()").value(5))
                .andExpect(jsonPath("$.transcript").doesNotExist());
    }

    @Test
    void notADsaLectureIs422WithItsOwnCode() throws Exception {
        doThrow(new SummariseException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_A_DSA_LECTURE", "This is a cooking tutorial.")).when(service).summarise(any());

        call(VALID_BODY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("NOT_A_DSA_LECTURE"))
                .andExpect(jsonPath("$.message").value("This is a cooking tutorial."));
    }

    @Test
    void summariseErrorsKeepTheirOwnStatusAndCode() throws Exception {
        doThrow(new SummariseException(HttpStatus.SERVICE_UNAVAILABLE, "SUMMARISER_NOT_CONFIGURED", "no key")).when(service).summarise(any());
        call(VALID_BODY).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SUMMARISER_NOT_CONFIGURED"));

        doThrow(new SummariseException(HttpStatus.TOO_MANY_REQUESTS, "GEMINI_RATE_LIMITED", "slow down")).when(service).summarise(any());
        call(VALID_BODY).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("GEMINI_RATE_LIMITED"));

        doThrow(new SummariseException(HttpStatus.GATEWAY_TIMEOUT, "GEMINI_TIMEOUT", "too slow")).when(service).summarise(any());
        call(VALID_BODY).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("GEMINI_TIMEOUT"));
    }

    @Test
    void existingErrorsStillMapAsBefore() throws Exception {
        doThrow(new TranscriptUnavailableException("no captions")).when(service).summarise(any());
        call(VALID_BODY).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("TRANSCRIPT_UNAVAILABLE"));

        doThrow(new InvalidUrlException("bad url")).when(service).summarise(any());
        call(VALID_BODY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_URL"));
    }

    @Test
    void everyTranscriptSubCodeIsReturnedAsItsOwnErrorCodeWithStatus422() throws Exception {
        for (String code : new String[]{"NO_ENGLISH_CAPTIONS", "NO_CAPTIONS", "VIDEO_UNAVAILABLE",
                "YOUTUBE_REFUSED", "YOUTUBE_UNREACHABLE", "TRANSCRIPT_UNAVAILABLE"}) {
            doThrow(new TranscriptUnavailableException(code, "message for " + code)).when(service).summarise(any());
            call(VALID_BODY)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value(code))
                    .andExpect(jsonPath("$.message").value("message for " + code));
        }
    }

    @Test
    void blankUrlIsAValidationError() throws Exception {
        call("{\"url\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
