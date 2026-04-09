package com.git.hui.jobclaw.components.env;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DotenvEnvironmentProcessor implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "JobClawDotenv";
    private static final String DOTENV_FILE = ".env";

    // 敏感关键词列表，包含这些关键词的key会被脱敏
    private static final String[] SENSITIVE_KEYWORDS = {
            "SECRET", "KEY", "PASSWORD", "TOKEN", "API_KEY", "PRIVATE",
            "CERT", "SERIAL", "MERCHANT"
    };

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Path dotenvPath = Path.of(System.getProperty("user.dir"), DOTENV_FILE);
        if (!Files.isRegularFile(dotenvPath)) {
            return;
        }

        Map<String, Object> properties = loadDotenv(dotenvPath);
        if (properties.isEmpty()) {
            return;
        }

        applyActiveProfiles(environment, properties);

        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().replace(PROPERTY_SOURCE_NAME,
                    new SystemEnvironmentPropertySource(PROPERTY_SOURCE_NAME, properties));
            return;
        }

        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(PROPERTY_SOURCE_NAME,
                properties));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Map<String, Object> loadDotenv(Path dotenvPath) {
        Map<String, Object> properties = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(dotenvPath, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                if (key.isEmpty()) {
                    continue;
                }

                if (log.isInfoEnabled()) {
                    log.info("[Dotenv] Loading property: {} = {}", key, maskSensitiveValue(key, value));
                }
                properties.put(key, unquote(value));
            }
        } catch (IOException ignored) {
            // Ignore malformed or unreadable .env files and continue with normal environment resolution.
        }
        return properties;
    }

    private void applyActiveProfiles(ConfigurableEnvironment environment, Map<String, Object> properties) {
        Object rawProfiles = properties.get("SPRING_PROFILES_ACTIVE");
        if (!(rawProfiles instanceof String profilesValue) || profilesValue.isBlank()) {
            return;
        }

        String[] profiles = profilesValue.split(",");
        for (int i = 0; i < profiles.length; i++) {
            profiles[i] = profiles[i].trim();
        }
        environment.setActiveProfiles(profiles);
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * 判断是否为敏感信息并脱敏
     */
    private String maskSensitiveValue(String key, String value) {
        String upperKey = key.toUpperCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (upperKey.contains(keyword)) {
                return maskValue(value);
            }
        }
        return value;
    }

    /**
     * 脱敏值：只显示前4个和后4个字符，中间用***替代
     */
    private String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}
