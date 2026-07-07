package com.git.hui.jobclaw.user.helper;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretPolicyTest {

    @Test
    void usesDevelopmentFallbackWhenDevSecretIsBlank() {
        SessionHelper.JwtProperties properties = new SessionHelper.JwtProperties();
        properties.setSecret("");

        String secret = JwtSecretPolicy.resolveSecret(properties, new MockEnvironment().withProperty("spring.profiles.active", "dev"));

        assertThat(secret).isEqualTo(JwtSecretPolicy.DEV_FALLBACK_SECRET);
    }

    @Test
    void rejectsBlankSecretForProductionProfile() {
        assertThatThrownBy(() -> JwtSecretPolicy.validateProductionSecret(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured");
    }

    @Test
    void rejectsLegacyDefaultSecretForProductionProfile() {
        assertThatThrownBy(() -> JwtSecretPolicy.validateProductionSecret("jobclaw"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsafe placeholder");
    }

    @Test
    void rejectsShortProductionSecret() {
        assertThatThrownBy(() -> JwtSecretPolicy.validateProductionSecret("short-but-not-placeholder"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    void acceptsStrongProductionSecret() {
        JwtSecretPolicy.validateProductionSecret("a-strong-jwt-secret-with-32-plus-chars");
    }
}
