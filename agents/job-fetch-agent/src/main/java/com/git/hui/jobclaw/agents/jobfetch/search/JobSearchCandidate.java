package com.git.hui.jobclaw.agents.jobfetch.search;

/**
 * A structured web-search result that may lead to one or more job postings.
 * AI-GENERATED
 */
public record JobSearchCandidate(
        String title,
        String url,
        String snippet,
        String source,
        String publishDate
) {
}
