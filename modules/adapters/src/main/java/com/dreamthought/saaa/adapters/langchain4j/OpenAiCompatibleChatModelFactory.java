package com.dreamthought.saaa.adapters.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public final class OpenAiCompatibleChatModelFactory {
    private final SmallRyeModelEndpointConfigSource configSource;

    public OpenAiCompatibleChatModelFactory() {
        this(new SmallRyeModelEndpointConfigSource());
    }

    OpenAiCompatibleChatModelFactory(SmallRyeModelEndpointConfigSource configSource) {
        this.configSource = configSource;
    }

    public ChatModel fromApplicationConfig() {
        return fromConfig(configSource.load());
    }

    public ChatModel fromConfig(ModelEndpointConfig config) {
        return OpenAiChatModel.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .modelName(config.modelName())
                .build();
    }
}
