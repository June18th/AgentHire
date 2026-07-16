package com.git.hui.jobclaw.agents.jobfetch.search;

import java.util.List;

/**
 * Discovers public job-related pages without coupling the fetch agent to one search vendor.
 * AI-GENERATED
 */
public interface JobSearchProvider {

    String provider();

    boolean isAvailable();

    List<JobSearchCandidate> search(String query, int limit);
}
