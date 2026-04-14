package com.git.hui.jobclaw.core.agent.soul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Manages user soul profile files (SOUL.md).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read user soul from workspace/users/{jobClawUserId}/soul.md</li>
 *   <li>Save updated soul to the same location</li>
 *   <li>Create directories if they don't exist</li>
 *   <li>Handle missing files gracefully</li>
 * </ul>
 *
 */
@Component
public class UserSoulManager {

    private static final Logger log = LoggerFactory.getLogger(UserSoulManager.class);

    private final Path usersDir;

    public UserSoulManager(@Value("${agent.workspace:Unknown}") Resource workspaceDir) throws IOException {
        this.usersDir = workspaceDir.getFile().toPath().resolve("users");
    }

    /**
     * Load user soul from file.
     *
     * @param jobClawUserId user ID
     * @return soul markdown content, or empty string if not exists
     */
    public String loadSoul(String jobClawUserId) {
        Path soulFile = resolveSoulFile(jobClawUserId);
        if (!Files.exists(soulFile)) {
            log.debug("No soul file found for user: {}", jobClawUserId);
            return "";
        }

        try {
            String soul = Files.readString(soulFile);
            log.debug("Loaded soul for user: {} ({} chars)", jobClawUserId, soul.length());
            return soul;
        } catch (IOException e) {
            log.error("Failed to load soul for user: {}", jobClawUserId, e);
            return "";
        }
    }

    /**
     * Save user soul to file.
     *
     * @param jobClawUserId user ID
     * @param soul soul markdown content
     */
    public void saveSoul(String jobClawUserId, String soul) {
        if (soul == null || soul.isBlank()) {
            log.debug("Skipping save of empty soul for user: {}", jobClawUserId);
            return;
        }

        Path soulFile = resolveSoulFile(jobClawUserId);
        ensureDirectory(soulFile.getParent());

        try {
            Files.writeString(soulFile, soul, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Saved soul for user: {} ({} chars) to {}", jobClawUserId, soul.length(), soulFile);
        } catch (IOException e) {
            log.error("Failed to save soul for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to save soul for user: " + jobClawUserId, e);
        }
    }

    /**
     * Check if user has a soul file.
     *
     * @param jobClawUserId user ID
     * @return true if soul file exists
     */
    public boolean hasSoul(String jobClawUserId) {
        Path soulFile = resolveSoulFile(jobClawUserId);
        return Files.exists(soulFile);
    }

    /**
     * Delete user soul file.
     *
     * @param jobClawUserId user ID
     */
    public void deleteSoul(String jobClawUserId) {
        Path soulFile = resolveSoulFile(jobClawUserId);
        try {
            Files.deleteIfExists(soulFile);
            log.info("Deleted soul for user: {}", jobClawUserId);
        } catch (IOException e) {
            log.error("Failed to delete soul for user: {}", jobClawUserId, e);
            throw new RuntimeException("Failed to delete soul for user: " + jobClawUserId, e);
        }
    }

    /**
     * Resolve soul file path: workspace/users/{jobClawUserId}/soul.md
     */
    private Path resolveSoulFile(String jobClawUserId) {
        return usersDir.resolve(jobClawUserId).resolve("soul.md");
    }

    private static Path ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + dir, e);
        }
    }
}
