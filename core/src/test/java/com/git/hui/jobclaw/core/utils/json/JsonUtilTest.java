package com.git.hui.jobclaw.core.utils.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilTest {

    @Test
    void usesJackson3AndRoundTripsJavaTime() {
        ObjectMapper mapper = JsonUtil.getMapper();
        Payload expected = new Payload("JobClaw", LocalDateTime.of(2026, 7, 16, 21, 0));

        String json = JsonUtil.toStr(expected);
        Payload actual = JsonUtil.toObj(json, Payload.class);

        assertThat(mapper.getClass().getPackageName()).startsWith("tools.jackson.databind");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void acceptsUnescapedControlCharactersFromModelOutput() {
        PayloadWithText actual = JsonUtil.toObj("{\"text\":\"line\tbreak\"}", PayloadWithText.class);

        assertThat(actual.text()).isEqualTo("line\tbreak");
    }

    private record Payload(String name, LocalDateTime time) {
    }

    private record PayloadWithText(String text) {
    }
}
