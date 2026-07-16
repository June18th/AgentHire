package com.git.hui.jobclaw.agents.jobfetch.crawler.impl.tool;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class SmartWebFetchToolTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/admin",
            "http://127.0.0.1/admin",
            "http://10.0.0.1/internal",
            "http://192.168.1.1/internal",
            "http://169.254.169.254/latest/meta-data",
            "http://100.64.0.1/internal",
            "http://[::1]/internal",
            "file:///etc/passwd",
            "ftp://93.184.216.34/jobs"
    })
    void blocksLocalPrivateAndUnsupportedUrls(String url) {
        assertThat(SmartWebFetchTool.checkLocalUrlSafety(URI.create(url)).canFetch()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://93.184.216.34/jobs",
            "https://8.8.8.8/jobs"
    })
    void allowsPublicHttpAddresses(String url) {
        assertThat(SmartWebFetchTool.checkLocalUrlSafety(URI.create(url)).canFetch()).isTrue();
    }
}
