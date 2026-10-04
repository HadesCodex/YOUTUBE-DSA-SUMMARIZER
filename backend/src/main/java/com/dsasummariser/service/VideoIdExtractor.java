package com.dsasummariser.service;

import com.dsasummariser.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VideoIdExtractor {

    // Handles: watch?v=ID, youtu.be/ID, shorts/ID, embed/ID
    private static final Pattern PATTERN = Pattern.compile(
            "(?:youtube\\.com/watch\\?(?:.*&)?v=|youtu\\.be/|youtube\\.com/shorts/|youtube\\.com/embed/)"
                    + "([A-Za-z0-9_-]{11})");

    public String extract(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("URL is empty.");
        }
        Matcher m = PATTERN.matcher(url.trim());
        if (!m.find()) {
            throw new InvalidUrlException("Not a valid YouTube video URL.");
        }
        return m.group(1);
    }
}
