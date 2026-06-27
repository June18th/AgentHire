package com.git.hui.jobclaw.gather.service.ai;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
import com.git.hui.jobclaw.constants.gather.GatherModelTypeEnum;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.gather.model.ModelSelectReq;
import com.git.hui.jobclaw.gather.service.ai.impl.AbsOcChatModelApi;
import com.git.hui.jobclaw.core.utils.json.StringBaseEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
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

    private final ModelProviders modelProviders;

    /**
     * 注入
     *
     * @param list
     */
    public OcAiModelContext(List<OcChatModelApi> list, ModelProviders modelProviders) {
        this.list = list;
        this.modelProviders = modelProviders;
    }

    public ChatClient chatClient(ModelSelectReq req) {
        if (req.providerModel()) {
            ModelConfig.ModelInfo modelInfo = modelProviders.getGlobalModelInfo(req.model());
            ChatModel chatModel = (ChatModel) modelProviders.getGlobalModel(req.model(), toModelType(req.type()));
            log.info("当前选中的后台模型为：{}#{}", modelInfo.getProvider(), modelInfo.getModelName());
            return ChatClient.builder(chatModel)
                    .defaultSystem(OcChatModelApi.GATHER_SYSTEM_PROMPT)
                    .defaultOptions(ChatOptions.builder().model(modelInfo.getModelName()).build())
                    .build();
        }
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
        if (req.providerModel()) {
            ModelConfig.ModelInfo modelInfo = modelProviders.getGlobalModelInfo(req.model());
            ChatModel chatModel = (ChatModel) modelProviders.getGlobalModel(req.model(), toModelType(req.type()));
            log.info("当前选中的后台模型为：{}#{}", modelInfo.getProvider(), modelInfo.getModelName());
            return Pair.of(chatModel, modelInfo.getModelName());
        }
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
    @Value("${jobclaw.mainModel:ZhiPu}")
    private String mainModel;

    /**
     * 获取默认的聊天模型
     *
     * @return
     */
    public ChatClient getMainChatClient() {
        try {
            return chatClient(ModelSelectReq.of(StringBaseEnum.getEnumByCode(GatherModelEnum.class, mainModel), GatherModelTypeEnum.CHAT_MODEL));
        } catch (Exception e) {
            return ((AbsOcChatModelApi) list.get(0)).chatClient();
        }
    }

    private ModelConfig.ModelType toModelType(GatherModelTypeEnum type) {
        return type == GatherModelTypeEnum.IMAGE_MODEL ? ModelConfig.ModelType.VISION : ModelConfig.ModelType.TEXT;
    }
}
