package com.git.hui.jobclaw.core.channel;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveAiCardCacheTest {

    @Test
    void returnsNewestInitCard() throws Exception {
        AbsStreamChannel.ActiveAiCardCache cache = new AbsStreamChannel.ActiveAiCardCache();

        cache.startAiCard("robot-1", "user-1", "card-old");
        Thread.sleep(5);
        cache.startAiCard("robot-1", "user-1", "card-new");

        assertThat(cache.getActiveAiCard("robot-1", "user-1")).isEqualTo("card-new");
    }

    @Test
    void ignoresAnsweringCardsWhenFindingActiveCard() throws Exception {
        AbsStreamChannel.ActiveAiCardCache cache = new AbsStreamChannel.ActiveAiCardCache();

        cache.startAiCard("robot-1", "user-1", "card-old");
        Thread.sleep(5);
        cache.startAiCard("robot-1", "user-1", "card-new");
        cache.answerAiCard("robot-1", "user-1", "card-new");

        assertThat(cache.getActiveAiCard("robot-1", "user-1")).isEqualTo("card-old");
    }

    @Test
    void removesEmptyStateBucketAfterFinish() throws Exception {
        AbsStreamChannel.ActiveAiCardCache cache = new AbsStreamChannel.ActiveAiCardCache();

        cache.startAiCard("robot-1", "user-1", "card-1");
        cache.finishAiCard("robot-1", "user-1", "card-1");

        assertThat(cache.getActiveAiCard("robot-1", "user-1")).isNull();
        assertThat(rawCache(cache)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, AbsStreamChannel.AiCardState>> rawCache(
            AbsStreamChannel.ActiveAiCardCache cache) throws Exception {
        Field field = AbsStreamChannel.ActiveAiCardCache.class.getDeclaredField("aiCardStatus");
        field.setAccessible(true);
        return (Map<String, Map<String, AbsStreamChannel.AiCardState>>) field.get(cache);
    }
}
