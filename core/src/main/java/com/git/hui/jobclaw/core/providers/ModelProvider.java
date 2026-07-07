package com.git.hui.jobclaw.core.providers;

import org.springframework.ai.model.Model;

/**
 * 大模型供应商策略接口。
 * <p>
 * 每一种 API 接入风格实现一个策略，例如 openai、zhipu、anthropic、ali。
 * 业务层只依赖这个接口，不直接感知具体厂商 SDK。
 *
 * @author YiHui
 * @date 2026/4/9
 */
public interface ModelProvider {
    /**
     * 模型接入的 API 风格，也是策略注册表中的唯一 key。
     *
     * @return 例如 openai、zhipu、anthropic、ali
     */
    String apiStyle();

    /**
     * 根据模型配置构建对应的 Spring AI Model。
     *
     * @param info 模型、密钥、地址、类型等运行时配置
     * @return 具体厂商 SDK 包装后的模型实例
     */
    Model model(ModelConfig.ModelInfo info);
}
