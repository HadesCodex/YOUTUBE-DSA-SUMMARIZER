package com.dsasummariser.service;

import com.dsasummariser.exception.TranscriptUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns YouTube's timedtext XML into one clean string. Pure function, no network,
 * so it can be unit tested without YouTube.
 *
 * Input looks like: <timedtext format="3"><body><p t="1360" d="1680">some text</p>...</body></timedtext>
 */
@Component
public class TranscriptParser {

    // Sound cues like [Music] or [Applause] carry no lecture content.
    private static final Pattern SOUND_CUE = Pattern.compile("\\[[^\\]]*]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new TranscriptUnavailableException("YouTube returned an empty transcript for this video.");
        }

        NodeList paragraphs = toDocument(xml).getElementsByTagName("p");
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < paragraphs.getLength(); i++) {
            // getTextContent() also joins nested <s> word tags used by auto-generated captions.
            String text = paragraphs.item(i).getTextContent();
            // Some tracks are double-encoded (&amp;#39;), so XML decoding leaves "&#39;" behind.
            text = HtmlUtils.htmlUnescape(text);
            text = SOUND_CUE.matcher(text).replaceAll(" ");
            text = WHITESPACE.matcher(text).replaceAll(" ").trim();
            if (!text.isEmpty()) {
                segments.add(text);
            }
        }

        if (segments.isEmpty()) {
            throw new TranscriptUnavailableException("The caption track for this video contains no text.");
        }
        return String.join(" ", segments);
    }

    private Document toDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Block XXE: the XML comes from the internet, so never allow DTDs or external entities.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new TranscriptUnavailableException("Could not read the transcript YouTube returned.");
        }
    }
}
