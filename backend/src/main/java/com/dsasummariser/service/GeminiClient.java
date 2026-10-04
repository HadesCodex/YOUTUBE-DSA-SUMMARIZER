package com.dsasummariser.service;

import com.dsasummariser.exception.SummariseException;
import com.dsasummariser.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The only class that talks to Gemini. It uses the Interactions API:
 *   POST {base-url}/interactions   with the key in the x-goog-api-key header.
 * The response holds the model's text in steps[type=model_output].content[type=text].text and the
 * token counts in usage.*.
 *
 * Retry and fallback (only for transient failures: HTTP 503, or a connect/read timeout):
 *   1. call the main model;
 *   2. if that fails transiently, wait retryDelayMillis and call the main model once more;
 *   3. if that also fails transiently and the fallback is enabled, call the fallback model once;
 *   4. if that fails too, give up with GEMINI_UNAVAILABLE.
 * Anything else (bad key, bad request, rate limit, blocked or malformed output) is returned at once:
 * repeating it would not help.
 *
 * Deliberately NOT sent: temperature, top_p, top_k (rejected by 3.7+ models), so the model defaults apply.
 * store=false so Google does not keep our transcripts. The key is only ever put in the header: never in a
 * URL, never in a log line, never in an error message.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    static final int MAX_OUTPUT_TOKENS = 4096;
    /** Thinking tokens are billed like output tokens, so keep thinking low for a summarisation task. */
    static final String THINKING_LEVEL = "low";

    /** Error codes Gemini uses when policy or safety blocks the output. */
    private static final Set<String> BLOCKED_CODES = Set.of(
            "safety", "recitation", "language", "prohibited_content", "spii", "blocklist", "content_blocked");

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String fallbackModel;
    private final boolean fallbackEnabled;
    private final long retryDelayMillis;

    @Autowired
    public GeminiClient(RestClient.Builder builder,
                        @Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.model}") String model,
                        @Value("${gemini.base-url}") String baseUrl,
                        @Value("${gemini.timeout.connect-seconds}") int connectSeconds,
                        @Value("${gemini.timeout.read-seconds}") int readSeconds,
                        @Value("${gemini.fallback-model:gemini-3.5-flash-lite}") String fallbackModel,
                        @Value("${gemini.fallback-enabled:true}") boolean fallbackEnabled,
                        @Value("${gemini.retry-delay-millis:2000}") long retryDelayMillis) {
        this(builder.requestFactory(timeouts(connectSeconds, readSeconds)).build(),
                apiKey, model, baseUrl, fallbackModel, fallbackEnabled, retryDelayMillis);
    }

    // Package-private so tests can pass a RestClient wired to a mock server.
    GeminiClient(RestClient restClient, String apiKey, String model, String baseUrl,
                 String fallbackModel, boolean fallbackEnabled, long retryDelayMillis) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
        this.fallbackModel = fallbackModel == null ? "" : fallbackModel.trim();
        this.fallbackEnabled = fallbackEnabled;
        this.retryDelayMillis = retryDelayMillis;
        if (this.apiKey.isEmpty()) {
            log.warn("No Gemini API key configured: set gemini.api.key (profile 'local') or the GEMINI_API_KEY "
                    + "environment variable. Summarising will fail until then.");
        }
    }

    private static SimpleClientHttpRequestFactory timeouts(int connectSeconds, int readSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));   // LLM calls are slow, so this is longer than for captions
        return factory;
    }

    public GeminiResult summarise(String transcriptText) {
        if (apiKey.isEmpty()) {
            throw new SummariseException(HttpStatus.SERVICE_UNAVAILABLE, "SUMMARISER_NOT_CONFIGURED",
                    "The server has no Gemini API key configured. Set gemini.api.key (profile 'local') "
                            + "or the GEMINI_API_KEY environment variable.");
        }

        // Attempt 1: the main model.
        SummariseException failure;
        try {
            return callGemini(model, transcriptText, 1);
        } catch (SummariseException e) {
            if (!e.isTransientFailure()) {
                throw e;
            }
            failure = e;
        }

        // Attempt 2: the main model again, after a short pause.
        log.warn("Gemini call to {} failed transiently ({}). Retrying once with the same model in {} ms.",
                model, failure.getCode(), retryDelayMillis);
        pause();
        try {
            return callGemini(model, transcriptText, 2);
        } catch (SummariseException e) {
            if (!e.isTransientFailure()) {
                throw e;
            }
            failure = e;
        }

        // Attempt 3: the fallback model, if allowed and different from the main one.
        if (!fallbackEnabled || fallbackModel.isEmpty() || fallbackModel.equals(model)) {
            throw failure;
        }
        log.warn("Retry with {} failed transiently ({}). Falling back to model {}.", model, failure.getCode(), fallbackModel);
        try {
            GeminiResult result = callGemini(fallbackModel, transcriptText, 3);
            log.warn("Fallback model {} produced the summary (main model {} was unavailable).", fallbackModel, model);
            return result;
        } catch (SummariseException e) {
            if (e.isTransientFailure()) {
                log.warn("Fallback model {} also failed transiently ({}). Giving up.", fallbackModel, e.getCode());
                throw new SummariseException(HttpStatus.BAD_GATEWAY, "GEMINI_UNAVAILABLE",
                        "Gemini is temporarily unavailable. Try again.");
            }
            throw e;   // for example the key has no access to the fallback model: that is worth seeing
        }
    }

    private void pause() {
        if (retryDelayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** One HTTP call to one model. Returns the result, or throws a SummariseException. */
    private GeminiResult callGemini(String modelName, String transcriptText, int attemptNumber) {
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/interactions")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(modelName, transcriptText))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw fromHttpError(e);
        } catch (RestClientException e) {
            throw fromTransportError(e);
        }
        return readResponse(response, modelName, attemptNumber);
    }

    private Map<String, Object> buildRequest(String modelName, String transcriptText) {
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");
        responseFormat.put("schema", SummarySchema.SCHEMA);

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        generationConfig.put("thinking_level", THINKING_LEVEL);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("system_instruction", PromptBuilder.SYSTEM_INSTRUCTION);
        body.put("input", PromptBuilder.buildInput(transcriptText));
        body.put("response_format", responseFormat);
        body.put("generation_config", generationConfig);
        body.put("store", false);
        return body;
    }

    // ----- reading a successful response -----

    // Package-private for tests.
    GeminiResult readResponse(JsonNode response, String modelName, int attemptNumber) {
        if (response == null || response.isNull()) {
            log.warn("Gemini returned an empty body");
            throw invalidResponse();
        }
        if (response.hasNonNull("error")) {
            JsonNode error = response.get("error");
            throw classify(0, error.path("code").asText(""), error.path("message").asText(""));
        }

        // A missing status is tolerated; a present status other than "completed" is a failure.
        String status = response.path("status").asText("");
        if (!status.isEmpty() && !status.equals("completed")) {
            log.warn("Gemini interaction ended with status '{}'", status);
            String lower = status.toLowerCase();
            if (lower.contains("incomplete") || lower.contains("max") || lower.contains("length")) {
                throw new SummariseException(HttpStatus.BAD_GATEWAY, "SUMMARY_TRUNCATED",
                        "The summary was cut off before it finished. Try again.");
            }
            throw invalidResponse();
        }

        String text = extractText(response);
        if (text.isBlank()) {
            log.warn("Gemini response had no model_output text step");
            throw invalidResponse();
        }
        return new GeminiResult(text, extractUsage(response), modelName, attemptNumber);
    }

    private String extractText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) {
                continue;   // skip thought steps and anything else
            }
            for (JsonNode part : step.path("content")) {
                if ("text".equals(part.path("type").asText())) {
                    text.append(part.path("text").asText(""));
                }
            }
        }
        return text.toString();
    }

    private TokenUsage extractUsage(JsonNode response) {
        JsonNode usage = response.path("usage");
        return new TokenUsage(
                usage.path("total_input_tokens").asInt(0),
                usage.path("total_output_tokens").asInt(0),
                usage.path("total_thought_tokens").asInt(0),
                usage.path("total_tokens").asInt(0));
    }

    private SummariseException invalidResponse() {
        return new SummariseException(HttpStatus.BAD_GATEWAY, "INVALID_MODEL_RESPONSE",
                "Gemini returned a response we could not read. Try again.");
    }

    // ----- turning failures into our own errors -----

    private SummariseException fromHttpError(RestClientResponseException e) {
        String code = "";
        String message = "";
        try {
            JsonNode error = mapper.readTree(e.getResponseBodyAsString()).path("error");
            code = error.path("code").asText("");
            message = error.path("message").asText("");
        } catch (Exception ignored) {
            // The body was not JSON; the HTTP status alone decides.
        }
        return classify(e.getStatusCode().value(), code, message);
    }

    private SummariseException fromTransportError(RestClientException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                log.warn("Gemini call timed out: {}", abbreviate(String.valueOf(t.getMessage())));
                return new SummariseException(HttpStatus.GATEWAY_TIMEOUT, "GEMINI_TIMEOUT",
                        "Gemini did not respond in time. Try again.", true);
            }
        }
        log.warn("Could not reach Gemini: {}", abbreviate(String.valueOf(e.getMessage())));
        return new SummariseException(HttpStatus.BAD_GATEWAY, "GEMINI_UNAVAILABLE",
                "Could not reach Gemini. Check the server's internet connection and try again.");
    }

    /**
     * Maps an upstream failure (HTTP status plus Gemini's snake_case error code) to our error.
     * The upstream message is only logged. Our messages are fixed so they can never contain the key.
     * Only HTTP 503 is marked transient (worth a retry or the fallback model).
     */
    SummariseException classify(int status, String upstreamCode, String upstreamMessage) {
        log.warn("Gemini error: http={} code={} message={}", status, upstreamCode, abbreviate(upstreamMessage));

        if (BLOCKED_CODES.contains(upstreamCode)) {
            return new SummariseException(HttpStatus.UNPROCESSABLE_ENTITY, "SUMMARY_BLOCKED",
                    "Gemini declined to summarise this content.");
        }
        boolean keyProblem = status == 401 || status == 403
                || upstreamCode.equals("authentication") || upstreamCode.equals("permission_denied")
                || (upstreamCode.equals("invalid_request") && upstreamMessage.toLowerCase().contains("api key"));
        if (keyProblem) {
            return new SummariseException(HttpStatus.BAD_GATEWAY, "GEMINI_AUTH_FAILED",
                    "The server's Gemini API key was rejected (invalid, expired, revoked or without permission).");
        }
        if (status == 429 || upstreamCode.equals("rate_limit_exceeded") || upstreamCode.equals("too_many_requests")
                || upstreamCode.equals("quota_exceeded")) {
            if (upstreamCode.equals("quota_exceeded")) {
                return new SummariseException(HttpStatus.TOO_MANY_REQUESTS, "GEMINI_QUOTA_EXCEEDED",
                        "The Gemini daily quota is used up. Try again after it resets.");
            }
            return new SummariseException(HttpStatus.TOO_MANY_REQUESTS, "GEMINI_RATE_LIMITED",
                    "Gemini is rate limiting requests. Wait a minute and try again.");
        }
        if (status == 402) {
            return new SummariseException(HttpStatus.BAD_GATEWAY, "GEMINI_QUOTA_EXCEEDED",
                    "The Gemini account has no credit left.");
        }
        if (status == 404 || upstreamCode.equals("model_not_found")) {
            return new SummariseException(HttpStatus.BAD_GATEWAY, "GEMINI_MODEL_NOT_FOUND",
                    "The configured Gemini model was not found. Check the gemini.model property.");
        }
        if (status == 504 || upstreamCode.equals("deadline_exceeded")) {
            return new SummariseException(HttpStatus.GATEWAY_TIMEOUT, "GEMINI_TIMEOUT",
                    "Gemini did not respond in time. Try again.");
        }
        if (status >= 500) {
            boolean overloaded = status == 503 || upstreamCode.equals("service_unavailable");
            return new SummariseException(HttpStatus.BAD_GATEWAY, "GEMINI_UNAVAILABLE",
                    "Gemini is temporarily unavailable. Try again.", overloaded);
        }
        return new SummariseException(HttpStatus.BAD_GATEWAY, "GEMINI_BAD_REQUEST",
                "Gemini rejected the request. This is a server-side problem; see the server log.");
    }

    /** Shortens an upstream message for the log and makes sure the key can never appear in it. */
    private String abbreviate(String s) {
        String safe = apiKey.isEmpty() ? s : s.replace(apiKey, "***");
        return safe.length() <= 300 ? safe : safe.substring(0, 300) + "...";
    }
}
