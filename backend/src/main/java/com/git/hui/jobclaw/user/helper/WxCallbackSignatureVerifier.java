package com.git.hui.jobclaw.user.helper;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** Verifies WeChat official-account callback signatures. */
@Component
public class WxCallbackSignatureVerifier {
    private final WxLoginProperties properties;
    private final Environment environment;

    public WxCallbackSignatureVerifier(WxLoginProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public boolean verify(String signature, String timestamp, String nonce) {
        String token = properties.getSecurityCheckToken();
        if (token == null || token.isBlank()) {
            return environment.acceptsProfiles(Profiles.of("dev", "test"));
        }
        if (signature == null || timestamp == null || nonce == null) {
            return false;
        }
        String[] values = {token, timestamp, nonce};
        Arrays.sort(values);
        String expected = sha1(String.join("", values));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 is unavailable", e);
        }
    }
}
