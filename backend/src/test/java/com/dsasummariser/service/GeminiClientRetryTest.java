package com.dsasummariser.service;

import com.dsasummariser.exception.SummariseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Retry and fallback behaviour. No real API calls: a MockRestServiceServer answers each request in order,
 * and every expectation also checks which model the request asked for.
 */
@ExtendWith(OutputCaptureExtension.class)
class GeminiClientRetryTest {

    static final String KEY = "test-key-SECRET-123";
    static final String URL = "https://gemini.test/v1beta/interactions";
    static final String MAIN = "gemini-3.8-flash";
    static final String FALLBACK = "gemini-3.5-flash-lite";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GeminiClient client(boolean fallbackEnabled, String fallbackModel, long delayMillis) {
        return new GeminiClient(builder.build(), KEY, MAIN, "https://gemini.test/v1beta",
                fallbackModel, fallbackEnabled, delayMillis);
    }

    private GeminiClient client() {
        return client(true, FALLBACK, 0);
    }

    /** Expect the next request to be for this model (with the key in the header), and answer it. */
    private void expect(String model, ResponseCreator answer) {
        server.expect(requestTo(URL))
                .andExpect(header("x-goog-api-key", KEY))
                .andExpect(jsonPath("$.model").value(model))
                .andRespond(answer);
    }

    private static ResponseCreator ok() {
        return withSuccess(SummaryFixtures.interactionResponse("{}"), MediaType.APPLICATION_JSON);
    }

    private static ResponseCreator overloaded() {
        return withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"service_unavailable\",\"message\":\"model is currently experiencing high demand\"}}");
    }

    private static ResponseCreator readTimeout() {
        return withException(new SocketTimeoutException("Read timed out"));
    }

    private SummariseException failureOf(GeminiClient client) {
        return assertThrows(SummariseException.class, () -> client.summarise("t"));
    }

    // ----- the happy retry paths -----

    @Test
    void anOverloadedMainModelIsRetriedOnceAndTheRetrySucceeds() {
        expect(MAIN, overloaded());
        expect(MAIN, ok());

        GeminiResult result = client().summarise("t");

        assertEquals(MAIN, result.modelUsed());
        assertEquals(2, result.attempts());
        server.verify();
    }

    @Test
    void aTimeoutIsRetriedOnceAndTheRetrySucceeds() {
        expect(MAIN, readTimeout());
        expect(MAIN, ok());

        GeminiResult result = client().summarise("t");

        assertEquals(MAIN, result.modelUsed());
        assertEquals(2, result.attempts());
        server.verify();
    }

    @Test
    void aConnectTimeoutCountsAsATimeToo() {
        expect(MAIN, withException(new SocketTimeoutException("Connect timed out")));
        expect(MAIN, ok());

        assertEquals(2, client().summarise("t").attempts());
        server.verify();
    }

    @Test
    void twoOverloadsThenTheFallbackModelSucceeds(CapturedOutput output) {
        expect(MAIN, overloaded());
        expect(MAIN, overloaded());
        expect(FALLBACK, ok());

        GeminiResult result = client().summarise("t");

        assertEquals(FALLBACK, result.modelUsed());
        assertEquals(3, result.attempts());
        server.verify();
        // The retry and the fallback are both logged, and the key is not.
        assertTrue(output.getAll().contains("Retrying once"));
        assertTrue(output.getAll().contains("Falling back to model " + FALLBACK));
        assertTrue(output.getAll().contains("Fallback model " + FALLBACK + " produced the summary"));
        assertFalse(output.getAll().contains(KEY));
    }

    @Test
    void aMixOfOverloadAndTimeoutStillReachesTheFallback() {
        expect(MAIN, overloaded());
        expect(MAIN, readTimeout());
        expect(FALLBACK, ok());

        assertEquals(FALLBACK, client().summarise("t").modelUsed());
        server.verify();
    }

    // ----- giving up -----

    @Test
    void allThreeCallsFailingGivesTheExistingUnavailableError() {
        expect(MAIN, overloaded());
        expect(MAIN, overloaded());
        expect(FALLBACK, overloaded());

        SummariseException ex = failureOf(client());

        assertEquals("GEMINI_UNAVAILABLE", ex.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("Gemini is temporarily unavailable. Try again.", ex.getMessage());
        server.verify();   // exactly three calls, no fourth
    }

    @Test
    void allThreeTimingOutAlsoGivesTheUnavailableError() {
        expect(MAIN, readTimeout());
        expect(MAIN, readTimeout());
        expect(FALLBACK, readTimeout());

        assertEquals("GEMINI_UNAVAILABLE", failureOf(client()).getCode());
        server.verify();
    }

    @Test
    void aFallbackThatFailsForANonTransientReasonShowsThatError() {
        // For example the key has no access to the fallback model: hiding that behind "unavailable" would mislead.
        expect(MAIN, overloaded());
        expect(MAIN, overloaded());
        expect(FALLBACK, withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"model_not_found\",\"message\":\"no such model\"}}"));

        assertEquals("GEMINI_MODEL_NOT_FOUND", failureOf(client()).getCode());
        server.verify();
    }

    @Test
    void aNonTransientFailureOnTheRetryIsReturnedWithoutTryingTheFallback() {
        expect(MAIN, overloaded());
        expect(MAIN, withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"authentication\",\"message\":\"bad key\"}}"));

        assertEquals("GEMINI_AUTH_FAILED", failureOf(client()).getCode());
        server.verify();   // two calls only
    }

    // ----- failures that must NOT be retried -----

    static Stream<Arguments> neverRetried() {
        String json = "{\"error\":{\"code\":\"%s\",\"message\":\"x\"}}";
        return Stream.of(
                Arguments.of(401, json.formatted("authentication"), "GEMINI_AUTH_FAILED"),
                Arguments.of(403, json.formatted("permission_denied"), "GEMINI_AUTH_FAILED"),
                Arguments.of(400, json.formatted("invalid_request"), "GEMINI_BAD_REQUEST"),
                Arguments.of(400, json.formatted("safety"), "SUMMARY_BLOCKED"),
                Arguments.of(400, json.formatted("content_blocked"), "SUMMARY_BLOCKED"),
                Arguments.of(429, json.formatted("rate_limit_exceeded"), "GEMINI_RATE_LIMITED"),
                Arguments.of(429, json.formatted("quota_exceeded"), "GEMINI_QUOTA_EXCEEDED"),
                Arguments.of(402, json.formatted("payment_required"), "GEMINI_QUOTA_EXCEEDED"),
                Arguments.of(404, json.formatted("model_not_found"), "GEMINI_MODEL_NOT_FOUND"),
                Arguments.of(500, json.formatted("api_error"), "GEMINI_UNAVAILABLE"),
                Arguments.of(504, json.formatted("deadline_exceeded"), "GEMINI_TIMEOUT"));
    }

    @ParameterizedTest(name = "HTTP {0} ({2}) is not retried")
    @MethodSource("neverRetried")
    void keyRequestRateAndOtherErrorsAreNotRetried(int httpStatus, String body, String expectedCode) {
        expect(MAIN, withStatus(HttpStatus.valueOf(httpStatus)).contentType(MediaType.APPLICATION_JSON).body(body));

        assertEquals(expectedCode, failureOf(client()).getCode());
        server.verify();   // exactly one call: no retry, no fallback
    }

    @Test
    void malformedOrBlockedResponsesWithHttp200AreNotRetried() {
        expect(MAIN, withSuccess("{\"status\":\"completed\",\"steps\":[]}", MediaType.APPLICATION_JSON));
        assertEquals("INVALID_MODEL_RESPONSE", failureOf(client()).getCode());
        server.verify();
        server.reset();

        expect(MAIN, withSuccess("{\"status\":\"incomplete\",\"steps\":[]}", MediaType.APPLICATION_JSON));
        assertEquals("SUMMARY_TRUNCATED", failureOf(client()).getCode());
        server.verify();
        server.reset();

        expect(MAIN, withSuccess("{\"error\":{\"code\":\"safety\",\"message\":\"blocked\"}}", MediaType.APPLICATION_JSON));
        assertEquals("SUMMARY_BLOCKED", failureOf(client()).getCode());
        server.verify();
    }

    @Test
    void aConnectionRefusedIsNotRetried() {
        // Not a timeout: the network itself is down, so a retry or another model would not help.
        expect(MAIN, withException(new ConnectException("Connection refused")));

        assertEquals("GEMINI_UNAVAILABLE", failureOf(client()).getCode());
        server.verify();
    }

    @Test
    void aMissingKeyMakesNoCallAtAll() {
        GeminiClient noKey = new GeminiClient(builder.build(), "", MAIN, "https://gemini.test/v1beta", FALLBACK, true, 0);

        assertEquals("SUMMARISER_NOT_CONFIGURED", failureOf(noKey).getCode());
        server.verify();   // no request was expected, none was made
    }

    // ----- the fallback switch -----

    @Test
    void withTheFallbackDisabledTheMainModelIsTriedTwiceAndThenTheErrorSurfaces() {
        expect(MAIN, overloaded());
        expect(MAIN, overloaded());

        SummariseException ex = failureOf(client(false, FALLBACK, 0));

        assertEquals("GEMINI_UNAVAILABLE", ex.getCode());
        server.verify();   // two calls, never the fallback model
    }

    @Test
    void withTheFallbackDisabledTimeoutsKeepTheirOwnError() {
        expect(MAIN, readTimeout());
        expect(MAIN, readTimeout());

        SummariseException ex = failureOf(client(false, FALLBACK, 0));

        assertEquals("GEMINI_TIMEOUT", ex.getCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
        server.verify();
    }

    @Test
    void withTheFallbackDisabledARetrySuccessIsStillReturned() {
        expect(MAIN, overloaded());
        expect(MAIN, ok());

        assertEquals(MAIN, client(false, FALLBACK, 0).summarise("t").modelUsed());
        server.verify();
    }

    @Test
    void aFallbackModelEqualToTheMainModelIsSkipped() {
        expect(MAIN, overloaded());
        expect(MAIN, overloaded());

        assertEquals("GEMINI_UNAVAILABLE", failureOf(client(true, MAIN, 0)).getCode());
        server.verify();   // two calls: calling the same model a third time would be pointless
    }

    @Test
    void aBlankFallbackModelIsSkipped() {
        expect(MAIN, overloaded());
        expect(MAIN, overloaded());

        assertEquals("GEMINI_UNAVAILABLE", failureOf(client(true, "  ", 0)).getCode());
        server.verify();
    }

    // ----- timing and logging -----

    @Test
    void theRetryWaitsForTheConfiguredDelay() {
        expect(MAIN, overloaded());
        expect(MAIN, ok());
        GeminiClient slow = client(true, FALLBACK, 150);

        long start = System.nanoTime();
        slow.summarise("t");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMillis >= 140, "expected a pause of about 150 ms but only " + elapsedMillis + " ms passed");
    }

    @Test
    void aSuccessfulFirstCallDoesNotWaitOrLogARetry(CapturedOutput output) {
        expect(MAIN, ok());

        GeminiResult result = client(true, FALLBACK, 5000).summarise("t");   // a 5 s delay would be obvious

        assertEquals(1, result.attempts());
        assertFalse(output.getAll().contains("Retrying"));
        assertFalse(output.getAll().contains("Falling back"));
    }

    @Test
    void theKeyIsNeverLoggedEvenWhenGeminiEchoesItDuringRetries(CapturedOutput output) {
        ResponseCreator echo = withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"service_unavailable\",\"message\":\"key " + KEY + " overloaded\"}}");
        expect(MAIN, echo);
        expect(MAIN, echo);
        expect(FALLBACK, echo);

        failureOf(client());

        assertTrue(output.getAll().contains("Gemini error"));
        assertFalse(output.getAll().contains(KEY));
    }
}
