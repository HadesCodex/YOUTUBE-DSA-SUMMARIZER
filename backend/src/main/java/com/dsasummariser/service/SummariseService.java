package com.dsasummariser.service;

import com.dsasummariser.model.LectureSummary;
import com.dsasummariser.model.SummariseRequest;
import com.dsasummariser.model.SummariseResponse;
import com.dsasummariser.model.TokenUsage;
import com.dsasummariser.model.TranscriptStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The whole request: transcript -> filter -> Gemini -> validated summary.
 */
@Service
public class SummariseService {

    private static final Logger log = LoggerFactory.getLogger(SummariseService.class);

    private final TranscriptService transcriptService;
    private final GeminiClient geminiClient;
    private final SummaryParser summaryParser;
    private final boolean filterEnabled;

    public SummariseService(TranscriptService transcriptService, GeminiClient geminiClient,
                            SummaryParser summaryParser,
                            @Value("${summariser.filter-enabled:true}") boolean filterEnabled) {
        this.transcriptService = transcriptService;
        this.geminiClient = geminiClient;
        this.summaryParser = summaryParser;
        this.filterEnabled = filterEnabled;
    }

    public SummariseResponse summarise(SummariseRequest request) {
        PreparedTranscript prepared = transcriptService.prepare(request);

        String textToSend;
        TranscriptStats stats;
        if (filterEnabled) {
            textToSend = prepared.selection().keptText();
            stats = prepared.selection().stats();
        } else {
            // Measurement mode (summariser.filter-enabled=false): send everything, so the logged input
            // tokens can be compared with a filtered run of the same video.
            textToSend = prepared.transcript();
            int chunks = prepared.selection().stats().chunksCreated();
            stats = new TranscriptStats(prepared.wordCount(), chunks, chunks, prepared.wordCount());
        }

        GeminiResult result = geminiClient.summarise(textToSend);

        // Logged before parsing so the tokens are recorded even if the summary is then rejected.
        TokenUsage usage = result.usage();
        log.info("Gemini call: video={} modelUsed={} attempts={} filter={} totalWords={} wordsSent={} "
                        + "inputTokens={} outputTokens={} thoughtTokens={} totalTokens={}",
                prepared.videoId(), result.modelUsed(), result.attempts(), filterEnabled, stats.totalWords(),
                stats.wordsSent(), usage.inputTokens(), usage.outputTokens(), usage.thoughtTokens(),
                usage.totalTokens());

        LectureSummary summary = summaryParser.parse(result.text());
        return new SummariseResponse(prepared.videoId(), prepared.wordCount(), prepared.source(),
                result.modelUsed(), stats, usage, summary);
    }
}
