package com.dsasummariser.service;

import com.dsasummariser.exception.TranscriptUnavailableException;
import com.dsasummariser.model.Chunk;
import com.dsasummariser.model.ScoredChunk;
import com.dsasummariser.model.SummariseRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranscriptService {

    private final VideoIdExtractor videoIdExtractor;
    private final TranscriptFetcher transcriptFetcher;
    private final TranscriptChunker chunker;
    private final ChunkScorer scorer;
    private final ChunkSelector selector;

    public TranscriptService(VideoIdExtractor videoIdExtractor, TranscriptFetcher transcriptFetcher,
                             TranscriptChunker chunker, ChunkScorer scorer, ChunkSelector selector) {
        this.videoIdExtractor = videoIdExtractor;
        this.transcriptFetcher = transcriptFetcher;
        this.chunker = chunker;
        this.scorer = scorer;
        this.selector = selector;
    }

    /** Gets the transcript (pasted or fetched) and runs chunk -> score -> select on it. */
    public PreparedTranscript prepare(SummariseRequest request) {
        String videoId = videoIdExtractor.extract(request.url());

        String transcript;
        String source;
        if (request.transcript() != null && !request.transcript().isBlank()) {
            transcript = request.transcript().trim();
            source = "pasted";
        } else {
            transcript = transcriptFetcher.fetch(videoId);
            source = "fetched";
        }

        if (transcript == null || transcript.isBlank()) {
            throw new TranscriptUnavailableException("This video has no usable transcript.");
        }

        int wordCount = transcript.split("\\s+").length;
        return new PreparedTranscript(videoId, transcript, wordCount, source, filter(transcript));
    }

    /** chunk -> score -> select. */
    public SelectionResult filter(String transcript) {
        List<Chunk> chunks = chunker.chunk(transcript);
        List<ScoredChunk> scored = scorer.scoreAll(chunks);
        return selector.select(scored);
    }
}
