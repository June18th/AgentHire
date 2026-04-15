package com.git.hui.jobclaw.core.agent.identity.collector;

import java.util.Optional;

/**
 * Common interface for identity/profile collectors.
 *
 * <p>Implementations can use different strategies:
 * <ul>
 *   <li>Rule-based: Predefined question flow</li>
 *   <li>AI-based: LLM-driven dynamic conversation</li>
 * </ul>
 *
 * AIDEV-NOTE: Common interface for Phase 3 identity collection strategies
 */
public interface IdentityCollector {

    /**
     * Collector type enum
     */
    enum CollectorType {
        /** Rule-based with predefined questions */
        RULE_BASED,
        /** AI-driven dynamic conversation */
        AI_BASED
    }

    /**
     * Check if we should initiate identity collection for this user.
     *
     * @param jobClawUserId user ID
     * @return true if collection should be initiated
     */
    boolean shouldInitiateCollection(String jobClawUserId);

    /**
     * Initiate active identity collection for a new user.
     *
     * @param jobClawUserId user ID
     * @param channel active channel
     * @param conversationId conversation ID
     */
    void initiateCollection(String jobClawUserId, String channel, String conversationId);

    /**
     * Process user's answer and continue collection.
     *
     * @param jobClawUserId user ID
     * @param userMessage user's message
     * @param channel active channel
     * @param conversationId conversation ID
     */
    void processAnswer(String jobClawUserId, String userMessage, String channel, String conversationId);

    /**
     * Get collection state for a user.
     *
     * @param jobClawUserId user ID
     * @return collection state if exists
     */
    Optional<IdentityCollectionState> getCollectionState(String jobClawUserId);

    /**
     * Get the collector type.
     *
     * @return collector type
     */
    CollectorType getCollectorType();

}
