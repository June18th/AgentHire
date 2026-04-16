package com.git.hui.jobclaw.core.agent.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for smart context window management.
 *
 * <p>Controls how conversation history is managed to prevent context overflow
 * while preserving important information.
 *
 * <p>Example configuration:
 * <pre>
 * agent:
 *   context:
 *     window:
 *       enabled: true
 *       max-messages: 20
 *       keep-recent: 10
 *       max-tokens: 8000
 *       filter-short-messages: true
 *       short-message-threshold: 5
 * </pre>
 *
 */
@Data
@ConfigurationProperties(prefix = "agent.context.window")
public class ContextWindowProperties {

    /**
     * Whether smart window management is enabled.
     */
    private boolean trimEnabled = true;

    /**
     * Whether to generate session summaries.
     */
    private boolean summaryEnabled = false;

    /**
     * Whether to update user identity automatically.
     */
    private boolean identityAutoUpdate = false;

    /**
     * Maximum number of messages to keep in context.
     */
    private int maxMessages = 20;

    /**
     * Number of recent messages to always keep (never truncate).
     */
    private int keepRecent = 10;

    /**
     * Maximum token count for the entire context window.
     */
    private int maxTokens = 8000;

    /**
     * Whether to filter out short/useless messages.
     */
    private boolean filterShortMessages = true;

    /**
     * Character threshold for short message filtering.
     * Messages shorter than this will be filtered out.
     */
    private int shortMessageThreshold = 5;

    /**
     * Frequency of updating user identity.
     */
    private int updateIdentityFrequency = 10;

    /**
     * Whether to update agent soul automatically.
     */
    private boolean soulAutoUpdate = false;

    /**
     * Frequency of updating agent soul (every N messages).
     */
    private int soulUpdateFrequency = 10;

    /**
     * Whether to update agent info automatically.
     */
    private boolean infoAutoUpdate = false;

    /**
     * Trigger for info update (e.g., "user_identity_change").
     */
    private String infoUpdateTrigger = "user_identity_change";

    /**
     * Maximum conversation turns for soul collection to prevent infinite loops.
     */
    private int maxSoulCollectionTurns = 15;

    /**
     * Maximum conversation turns for identity collection to prevent infinite loops.
     */
    private int maxIdentityCollectionTurns = 20;
}
