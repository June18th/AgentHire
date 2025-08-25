package com.git.hui.offer.gather.service.ai;

import com.git.hui.offer.components.bizexception.BizException;
import com.git.hui.offer.components.bizexception.StatusEnum;
import com.git.hui.offer.constants.gather.GatherModelEnum;
import com.git.hui.offer.constants.gather.GatherModelTypeEnum;
import com.git.hui.offer.gather.model.ModelSelectReq;
import com.git.hui.offer.gather.service.ai.impl.AbsOcChatModelApi;
import com.git.hui.offer.util.json.StringBaseEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author YiHui
 * @date 2025/7/18
 */
@Slf4j
@Component
public class OcAiModelContext {

    private final List<OcChatModelApi> list;

    /**
     * 注入
     *
     * @param list
     */
    public OcAiModelContext(List<OcChatModelApi> list) {
        this.list = list;
    }

    public ChatClient chatClient(ModelSelectReq req) {
        for (OcChatModelApi api : list) {
            ChatClient client = api.chatClient(req);
            if (client != null) {
                log.info("当前选中的模型为：{}", api.modelEnum());
                return client;
            }
        }
        throw new BizException(StatusEnum.MODEL_MISMATCH_SUPPORT);
    }

    public Pair<ChatModel, String> model(ModelSelectReq req) {
        for (OcChatModelApi api : list) {
            Pair<ChatModel, String> model = api.model(req);
            if (model != null) {
                log.info("当前选中的模型为：{}", api.modelEnum());
                return model;
            }
        }
        throw new BizException(StatusEnum.MODEL_MISMATCH_SUPPORT);
    }


    /**
     * 默认使用的大模型
     */
    @Value("${oc.mainModel:ZhiPu}")
    private String mainModel;

    /**
     * 获取默认的聊天模型
     *
     * @return
     */
    public ChatClient getMainChatClient() {
        try {
            return chatClient(new ModelSelectReq(StringBaseEnum.getEnumByCode(GatherModelEnum.class, mainModel), GatherModelTypeEnum.CHAT_MODEL));
        } catch (Exception e) {
            return ((AbsOcChatModelApi) list.get(0)).chatClient();
        }
    }
}
