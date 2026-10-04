package com.dsasummariser.service;

import com.dsasummariser.exception.TranscriptUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The whole fetch (player call, then caption download) against a mock server: no real YouTube calls.
 * Checks the error code each failure produces, because the frontend branches on those codes.
 */
class YoutubeTranscriptFetcherHttpTest {

    static final String PLAYER = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";
    static final String CAPTIONS = "https://yt.test/timedtext?v=abc";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final YoutubeTranscriptFetcher fetcher = new YoutubeTranscriptFetcher(builder.build(), new TranscriptParser());

    private static String player(String tracks) {
        return "{\"playabilityStatus\":{\"status\":\"OK\"},\"captions\":{\"playerCaptionsTracklistRenderer\":{\"captionTracks\":["
                + tracks + "]}}}";
    }

    private static final String ENGLISH_TRACK = "{\"baseUrl\":\"" + CAPTIONS + "\",\"languageCode\":\"en\"}";

    private static ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private TranscriptUnavailableException failure() {
        return assertThrows(TranscriptUnavailableException.class, () -> fetcher.fetch("abcdefghijk"));
    }

    // ----- success -----

    @Test
    void fetchesThePlayerThenTheCaptionsAndReturnsCleanText() {
        server.expect(requestTo(PLAYER)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.videoId").value("abcdefghijk"))
                .andExpect(jsonPath("$.context.client.clientName").value("ANDROID"))
                .andRespond(json(player(ENGLISH_TRACK)));
        server.expect(requestTo(CAPTIONS)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("<timedtext><body><p t=\"0\" d=\"1\">hello</p><p t=\"1\" d=\"1\">world [Music]</p></body></timedtext>",
                        MediaType.TEXT_XML));

        assertEquals("hello world", fetcher.fetch("abcdefghijk"));
        server.verify();
    }

    // ----- transcript problems that come from the video itself -----

    @Test
    void noCaptionsAtAllIsNoCaptions() {
        server.expect(requestTo(PLAYER)).andRespond(json("{\"playabilityStatus\":{\"status\":\"OK\"}}"));

        TranscriptUnavailableException ex = failure();

        assertEquals("NO_CAPTIONS", ex.getCode());
    }

    @Test
    void onlyNonEnglishCaptionsIsNoEnglishCaptions() {
        server.expect(requestTo(PLAYER)).andRespond(json(player(
                "{\"baseUrl\":\"https://yt.test/hi\",\"languageCode\":\"hi\",\"kind\":\"asr\"}")));

        TranscriptUnavailableException ex = failure();

        assertEquals("NO_ENGLISH_CAPTIONS", ex.getCode());
        assertEquals(true, ex.getMessage().contains("Only English captions are supported"));
    }

    @Test
    void aPrivateOrRemovedVideoIsVideoUnavailable() {
        server.expect(requestTo(PLAYER)).andRespond(json(
                "{\"playabilityStatus\":{\"status\":\"ERROR\",\"reason\":\"Video unavailable\"}}"));
        assertEquals("VIDEO_UNAVAILABLE", failure().getCode());
        server.reset();

        server.expect(requestTo(PLAYER)).andRespond(json(
                "{\"playabilityStatus\":{\"status\":\"LOGIN_REQUIRED\",\"reason\":\"This video is private\"}}"));
        assertEquals("VIDEO_UNAVAILABLE", failure().getCode());
    }

    @Test
    void aBotCheckMeansYoutubeRefusesThisServerNotThatTheVideoIsGone() {
        server.expect(requestTo(PLAYER)).andRespond(json(
                "{\"playabilityStatus\":{\"status\":\"LOGIN_REQUIRED\",\"reason\":\"Sign in to confirm you're not a bot\"}}"));

        assertEquals("YOUTUBE_REFUSED", failure().getCode());
    }

    @Test
    void anEmptyCaptionFileIsTheGenericCode() {
        server.expect(requestTo(PLAYER)).andRespond(json(player(ENGLISH_TRACK)));
        server.expect(requestTo(CAPTIONS)).andRespond(withSuccess("   ", MediaType.TEXT_XML));

        assertEquals("TRANSCRIPT_UNAVAILABLE", failure().getCode());
    }

    // ----- failures talking to YouTube -----

    static Stream<Arguments> youtubeHttpFailures() {
        return Stream.of(
                Arguments.of(403, "YOUTUBE_REFUSED"),
                Arguments.of(429, "YOUTUBE_REFUSED"),
                Arguments.of(500, "YOUTUBE_UNREACHABLE"),
                Arguments.of(503, "YOUTUBE_UNREACHABLE"),
                Arguments.of(404, "YOUTUBE_UNREACHABLE"));
    }

    @ParameterizedTest(name = "player call returns HTTP {0} -> {1}")
    @MethodSource("youtubeHttpFailures")
    void playerCallHttpErrorsAreMappedToTheRightCode(int status, String expectedCode) {
        server.expect(requestTo(PLAYER)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertEquals(expectedCode, failure().getCode());
    }

    @ParameterizedTest(name = "caption download returns HTTP {0} -> {1}")
    @MethodSource("youtubeHttpFailures")
    void captionDownloadHttpErrorsAreMappedToTheRightCode(int status, String expectedCode) {
        server.expect(requestTo(PLAYER)).andRespond(json(player(ENGLISH_TRACK)));
        server.expect(requestTo(CAPTIONS)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertEquals(expectedCode, failure().getCode());
    }

    @Test
    void timeoutsAndConnectionFailuresAreYoutubeUnreachable() {
        server.expect(requestTo(PLAYER)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertEquals("YOUTUBE_UNREACHABLE", failure().getCode());
        server.reset();

        server.expect(requestTo(PLAYER)).andRespond(withException(new ConnectException("Connection refused")));
        assertEquals("YOUTUBE_UNREACHABLE", failure().getCode());
        server.reset();

        server.expect(requestTo(PLAYER)).andRespond(json(player(ENGLISH_TRACK)));
        server.expect(requestTo(CAPTIONS)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertEquals("YOUTUBE_UNREACHABLE", failure().getCode());
    }
}
