package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.constants.user.LoginConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Admin 采集任务 SSE 通知：前端提交投料后可订阅单个 taskId 的完成事件。
 */
@Slf4j
@Service
public class GatherTaskNotifyService {

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long taskId) {
        SseEmitter emitter = new SseEmitter(LoginConstants.SSE_EXPIRE_TIME);
        subscribers.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(error -> remove(taskId, emitter));
        return emitter;
    }

    public void notifyTaskUpdate(Long taskId, Map<String, Object> payload) {
        if (taskId == null) {
            return;
        }
        List<SseEmitter> emitters = subscribers.remove(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(payload);
                emitter.complete();
            } catch (IOException | IllegalStateException ex) {
                log.debug("推送采集任务 SSE 失败: taskId={}", taskId, ex);
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                    // ignore secondary close errors
                }
            }
        }
    }

    private void remove(Long taskId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(taskId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(taskId, emitters);
        }
    }
}
