package com.git.hui.jobclaw.core.agent.memory;

import com.git.hui.jobclaw.core.agent.LlmCaller;
import com.git.hui.jobclaw.core.agent.IIdentityAgent;
import com.git.hui.jobclaw.core.utils.FileUtils;
import com.git.hui.jobclaw.core.utils.MD5Utils;
import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
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
    private final IIdentityAgent identityAgent;
    private final ContextWindowProperties contextWindowProperties;

    public FileSystemChatMemoryRepository(
            @Value("${agent.workspace:Unknown}") Resource workspaceDir,
            SmartWindowChatMemory smartWindow,
            SessionSummarizer sessionSummarizer,
            IIdentityAgent identityAgent,
            ContextWindowProperties contextWindowProperties) throws IOException {
        this.conversationsDir = workspaceDir.getFile().toPath().resolve("conversations");
        this.smartWindow = smartWindow;
        this.sessionSummarizer = sessionSummarizer;
        this.identityAgent = identityAgent;
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
            String summary = sessionSummarizer.getSummaryInfo(LlmCaller.UserConversationInfo.parse(conversationId));
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
        LlmCaller.UserConversationInfo conversation = LlmCaller.UserConversationInfo.parse(conversationId);
        Path file = resolveFile(conversationId);
        FileUtils.ensureDirectory(file.getParent());

        // Preserve createdAt from any existing file; set it fresh on first write
        String createdAt = Instant.now().toString();
        if (Files.exists(file)) {
            try {
                Map<String, String> existing = YamlParser.parse(Files.readString(file)).frontmatter();
                if (existing.containsKey("createdAt")) createdAt = existing.get("createdAt");
            } catch (IOException ignored) {
            }
        }

        Map<String, String> frontmatter = new LinkedHashMap<>();
        frontmatter.put("createdAt", createdAt);
        frontmatter.put("updatedAt", Instant.now().toString());

        String body = ChatYamlSerializer.serialize(messages);
        String content = YamlParser.serialize(new YamlDocument(frontmatter, body));
        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save conversation: " + conversationId, e);
        }

        // Async: Update user identity profile
        sessionSummarizer.autoAsyncSummarize(conversation, messages);
        identityAgent.asyncUpdateUserIdentityAsync(conversation, messages);
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
        LlmCaller.UserConversationInfo userConversation = LlmCaller.UserConversationInfo.parse(conversationId);
        // 为了避免会话的字符串格式异常，我们统一进行md5操作
        String md5 = MD5Utils.md5(userConversation.conversationId());
        return conversationsDir.resolve(userConversation.jobClawUserId()).resolve("chat-" + userConversation.channel() + "-" + md5 + ".yaml");
    }
}
