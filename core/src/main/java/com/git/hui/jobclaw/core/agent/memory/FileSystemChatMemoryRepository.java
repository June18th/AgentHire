package com.git.hui.jobclaw.core.agent.memory;

import com.git.hui.jobclaw.core.utils.MD5Utils;
import com.git.hui.jobclaw.core.utils.files.YamlDocument;
import com.git.hui.jobclaw.core.utils.files.YamlParser;
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

    private final Path conversationsDir;

    public FileSystemChatMemoryRepository(@Value("${agent.workspace:Unknown}") Resource workspaceDir) throws IOException {
        this.conversationsDir = workspaceDir.getFile().toPath().resolve("conversations");
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
            return ChatYamlSerializer.deserialize(doc.body());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read conversation: " + conversationId, e);
        }
    }

    @Override
    public void appendAll(String conversationId, List<Message> messages) {
        List<Message> existing = findByConversationId(conversationId);
        List<Message> combined = Stream.concat(existing.stream(), messages.stream()).toList();
        saveAll(conversationId, combined);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Path file = resolveFile(conversationId);
        ensureDirectory(file.getParent());

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
        int index = conversationId.indexOf("-");
        String jobClawUserId;
        if (index > 0) {
            jobClawUserId = conversationId.substring(0, index);
            conversationId = conversationId.substring(index + 1);
        } else {
            jobClawUserId = "Unknown";
        }

        // 为了避免会话的字符串格式异常，我们统一进行md5操作
        String md5 = MD5Utils.md5(conversationId);
        return conversationsDir.resolve(jobClawUserId).resolve("chat-" + md5 + ".yaml");
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
