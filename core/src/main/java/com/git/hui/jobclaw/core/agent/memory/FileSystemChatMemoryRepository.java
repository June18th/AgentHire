package com.git.hui.jobclaw.core.agent.memory;

import com.git.hui.jobclaw.core.agent.identity.UserIdentityExtractor;
import com.git.hui.jobclaw.core.agent.identity.UserIdentityManager;
import com.git.hui.jobclaw.core.utils.MD5Utils;
import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.AppendableChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Persists chat conversation history as YAML files inside the agent workspace.
 *
 * <p>The conversation ID is the channel name, e.g. {@code web} or
 * {@code telegram-123456789}. The repository maps this to a flat file:
 * {@code {workspace}/conversations/{jobClawUserId}/chat-{channel}.yaml}
 *
 * <p>Each file has a frontmatter block with timestamps and a body containing the
 * message list:
 * <pre>
 * ---
 * createdAt: 2026-03-21T10:00:00Z
 * updatedAt: 2026-03-21T10:05:30Z
 * ---
 * - user: |
 *     Question text
 * - assistant: |
 *     Answer text
 * </pre>
 */
@Component
public class FileSystemChatMemoryRepository implements AppendableChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemChatMemoryRepository.class);

    private final Path conversationsDir;
    private final SmartWindowChatMemory smartWindow;
    private final SessionSummarizer sessionSummarizer;
    private final UserIdentityManager useridentityManager;
    private final UserIdentityExtractor useridentityExtractor;
    private final ContextWindowProperties contextWindowProperties;

    public FileSystemChatMemoryRepository(
            @Value("${agent.workspace:Unknown}") Resource workspaceDir,
            SmartWindowChatMemory smartWindow,
            SessionSummarizer sessionSummarizer,
            UserIdentityManager useridentityManager,
            UserIdentityExtractor useridentityExtractor, ContextWindowProperties contextWindowProperties) throws IOException {
        this.conversationsDir = workspaceDir.getFile().toPath().resolve("conversations");
        this.smartWindow = smartWindow;
        this.sessionSummarizer = sessionSummarizer;
        this.useridentityManager = useridentityManager;
        this.useridentityExtractor = useridentityExtractor;
        this.contextWindowProperties = contextWindowProperties;
    }

    @Override
    public List<String> findConversationIds() {
        if (!Files.exists(conversationsDir)) return List.of();
        try (Stream<Path> files = Files.list(conversationsDir)) {
            List<String> total = new ArrayList<>();
            for (Path p : files.toList()) {
                try (var sub = Files.list(p)) {
                    total.addAll(
                            sub.map(t -> t.getFileName().toString())
                               .filter(name -> name.startsWith("chat-") && name.endsWith(".yaml"))
                               .map(name -> name.substring("chat-".length(), name.length() - ".yaml".length()))
                               .toList()
                    );
                }
            }
            return total;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list conversations", e);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Path file = resolveFile(conversationId);
        if (!Files.exists(file)) return List.of();
        try {
            YamlDocument doc = YamlParser.parse(Files.readString(file));
            List<Message> allMessages = ChatYamlSerializer.deserialize(doc.body());

            // Apply smart window management
            List<Message> managedMessages = smartWindow.manage(allMessages);

            // Inject summary if available
            String summary = doc.frontmatter().get("summary");
            if (summary != null && !summary.isBlank()) {
                Message summaryMessage = sessionSummarizer.createSummaryMessage(summary);
                if (summaryMessage != null) {
                    // Insert summary at the beginning
                    List<Message> withSummary = new ArrayList<>();
                    withSummary.add(summaryMessage);
                    withSummary.addAll(managedMessages);
                    log.debug("Injected summary for conversation: {}", conversationId);
                    return withSummary;
                }
            }

            return managedMessages;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read conversation: " + conversationId, e);
        }
    }

    /**
     * Load all messages from file without applying window management.
     * Used internally for appending messages to preserve full history.
     *
     * @param conversationId conversation ID
     * @return all messages without truncation
     */
    private List<Message> loadAllMessages(String conversationId) {
        Path file = resolveFile(conversationId);
        if (!Files.exists(file)) return List.of();
        try {
            YamlDocument doc = YamlParser.parse(Files.readString(file));
            return ChatYamlSerializer.deserialize(doc.body());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read conversation: " + conversationId, e);
        }
    }

    @Override
    public void appendAll(String conversationId, List<Message> messages) {
        // Load ALL messages (without window truncation) to preserve full history
        List<Message> existing = loadAllMessages(conversationId);
        List<Message> combined = Stream.concat(existing.stream(), messages.stream()).toList();
        saveAll(conversationId, combined);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Path file = resolveFile(conversationId);
        ensureDirectory(file.getParent());

        // Preserve createdAt from any existing file; set it fresh on first write
        String createdAt = Instant.now().toString();
        String existingSummary = "";
        if (Files.exists(file)) {
            try {
                Map<String, String> existing = YamlParser.parse(Files.readString(file)).frontmatter();
                if (existing.containsKey("createdAt")) createdAt = existing.get("createdAt");
                // Preserve existing summary
                if (existing.containsKey("summary")) {
                    existingSummary = existing.get("summary");
                }
            } catch (IOException ignored) {
            }
        }

        // Generate new summary if needed
        String summary = existingSummary;
        if (sessionSummarizer.shouldSummarize(messages)) {
            log.info("Generating summary for conversation: {} ({} messages)", conversationId, messages.size());
            try {
                UserConversation conversation = UserConversation.parse(conversationId);
                summary = sessionSummarizer.summarize(conversation.jobClawUserId, messages);
                if (summary != null && !summary.isBlank()) {
                    log.info("Summary saved: {}", summary.substring(0, Math.min(50, summary.length())));
                }
            } catch (Exception e) {
                log.error("Failed to generate summary, keeping existing", e);
                // Keep existing summary on failure
            }
        }

        Map<String, String> frontmatter = new LinkedHashMap<>();
        frontmatter.put("createdAt", createdAt);
        frontmatter.put("updatedAt", Instant.now().toString());
        if (summary != null && !summary.isBlank()) {
            frontmatter.put("summary", summary);
        }

        String body = ChatYamlSerializer.serialize(messages);
        String content = YamlParser.serialize(new YamlDocument(frontmatter, body));
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save conversation: " + conversationId, e);
        }

        // Async: Update user identity profile
        updateUserIdentityAsync(conversationId, messages);
    }

    /**
     * Asynchronously update user identity profile based on conversation history.
     *
     * <p>This method handles incremental identity updates for existing users.
     * Unlike active collection (for new users), this performs passive extraction
     * from conversation history to keep the identity profile up-to-date.
     *
     * <p>Update strategy:
     * <ul>
     *   <li>Only triggers when conversation reaches certain size (avoid frequent AI calls)</li>
     *   <li>Uses existing identity as baseline for incremental update</li>
     *   <li>Runs asynchronously to avoid blocking conversation</li>
     *   <li>Graceful fallback on failure (keeps existing identity)</li>
     * </ul>
     *
     * @param conversationId conversation ID (contains jobClawUserId)
     * @param messages conversation messages
     */
    private void updateUserIdentityAsync(String conversationId, List<Message> messages) {
        try {
            UserConversation userConv = UserConversation.parse(conversationId);
            String jobClawUserId = userConv.jobClawUserId();

            // Check if update should be triggered (avoid frequent AI calls)
            if (!useridentityManager.shouldAutoUpdateIdentity(jobClawUserId, messages)) {
                return;
            }

            log.info("Triggering incremental identity update for user: {} ({} messages)", jobClawUserId, messages.size());

            // Load existing identity as baseline
            String currentIdentity = useridentityManager.loadIdentity(jobClawUserId);

            // Extract and update identity asynchronously
            useridentityExtractor.extractAsync(jobClawUserId, currentIdentity, messages)
                                 .thenAcceptAsync(updatedIdentity -> {
                                     if (StringUtils.isNotBlank(updatedIdentity)) {
                                         useridentityManager.saveIdentity(jobClawUserId, updatedIdentity);
                                         log.info("User identity updated incrementally for: {}", jobClawUserId);
                                     } else {
                                         log.warn("identity extraction returned empty for user: {}, keeping existing", jobClawUserId);
                                     }
                                 })
                                 .exceptionally(ex -> {
                                     log.error("Failed to update user identity incrementally for: {}, keeping existing identity",
                                             jobClawUserId, ex);
                                     return null;
                                 });

        } catch (Exception e) {
            log.error("Failed to trigger incremental identity update for conversation: {}", conversationId, e);
            // Don't throw - identity update is non-critical
        }
    }


    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            Files.deleteIfExists(resolveFile(conversationId));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete conversation: " + conversationId, e);
        }
    }

    /**
     * Maps a conversation ID (channel name) to
     * {@code conversations/{jobClawUserId}/chat-{channel}.yaml}.
     */
    private Path resolveFile(String conversationId) {
        // 按照约定，原始的conversationId 是按照 jobClawUerId-ConversationId 的格式进行组装的，所以我们首先进行解析，将会话的JobClawUserId依然保存，用于用户会话的隔离
        UserConversation userConversation = UserConversation.parse(conversationId);
        // 为了避免会话的字符串格式异常，我们统一进行md5操作
        String md5 = MD5Utils.md5(userConversation.conversationId);
        return conversationsDir.resolve(userConversation.jobClawUserId).resolve("chat-" + md5 + ".yaml");
    }

    private static Path ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + dir, e);
        }
    }


    record UserConversation(String jobClawUserId, String conversationId) {
        public static UserConversation parse(String conversationId) {
            int index = conversationId.indexOf("-");
            if (index > 0) {
                String jobClawUserId = conversationId.substring(0, index);
                String conversation = conversationId.substring(index + 1);
                return new UserConversation(jobClawUserId, conversation);
            }
            return new UserConversation("Unknown", conversationId);
        }
    }
}
