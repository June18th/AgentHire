package com.git.hui.jobclaw.core.agent;

/**
 * 大模型返回的基础信息
 * @author YiHui
 * @date 2026/4/16
 */
public record LlmRspCell(String thinking, String content, String tool) {
}
