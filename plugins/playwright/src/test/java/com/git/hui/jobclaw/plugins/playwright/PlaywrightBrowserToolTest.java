package com.git.hui.jobclaw.plugins.playwright;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlaywrightBrowserToolTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/admin",
            "http://127.0.0.1/admin",
            "http://10.0.0.1/internal",
            "http://169.254.169.254/latest/meta-data",
            "file:///etc/passwd"
    })
    void blocksUnsafeNavigationBeforeLaunchingBrowser(String url) {
        try (PlaywrightBrowserTool tool = PlaywrightBrowserTool.builder().build()) {
            assertThat(tool.navigateTo(url)).startsWith("URL safety check failed");
        }
    }
}
