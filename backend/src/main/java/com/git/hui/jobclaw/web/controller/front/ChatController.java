package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.web.model.req.ChatMessageReq;
import com.git.hui.jobclaw.web.model.res.ChatAgentVo;
import com.git.hui.jobclaw.web.model.res.ChatMessageVo;
import com.git.hui.jobclaw.web.model.res.ChatStreamVo;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Web chat entry point. It adapts HTTP requests to the existing Agent and ChatMemory pipeline.
 *
 * @author Codex
 */
@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping(path = "/api/chat")
public class ChatController {
    private static final String WEB_CHANNEL = "web";

    private final AgentRegistry agentRegistry;

    public ChatController(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @RequestMapping(path = "agents")
    public List<ChatAgentVo> agents() {
        String userId = currentUserId();
        return agentRegistry.getAllAgents(userId).stream()
                .map(agent -> new ChatAgentVo(
                        agent.getAgentIntro().getAgentId(),
                        agent.getAgentIntro().getIntro(),
                        agent.getAgentIntro().getDescription()))
                .toList();
    }

    @RequestMapping(path = "send")
    public ChatMessageVo send(@RequestBody ChatMessageReq req) {
        Assert.notNull(req, "请求不能为空");
        Assert.hasText(req.message(), "消息不能为空");

        ChatRequestContext context = buildContext(req, false);
        String content = context.agent().process(context.conversationInfo(), context.message());
        return new ChatMessageVo(
                context.conversationId(),
                context.conversationInfo().genId(),
                context.agentId(),
                content);
    }

    @RequestMapping(path = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamVo>> stream(@RequestBody ChatMessageReq req) {
        Assert.notNull(req, "请求不能为空");
        Assert.hasText(req.message(), "消息不能为空");

        ChatRequestContext context = buildContext(req, true);
        ServerSentEvent<ChatStreamVo> ready = event("ready", ChatStreamVo.ready(
                context.conversationId(),
                context.conversationInfo().genId(),
                context.agentId()));

        Flux<ServerSentEvent<ChatStreamVo>> chunks = context.agent()
                .stream(context.conversationInfo(), context.message())
                .map(cell -> event("chunk", ChatStreamVo.chunk(context.conversationId(), context.agentId(), cell)))
                .filter(item -> item.data() != null && item.data().hasPayload());

        ServerSentEvent<ChatStreamVo> done = event("done", ChatStreamVo.done(
                context.conversationId(),
                context.conversationInfo().genId(),
                context.agentId()));

        return Flux.concat(Flux.just(ready), chunks, Flux.just(done))
                .onErrorResume(error -> Flux.just(event("error", ChatStreamVo.error(
                        context.conversationId(),
                        context.agentId(),
                        StringUtils.hasText(error.getMessage()) ? error.getMessage() : "流式对话失败"))));
    }

    private BizAgent resolveAgent(String agentId, String userId) {
        BizAgent agent = StringUtils.hasText(agentId)
                ? agentRegistry.getAgent(agentId.trim()).orElse(null)
                : agentRegistry.getDefaultAgent().orElse(null);
        Assert.notNull(agent, "Agent 不存在或未初始化");

        boolean allowed = agentRegistry.getAllAgents(userId).stream()
                .anyMatch(item -> item.getAgentIntro().getAgentId().equals(agent.getAgentIntro().getAgentId()));
        Assert.isTrue(allowed, "当前用户无权使用该 Agent");
        return agent;
    }

    private String currentUserId() {
        Long userId = ReqInfoContext.getReqInfo() == null ? null : ReqInfoContext.getReqInfo().getUserId();
        Assert.notNull(userId, "未登录");
        return String.valueOf(userId);
    }

    private ChatRequestContext buildContext(ChatMessageReq req, boolean stream) {
        String userId = currentUserId();
        BizAgent agent = resolveAgent(req.agentId(), userId);
        String agentId = agent.getAgentIntro().getAgentId();
        String conversationId = StringUtils.hasText(req.conversationId())
                ? req.conversationId().trim()
                : UUID.randomUUID().toString();

        UserConversationInfo conversationInfo = new UserConversationInfo(userId, WEB_CHANNEL, conversationId, false)
                .setAgent(agentId);
        ChannelReceiveMessage message = ChannelReceiveMessage.builder()
                .msgId(UUID.randomUUID().toString())
                .channel(WEB_CHANNEL)
                .fromUserId(userId)
                .jobClawUserId(userId)
                .message(req.message().trim())
                .passThrough(Map.of("source", "web-chat"))
                .stream(stream)
                .groupTalk(false)
                .build();
        return new ChatRequestContext(agent, agentId, conversationId, conversationInfo, message);
    }

    private ServerSentEvent<ChatStreamVo> event(String event, ChatStreamVo data) {
        return ServerSentEvent.<ChatStreamVo>builder()
                .event(event)
                .data(data)
                .build();
    }

    private record ChatRequestContext(
            BizAgent agent,
            String agentId,
            String conversationId,
            UserConversationInfo conversationInfo,
            ChannelReceiveMessage message) {
    }
}
