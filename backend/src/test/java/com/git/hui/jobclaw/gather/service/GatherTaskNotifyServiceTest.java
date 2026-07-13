package com.git.hui.jobclaw.gather.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GatherTaskNotifyServiceTest {

    @Test
    void notifiesEverySubscriberOnceAndCompletesThem() throws Exception {
        GatherTaskNotifyService service = new GatherTaskNotifyService();
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        subscribers(service).put(9L, new CopyOnWriteArrayList<>(java.util.List.of(first, second)));
        Map<String, Object> payload = Map.of("cmd", "success", "taskId", 9L);

        service.notifyTaskUpdate(9L, payload);
        service.notifyTaskUpdate(9L, Map.of("cmd", "duplicate"));

        verify(first).send(payload);
        verify(second).send(payload);
        verify(first).complete();
        verify(second).complete();
        assertThat(subscribers(service)).doesNotContainKey(9L);
    }

    @Test
    void ignoresUnknownOrNullTask() throws Exception {
        GatherTaskNotifyService service = new GatherTaskNotifyService();
        SseEmitter emitter = mock(SseEmitter.class);
        subscribers(service).put(4L, new CopyOnWriteArrayList<>(java.util.List.of(emitter)));

        service.notifyTaskUpdate(null, Map.of());
        service.notifyTaskUpdate(5L, Map.of());

        verify(emitter, never()).send(org.mockito.ArgumentMatchers.any(Object.class));
        assertThat(subscribers(service)).containsKey(4L);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> subscribers(
            GatherTaskNotifyService service) throws Exception {
        Field field = GatherTaskNotifyService.class.getDeclaredField("subscribers");
        field.setAccessible(true);
        return (ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>) field.get(service);
    }
}
