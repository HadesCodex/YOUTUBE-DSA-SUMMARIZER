package com.dsasummariser.service;

import com.dsasummariser.exception.TranscriptUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches captions straight from YouTube's internal "InnerTube" API in two steps:
 *   1. POST /youtubei/v1/player  -> JSON that lists the caption tracks (captionTracks[].baseUrl)
 *   2. GET  baseUrl              -> timedtext XML, which TranscriptParser turns into plain text
 *
 * We pretend to be the ANDROID app because the WEB client's caption URLs now require a
 * proof-of-origin token we cannot produce. This API is undocumented and may change; if it
 * breaks, the constants below are the first thing to update.
 */
@Component
public class YoutubeTranscriptFetcher implements TranscriptFetcher {

    private static final String PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";
    private static final String CLIENT_VERSION = "20.10.38";
    private static final String USER_AGENT =
            "com.google.android.youtube/" + CLIENT_VERSION + " (Linux; U; Android 14) gzip";

    private final RestClient restClient;
    private final TranscriptParser parser;

    @Autowired
    public YoutubeTranscriptFetcher(RestClient.Builder builder, TranscriptParser parser) {
        // Timeouts so a slow YouTube cannot hang our request threads forever.
        this(builder
                .requestFactory(timeouts())
                .defaultHeader("User-Agent", USER_AGENT)
                .build(), parser);
    }

    // Package-private so tests can pass a RestClient wired to a mock server.
    YoutubeTranscriptFetcher(RestClient restClient, TranscriptParser parser) {
        this.restClient = restClient;
        this.parser = parser;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    @Override
    public String fetch(String videoId) {
        try {
            JsonNode player = fetchPlayerResponse(videoId);
            URI captionUrl = findCaptionUrl(player);
            String xml = restClient.get().uri(captionUrl).retrieve().body(String.class);
            return parser.parse(xml);
        } catch (RestClientResponseException e) {
            throw httpFailure(e.getStatusCode());
        } catch (RestClientException e) {
            // Timeouts, DNS failures, connection resets: nothing came back from YouTube at all.
            throw new TranscriptUnavailableException("YOUTUBE_UNREACHABLE",
                    "Could not reach YouTube to fetch the transcript. Check your connection or paste the transcript instead.");
        }
    }

    private JsonNode fetchPlayerResponse(String videoId) {
        Map<String, Object> body = Map.of(
                "videoId", videoId,
                "context", Map.of("client", Map.of(
                        "clientName", "ANDROID",
                        "clientVersion", CLIENT_VERSION,
                        "androidSdkVersion", 34,
                        "hl", "en")));
        JsonNode response = restClient.post()
                .uri(PLAYER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new TranscriptUnavailableException("YouTube returned an empty response for this video.");
        }
        return response;
    }

    // Package-private so unit tests can feed it canned JSON without any network.
    URI findCaptionUrl(JsonNode player) {
        checkPlayable(player);

        JsonNode tracks = player.path("captions").path("playerCaptionsTracklistRenderer").path("captionTracks");
        if (!tracks.isArray() || tracks.isEmpty()) {
            throw new TranscriptUnavailableException("NO_CAPTIONS",
                    "This video has no captions, so there is no transcript to fetch. Paste the transcript instead.");
        }

        JsonNode chosen = pickEnglishTrack(tracks).orElseThrow(() -> new TranscriptUnavailableException(
                "NO_ENGLISH_CAPTIONS",
                "This video has no English captions. Only English captions are supported, "
                        + "so paste an English transcript instead."));
        String baseUrl = chosen.path("baseUrl").asText("");
        if (baseUrl.isBlank()) {
            throw new TranscriptUnavailableException("YouTube did not give a usable caption link for this video.");
        }
        return URI.create(baseUrl);
    }

    private void checkPlayable(JsonNode player) {
        JsonNode playability = player.path("playabilityStatus");
        String status = playability.path("status").asText("");
        if (status.equals("OK")) {
            return;
        }
        // YouTube's own wording ("Video unavailable", "This video is private", "Sign in to confirm
        // you're not a bot", ...) is the most accurate explanation we have, so pass it through.
        String reason = playability.path("reason").asText("");
        if (reason.isBlank()) {
            reason = "status " + (status.isBlank() ? "unknown" : status);
        }
        // A bot check ("Sign in to confirm you're not a bot") means YouTube is refusing THIS SERVER, not that
        // the video is gone, so it gets its own code.
        String code = reason.toLowerCase(Locale.ROOT).contains("bot") ? "YOUTUBE_REFUSED" : "VIDEO_UNAVAILABLE";
        throw new TranscriptUnavailableException(code,
                "This video can't be accessed (" + reason + "). It may be private, removed, age-restricted or blocked. Paste the transcript instead.");
    }

    /**
     * English only: any language code starting with "en" (en, en-US, en-GB, en-IN, ...).
     * Preference order: human-made English, then auto-generated English; otherwise empty.
     * Human captions are more accurate, which matters for technical terms like "heapify".
     *
     * Why English only: the chunk scorer's keyword lists are English, so a transcript in another
     * language would score 0 everywhere and be filtered arbitrarily.
     */
    Optional<JsonNode> pickEnglishTrack(JsonNode tracks) {
        JsonNode englishAuto = null;
        for (JsonNode track : tracks) {
            boolean english = track.path("languageCode").asText("").toLowerCase(Locale.ROOT).startsWith("en");
            if (!english) {
                continue;
            }
            boolean auto = "asr".equals(track.path("kind").asText());
            if (!auto) {
                return Optional.of(track);
            }
            if (englishAuto == null) {
                englishAuto = track;
            }
        }
        return Optional.ofNullable(englishAuto);
    }

    private TranscriptUnavailableException httpFailure(HttpStatusCode status) {
        if (status.value() == 429 || status.value() == 403) {
            return new TranscriptUnavailableException("YOUTUBE_REFUSED",
                    "YouTube is refusing requests from this server (HTTP " + status.value()
                            + "). Try again later or paste the transcript instead.");
        }
        return new TranscriptUnavailableException("YOUTUBE_UNREACHABLE",
                "YouTube returned an error (HTTP " + status.value() + "). Try again later or paste the transcript instead.");
    }
}
