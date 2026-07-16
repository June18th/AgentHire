package com.git.hui.jobclaw.core.router.intent.impl;

import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemSessionAgentBinderTest {

    @Test
    void reclassifiesBoundConversationAndRestoresIntentHistory() throws Exception {
        String userId = "test-" + UUID.randomUUID();
        String sessionId = "session-" + UUID.randomUUID();
        Path userDir = Path.of("workspace", "sessions", userId);
        Path sessionFile = userDir.resolve("session-" + sessionId + ".yaml");
        try {
            FileSystemSessionAgentBinder binder = new FileSystemSessionAgentBinder();
            binder.bind(userId, sessionId, PresetAgentIntro.RECOMMEND.getAgentId());
            binder.addIntentHistory(userId, sessionId, PresetAgentIntro.RECOMMEND, 0.95);

            assertThat(binder.needsIntentRecognition(userId, sessionId, "记录这次投递")).isTrue();

            FileSystemSessionAgentBinder restored = new FileSystemSessionAgentBinder();
            assertThat(restored.getIntentHistory(userId, sessionId))
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.intentType()).isEqualTo(PresetAgentIntro.RECOMMEND);
                        assertThat(item.confidence()).isEqualTo(0.95);
                    });
        } finally {
            Files.deleteIfExists(sessionFile);
            Files.deleteIfExists(userDir);
        }
    }
}
