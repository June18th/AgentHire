package com.git.hui.jobclaw.user.helper;

import io.micrometer.common.util.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Locale;
import java.util.Set;

final class JwtSecretPolicy {
    static final String DEV_FALLBACK_SECRET = "jobclaw-dev-local-secret-change-me";
    private static final int MIN_PROD_SECRET_LENGTH = 32;
    private static final Set<String> WEAK_SECRETS = Set.of(
            "jobclaw",
            "changeme",
            "change_me",
            "change-me",
            "secret",
            "password"
    );

    private JwtSecretPolicy() {
    }

    static String resolveSecret(SessionHelper.JwtProperties properties, Environment environment) {
        String secret = properties.getSecret();
        boolean devLike = environment == null || environment.acceptsProfiles(Profiles.of("dev", "test"));

        if (devLike && StringUtils.isBlank(secret)) {
            return DEV_FALLBACK_SECRET;
        }

        if (!devLike) {
            validateProductionSecret(secret);
        }

        return secret;
    }

    static void validateProductionSecret(String secret) {
        if (StringUtils.isBlank(secret)) {
            throw new IllegalStateException("JOBCLAW_JWT_SECRET must be configured for non-dev profiles.");
        }

        String normalized = secret.trim().toLowerCase(Locale.ROOT);
        if (WEAK_SECRETS.contains(normalized) || normalized.startsWith("change_me")) {
            throw new IllegalStateException("JOBCLAW_JWT_SECRET uses an unsafe placeholder value.");
        }

        if (secret.length() < MIN_PROD_SECRET_LENGTH) {
            throw new IllegalStateException("JOBCLAW_JWT_SECRET must be at least 32 characters for non-dev profiles.");
        }
    }

    static boolean isWeakDevelopmentSecret(String secret) {
        if (StringUtils.isBlank(secret)) {
            return true;
        }
        String normalized = secret.trim().toLowerCase(Locale.ROOT);
        return WEAK_SECRETS.contains(normalized) || normalized.startsWith("change_me");
    }
}
