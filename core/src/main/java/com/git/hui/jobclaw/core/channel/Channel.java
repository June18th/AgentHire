package com.git.hui.jobclaw.core.channel;

/**
 *
 * @author YiHui
 * @date 2026/4/8
 */
public interface Channel {
    default String name() {
        return this.getClass().getSimpleName();
    }

    /**
     * 启动通道，用于轮询获取消息
     */
    void start();

    /**
     * 停止通道
     */
    void stop();

    /**
     * 通道是否运行中
     */
    boolean isRunning();

    /**
     * 发送消息
     *
     * @param msg
     * @return
     */
    boolean send(ChannelResponseMessage msg);

    /**
     * 是否支持流式输出
     *
     * @return
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 获取流式输出回调接口
     *
     * @param chatId
     * @return
     */
    default StreamCallback streamingCallback(String chatId) {
        return null;
    }

    /**
     * 流式输出回调接口（基础版本，仅支持文本内容）
     */
    @FunctionalInterface
    interface StreamCallback {
        /**
         * 当接收到流式内容块时调用
         *
         * @param content 内容块
         */
        void onChunk(String content);
    }

}
