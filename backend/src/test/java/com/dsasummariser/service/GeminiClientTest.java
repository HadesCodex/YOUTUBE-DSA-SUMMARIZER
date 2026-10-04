package com.dsasummariser.service;

import com.dsasummariser.exception.SummariseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No real API calls: every request goes to a MockRestServiceServer. */
@ExtendWith(OutputCaptureExtension.class)
class GeminiClientTest {

    static final String KEY = "test-key-SECRET-123";
    static final String URL = "https://gemini.test/v1beta/interactions";
    static final String MODEL = "gemini-3.8-flash";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GeminiClient clientWithKey(String key) {
        // Fallback off and no delay: these tests cover single calls. Retry/fallback is in GeminiClientRetryTest.
        return new GeminiClient(builder.build(), key, MODEL, "https://gemini.test/v1beta", "gemini-3.5-flash-lite", false, 0);
    }

    private static String errorBody(String code, String message) {
        return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}";
    }

    // ----- success path and what we send -----

    @Test
    void extractsTheModelTextAndUsageIgnoringThoughtSteps() {
        server.expect(requestTo(URL)).andRespond(
                withSuccess(SummaryFixtures.interactionResponse("{\"hello\":\"world\"}"), MediaType.APPLICATION_JSON));

        GeminiResult result = clientWithKey(KEY).summarise("some transcript");

        assertEquals("{\"hello\":\"world\"}", result.text());
        assertEquals(MODEL, result.modelUsed());
        assertEquals(1, result.attempts());
        assertEquals(1200, result.usage().inputTokens());
        assertEquals(300, result.usage().outputTokens());
        assertEquals(50, result.usage().thoughtTokens());
        assertEquals(1550, result.usage().totalTokens());
        server.verify();
    }

    @Test
    void sendsKeyInHeaderOnlyAndTheExpectedBody() {
        server.expect(requestTo(URL))   // exact URL: no ?key=... query string
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", KEY))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.input").value(containsString("<transcript>")))
                .andExpect(jsonPath("$.input").value(containsString("my transcript text")))
                .andExpect(jsonPath("$.system_instruction").value(containsString("auto-generated")))
                .andExpect(jsonPath("$.response_format.type").value("text"))
                .andExpect(jsonPath("$.response_format.mime_type").value("application/json"))
                .andExpect(jsonPath("$.response_format.schema.properties.revisionSummary.minItems").value(5))
                .andExpect(jsonPath("$.response_format.schema.properties.revisionSummary.maxItems").value(5))
                .andExpect(jsonPath("$.response_format.schema.required").isArray())
                .andExpect(jsonPath("$.generation_config.max_output_tokens").value(4096))
                .andExpect(jsonPath("$.generation_config.thinking_level").value("low"))
                .andRespond(withSuccess(SummaryFixtures.interactionResponse("{}"), MediaType.APPLICATION_JSON));

        clientWithKey(KEY).summarise("my transcript text");
        server.verify();
    }

    @Test
    void neverSendsTemperatureTopPOrTopK() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andExpect(jsonPath("$.top_p").doesNotExist())
                .andExpect(jsonPath("$.top_k").doesNotExist())
                .andExpect(jsonPath("$.generation_config.temperature").doesNotExist())
                .andExpect(jsonPath("$.generation_config.top_p").doesNotExist())
                .andExpect(jsonPath("$.generation_config.top_k").doesNotExist())
                .andRespond(withSuccess(SummaryFixtures.interactionResponse("{}"), MediaType.APPLICATION_JSON));

        clientWithKey(KEY).summarise("t");
        server.verify();
    }

    @Test
    void aMissingStatusIsToleratedWhenThereIsAnswerText() {
        String body = "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"{}\"}]}]}";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        GeminiResult result = clientWithKey(KEY).summarise("t");

        assertEquals("{}", result.text());
        assertEquals(0, result.usage().inputTokens());   // missing usage counts as zero, not a crash
    }

    // ----- configuration errors -----

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        for (String key : new String[]{"", "   ", null}) {
            SummariseException ex = assertThrows(SummariseException.class, () -> clientWithKey(key).summarise("t"));
            assertEquals("SUMMARISER_NOT_CONFIGURED", ex.getCode());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
            assertTrue(ex.getMessage().contains("GEMINI_API_KEY"));
        }
        server.verify();   // no request was expected, and none was made
    }

    // ----- HTTP errors from Gemini -----

    static Stream<Arguments> httpErrors() {
        return Stream.of(
                Arguments.of(401, errorBody("authentication", "bad key"), HttpStatus.BAD_GATEWAY, "GEMINI_AUTH_FAILED"),
                Arguments.of(403, errorBody("permission_denied", "no access"), HttpStatus.BAD_GATEWAY, "GEMINI_AUTH_FAILED"),
                Arguments.of(400, errorBody("invalid_request", "API key not valid. Please pass a valid API key."),
                        HttpStatus.BAD_GATEWAY, "GEMINI_AUTH_FAILED"),
                Arguments.of(429, errorBody("rate_limit_exceeded", "slow down"), HttpStatus.TOO_MANY_REQUESTS, "GEMINI_RATE_LIMITED"),
                Arguments.of(429, errorBody("too_many_requests", "slow down"), HttpStatus.TOO_MANY_REQUESTS, "GEMINI_RATE_LIMITED"),
                Arguments.of(429, "not json at all", HttpStatus.TOO_MANY_REQUESTS, "GEMINI_RATE_LIMITED"),
                Arguments.of(429, errorBody("quota_exceeded", "daily limit"), HttpStatus.TOO_MANY_REQUESTS, "GEMINI_QUOTA_EXCEEDED"),
                Arguments.of(402, errorBody("payment_required", "no credit"), HttpStatus.BAD_GATEWAY, "GEMINI_QUOTA_EXCEEDED"),
                Arguments.of(404, errorBody("model_not_found", "no such model"), HttpStatus.BAD_GATEWAY, "GEMINI_MODEL_NOT_FOUND"),
                Arguments.of(500, errorBody("api_error", "oops"), HttpStatus.BAD_GATEWAY, "GEMINI_UNAVAILABLE"),
                Arguments.of(503, errorBody("service_unavailable", "overloaded"), HttpStatus.BAD_GATEWAY, "GEMINI_UNAVAILABLE"),
                Arguments.of(503, "<html>Service Unavailable</html>", HttpStatus.BAD_GATEWAY, "GEMINI_UNAVAILABLE"),
                Arguments.of(504, errorBody("deadline_exceeded", "too slow"), HttpStatus.GATEWAY_TIMEOUT, "GEMINI_TIMEOUT"),
                Arguments.of(400, errorBody("invalid_request", "bad parameter"), HttpStatus.BAD_GATEWAY, "GEMINI_BAD_REQUEST"),
                Arguments.of(400, errorBody("safety", "blocked"), HttpStatus.UNPROCESSABLE_ENTITY, "SUMMARY_BLOCKED"),
                Arguments.of(400, errorBody("content_blocked", "blocked"), HttpStatus.UNPROCESSABLE_ENTITY, "SUMMARY_BLOCKED"),
                Arguments.of(400, errorBody("recitation", "blocked"), HttpStatus.UNPROCESSABLE_ENTITY, "SUMMARY_BLOCKED"));
    }

    @ParameterizedTest(name = "HTTP {0} -> {3}")
    @MethodSource("httpErrors")
    void mapsHttpErrorsToOurOwnCodes(int httpStatus, String body, HttpStatus expectedStatus, String expectedCode) {
        // HTTP 503 is the one error that is retried once (see GeminiClientRetryTest), so it is answered twice.
        int requests = httpStatus == 503 ? 2 : 1;
        for (int i = 0; i < requests; i++) {
            server.expect(requestTo(URL)).andRespond(
                    withStatus(HttpStatus.valueOf(httpStatus)).contentType(MediaType.APPLICATION_JSON).body(body));
        }

        SummariseException ex = assertThrows(SummariseException.class, () -> clientWithKey(KEY).summarise("t"));
        server.verify();   // exactly the expected number of requests was made

        assertEquals(expectedCode, ex.getCode());
        assertEquals(expectedStatus, ex.getStatus());
        assertFalse(ex.getMessage().contains(KEY));
    }

    // ----- transport errors -----

    @Test
    void timeoutBecomesGeminiTimeout() {
        // A timeout is retried once, so it is answered twice; with the fallback off the error then surfaces.
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        SummariseException ex = assertThrows(SummariseException.class, () -> clientWithKey(KEY).summarise("t"));

        assertEquals("GEMINI_TIMEOUT", ex.getCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
    }

    @Test
    void connectionFailureBecomesGeminiUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("Connection refused")));

        SummariseException ex = assertThrows(SummariseException.class, () -> clientWithKey(KEY).summarise("t"));

        assertEquals("GEMINI_UNAVAILABLE", ex.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    // ----- malformed, truncated or blocked responses that arrive with HTTP 200 -----

    private SummariseException failureForOkBody(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return assertThrows(SummariseException.class, () -> clientWithKey(KEY).summarise("t"));
    }

    @Test
    void errorObjectInsideAn200BodyIsMappedLikeAnHttpError() {
        SummariseException ex = failureForOkBody(errorBody("safety", "blocked"));
        assertEquals("SUMMARY_BLOCKED", ex.getCode());
    }

    @Test
    void nonCompletedStatusIsAnInvalidResponse() {
        SummariseException ex = failureForOkBody("{\"status\":\"failed\",\"steps\":[]}");
        assertEquals("INVALID_MODEL_RESPONSE", ex.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void incompleteStatusIsReportedAsTruncated() {
        SummariseException ex = failureForOkBody("{\"status\":\"incomplete\",\"steps\":[]}");
        assertEquals("SUMMARY_TRUNCATED", ex.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void responseWithoutAModelOutputTextIsInvalid() {
        assertEquals("INVALID_MODEL_RESPONSE", failureForOkBody("{\"status\":\"completed\",\"steps\":[]}").getCode());
    }

    @Test
    void onlyThoughtStepsIsInvalid() {
        String body = "{\"status\":\"completed\",\"steps\":[{\"type\":\"thought\",\"content\":[{\"type\":\"text\",\"text\":\"x\"}]}]}";
        assertEquals("INVALID_MODEL_RESPONSE", failureForOkBody(body).getCode());
    }

    @Test
    void emptyBodyIsInvalid() {
        assertEquals("INVALID_MODEL_RESPONSE", failureForOkBody("null").getCode());
    }

    // ----- the key never leaks -----

    @Test
    void keyIsNeverLoggedEvenIfGeminiEchoesItBack(CapturedOutput output) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body(errorBody("authentication", "API key " + KEY + " is not valid")));

        SummariseException ex = assertThrows(SummariseException.class, () -> clientWithKey(KEY).summarise("t"));

        assertFalse(ex.getMessage().contains(KEY));
        assertTrue(output.getAll().contains("Gemini error"), "the error should be logged");
        assertFalse(output.getAll().contains(KEY), "the key must not appear in the log");
        assertTrue(output.getAll().contains("***"));
    }

    @Test
    void startupWarningForMissingKeyDoesNotContainAnyKey(CapturedOutput output) {
        clientWithKey("");
        assertTrue(output.getAll().contains("No Gemini API key configured"));
    }
}
