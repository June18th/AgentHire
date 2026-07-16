package com.git.hui.jobclaw.user.helper;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WxCallbackSignatureVerifierTest {

    @Test
    void rejectsMissingTokenOutsideDevelopment() {
        WxLoginProperties properties = new WxLoginProperties();
        WxCallbackSignatureVerifier verifier = new WxCallbackSignatureVerifier(properties, new MockEnvironment());

        assertThat(verifier.verify("anything", "1", "2")).isFalse();
    }

    @Test
    void allowsMissingTokenOnlyInDevelopment() {
        WxLoginProperties properties = new WxLoginProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThat(new WxCallbackSignatureVerifier(properties, environment)
                .verify(null, null, null)).isTrue();
    }

    @Test
    void verifiesConfiguredWechatSignature() throws Exception {
        WxLoginProperties properties = new WxLoginProperties();
        properties.setSecurityCheckToken("jobclaw-token");
        WxCallbackSignatureVerifier verifier = new WxCallbackSignatureVerifier(properties, new MockEnvironment());
        String timestamp = "1720000000";
        String nonce = "abc123";

        assertThat(verifier.verify(signature("jobclaw-token", timestamp, nonce), timestamp, nonce)).isTrue();
        assertThat(verifier.verify("invalid", timestamp, nonce)).isFalse();
    }

    private String signature(String token, String timestamp, String nonce) throws Exception {
        String[] values = {token, timestamp, nonce};
        Arrays.sort(values);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest(String.join("", values).getBytes(StandardCharsets.UTF_8)));
    }
}
