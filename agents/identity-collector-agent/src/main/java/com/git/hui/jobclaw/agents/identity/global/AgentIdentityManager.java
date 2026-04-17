package com.git.hui.jobclaw.agents.identity.global;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages global agent operation manual (agent.md).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read agent manual from workspace/agent.md</li>
 *   <li>Validate format of agent manual</li>
 *   <li>Handle missing files gracefully</li>
 *   <li>Do NOT provide save method - only manual editing supported</li>
 * </ul>
 *
 * AIDEV-NOTE: Global agent.md manager for Phase 1 implementation
 */
@Component
public class AgentIdentityManager {

    private static final Logger log = LoggerFactory.getLogger(AgentIdentityManager.class);

    private final Path agentManualPath;

    public AgentIdentityManager(@Value("${agent.workspace:Unknown}") Resource workspaceDir,
                                @Value("${agent.identity.path:AGENT.md}") String agentManualPath) throws IOException {
        this.agentManualPath = workspaceDir.getFile().toPath().resolve(agentManualPath);
        log.info("AgentIdentityManager initialized with path: {}", this.agentManualPath);
    }

    /**
     * Load agent manual from file.
     *
     * @return agent manual markdown content, or empty string if not exists
     */
    public String loadAgentIdentity() {
        if (!Files.exists(agentManualPath)) {
            log.debug("No agent manual found at: {}", agentManualPath);
            return "";
        }

        try {
            String content = Files.readString(agentManualPath);
            log.debug("Loaded agent manual ({} chars)", content.length());
            return content;
        } catch (IOException e) {
            log.error("Failed to load agent manual from: {}", agentManualPath, e);
            return "";
        }
    }

    /**
     * Check if agent manual file exists.
     *
     * @return true if agent manual file exists
     */
    public boolean hasAgentIdentity() {
        return Files.exists(agentManualPath);
    }

    /**
     * Validate the format of agent manual content.
     *
     * @param content agent manual markdown content
     * @return true if format is valid
     */
    public boolean validateFormat(String content) {
        if (content == null || content.isBlank()) {
            log.warn("Agent manual content is empty");
            return false;
        }

        // Check for required sections
        boolean hasCoreIdentity = content.contains("## 核心身份") || content.contains("## Core Identity");
        boolean hasThinkingProcess = content.contains("## 思考方式") || content.contains("## Thinking Process");
        boolean hasWorkRules = content.contains("## 工作规范") || content.contains("## Work Rules");

        if (!hasCoreIdentity) {
            log.warn("Agent manual missing core identity section");
            return false;
        }

        if (!hasThinkingProcess) {
            log.warn("Agent manual missing thinking process section");
            return false;
        }

        if (!hasWorkRules) {
            log.warn("Agent manual missing work rules section");
            return false;
        }

        log.debug("Agent manual format validation passed");
        return true;
    }

    /**
     * Get the agent manual file path.
     *
     * @return agent manual file path
     */
    public Path getAgentManualPath() {
        return agentManualPath;
    }
}
