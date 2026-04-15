package com.git.hui.jobclaw.core.agent.identity;

import com.git.hui.jobclaw.core.agent.identity.info.UserAgentInfoCollector;
import com.git.hui.jobclaw.core.agent.identity.soul.UserAgentSoulCollector;
import com.git.hui.jobclaw.core.agent.identity.user.IdentityCollectorSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Unified identity initializer that coordinates the complete initialization sequence.
 *
 * <p>Manages three initialization phases using the Collector pattern:
 * <ol>
 *   <li>SOUL_COLLECTION - Uses SoulCollector to collect AI personality via multi-turn conversation</li>
 *   <li>USER_ID_COLLECTION - Uses IdentityCollector to collect user profile via multi-turn conversation</li>
 *   <li>INFO_GENERATION - Uses InfoCollector to auto-generate AI identity card (no user interaction)</li>
 * </ol>
 *
 * <p>Each phase uses its own Collector implementation, following the same interface pattern.
 *
 * AIDEV-NOTE: Unified initializer for Phase 4 - replaces AgentIdentityInitializer
 */
@Component
public class UnifiedIdentityInitializer {

    private static final Logger log = LoggerFactory.getLogger(UnifiedIdentityInitializer.class);

    private final UnifiedCollectionStateManager stateManager;
    private final UserAgentSoulCollector userAgentSoulCollector;
    private final IdentityCollectorSelector identityCollectorSelector;
    private final UserAgentInfoCollector userAgentInfoCollector;

    public UnifiedIdentityInitializer(UnifiedCollectionStateManager stateManager,
                                      UserAgentSoulCollector userAgentSoulCollector,
                                      IdentityCollectorSelector identityCollectorSelector,
                                      UserAgentInfoCollector userAgentInfoCollector) {
        this.stateManager = stateManager;
        this.userAgentSoulCollector = userAgentSoulCollector;
        this.identityCollectorSelector = identityCollectorSelector;
        this.userAgentInfoCollector = userAgentInfoCollector;

        log.info("UnifiedIdentityInitializer initialized");
    }

    /**
     * Check and advance the initialization sequence for a user.
     *
     * <p>This method should be called on every user message to:
     * <ul>
     *   <li>Check current initialization phase</li>
     *   <li>Route message to appropriate collector if in collection mode</li>
     *   <li>Advance to next phase when current phase completes</li>
     * </ul>
     *
     * @param jobClawUserId user ID
     * @param userMessage user's message
     * @param channel channel name
     * @param conversationId conversation ID
     * @return true if message was handled by initializer (don't send to normal agent),
     *         false if initialization is complete (proceed to normal agent)
     */
    public boolean checkAndAdvance(String jobClawUserId, String userMessage, String channel, String conversationId) {
        // Get or create unified state
        UnifiedCollectionState state = stateManager.getOrCreateState(jobClawUserId);

        // Start if not started
        if (state.getOverallStatus() == UnifiedCollectionState.OverallStatus.NOT_STARTED) {
            state.start();
            log.info("Starting unified initialization for user: {}", jobClawUserId);
        }

        // Check if already completed
        if (state.isCompleted()) {
            log.debug("Initialization already completed for user: {}", jobClawUserId);
            stateManager.removeState(jobClawUserId); // Clean up
            return false;
        }

        // Handle based on current phase
        return switch (state.getCurrentPhase()) {
            case SOUL_COLLECTION -> handleSoulCollection(state, jobClawUserId, userMessage, channel, conversationId);
            case USER_ID_COLLECTION -> handleUserIdentityCollection(state, jobClawUserId, userMessage, channel, conversationId);
            case INFO_GENERATION -> handleInfoGeneration(state, jobClawUserId, channel, conversationId);
            case COMPLETED -> {
                stateManager.removeState(jobClawUserId);
                yield false;
            }
        };
    }

    /**
     * Handle soul collection phase
     */
    private boolean handleSoulCollection(UnifiedCollectionState state, String jobClawUserId,
                                         String userMessage, String channel, String conversationId) {
        // Check if we should initiate soul collection
        if (userAgentSoulCollector.shouldInitiateCollection(jobClawUserId)) {
            log.info("[Phase 1] Initiating soul collection for user: {}", jobClawUserId);
            userAgentSoulCollector.initiateCollection(jobClawUserId, channel, conversationId);

            // Track state
            var collectionState = userAgentSoulCollector.getCollectionState(jobClawUserId);
            collectionState.ifPresent(state::setSoulState);

            return true; // Message handled
        }

        // Check if collection is in progress
        var collectionStateOpt = userAgentSoulCollector.getCollectionState(jobClawUserId);
        if (collectionStateOpt.isPresent() && collectionStateOpt.get().isInProgress()) {
            log.debug("[Phase 1] Processing soul collection answer from user: {}", jobClawUserId);

            // Process user's answer
            userAgentSoulCollector.processAnswer(jobClawUserId, userMessage, channel, conversationId);

            // Check if collection completed
            var updatedState = userAgentSoulCollector.getCollectionState(jobClawUserId);
            if (updatedState.isPresent() && updatedState.get().isCompleted()) {
                log.info("[Phase 1] Soul collection completed for user: {}", jobClawUserId);
                state.markCurrentPhaseCompleted();

                // Advance to next phase
                var nextPhase = state.advanceToNextPhase();
                log.info("[Phase 1->2] Advancing to phase: {}", nextPhase);

                // Immediately trigger next phase
                return checkAndAdvance(jobClawUserId, userMessage, channel, conversationId);
            }

            return true; // Message handled by collector
        }

        // Collection not started or completed, advance to next phase
        log.info("[Phase 1] Soul collection skipped/completed, advancing to next phase");
        state.markCurrentPhaseCompleted();
        var nextPhase = state.advanceToNextPhase();
        log.info("[Phase 1->2] Advancing to phase: {}", nextPhase);
        return checkAndAdvance(jobClawUserId, userMessage, channel, conversationId);
    }

    /**
     * Handle user identity collection phase
     */
    private boolean handleUserIdentityCollection(UnifiedCollectionState state, String jobClawUserId,
                                                 String userMessage, String channel, String conversationId) {
        // Check if we should initiate user identity collection
        var userIdentityCollector = identityCollectorSelector.getCollector(jobClawUserId);
        if (userIdentityCollector.shouldInitiateCollection(jobClawUserId)) {
            log.info("[Phase 2] Initiating user identity collection for user: {}", jobClawUserId);
            userIdentityCollector.initiateCollection(jobClawUserId, channel, conversationId);

            // Track state
            var collectionState = userIdentityCollector.getCollectionState(jobClawUserId);
            collectionState.ifPresent(state::setUserIdentityState);

            return true; // Message handled
        }

        // Check if collection is in progress
        var collectionStateOpt = userIdentityCollector.getCollectionState(jobClawUserId);
        if (collectionStateOpt.isPresent() && collectionStateOpt.get().isInProgress()) {
            log.debug("[Phase 2] Processing user identity collection answer from user: {}", jobClawUserId);

            // Process user's answer
            userIdentityCollector.processAnswer(jobClawUserId, userMessage, channel, conversationId);

            // Check if collection completed
            var updatedState = userIdentityCollector.getCollectionState(jobClawUserId);
            if (updatedState.isPresent() && updatedState.get().isCompleted()) {
                log.info("[Phase 2] User identity collection completed for user: {}", jobClawUserId);
                state.markCurrentPhaseCompleted();

                // Advance to next phase
                var nextPhase = state.advanceToNextPhase();
                log.info("[Phase 2->3] Advancing to phase: {}", nextPhase);

                // Immediately trigger next phase
                return checkAndAdvance(jobClawUserId, userMessage, channel, conversationId);
            }

            return true; // Message handled by collector
        }

        // Collection not started or completed, advance to next phase
        log.info("[Phase 2] User identity collection skipped/completed, advancing to next phase");
        state.markCurrentPhaseCompleted();
        var nextPhase = state.advanceToNextPhase();
        log.info("[Phase 2->3] Advancing to phase: {}", nextPhase);
        return checkAndAdvance(jobClawUserId, userMessage, channel, conversationId);
    }

    /**
     * Handle info generation phase
     */
    private boolean handleInfoGeneration(UnifiedCollectionState state, String jobClawUserId,
                                         String channel, String conversationId) {
        // Check if we should initiate info generation
        if (userAgentInfoCollector.shouldInitiateCollection(jobClawUserId)) {
            log.info("[Phase 3] Initiating info.md generation for user: {}", jobClawUserId);
            userAgentInfoCollector.initiateCollection(jobClawUserId, channel, conversationId);

            // Track state
            var collectionState = userAgentInfoCollector.getCollectionState(jobClawUserId);
            collectionState.ifPresent(state::setInfoState);

            return false; // Don't block user message, info generation is async
        }

        // Check if generation is in progress (wait for async completion)
        var collectionStateOpt = userAgentInfoCollector.getCollectionState(jobClawUserId);
        if (collectionStateOpt.isPresent() && collectionStateOpt.get().isInProgress()) {
            log.debug("[Phase 3] Info generation in progress for user: {}", jobClawUserId);
            return false; // Don't block, let user continue chatting
        }

        // Generation completed or skipped
        log.info("[Phase 3] Info generation completed/skipped for user: {}", jobClawUserId);
        state.markCurrentPhaseCompleted();
        state.complete();

        // Clean up
        stateManager.removeState(jobClawUserId);
        log.info("[Phase 3] Cleaning up state for user: {}", jobClawUserId);
        return false; // Initialization complete, proceed to normal agent
    }
}
