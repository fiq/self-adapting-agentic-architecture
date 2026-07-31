package com.dreamthought.saaa.adapters.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OpenAiCompatibleChatModelFactoryTest {
    private final OpenAiCompatibleChatModelFactory factory = new OpenAiCompatibleChatModelFactory();

    @Test
    void buildsAnOpenAiChatModelFromTheSaaaEnvironmentContract() {
        var model = factory.fromEnvironment(Map.of(
                OpenAiCompatibleChatModelFactory.BASE_URL_ENV, "http://127.0.0.1:11434/v1",
                OpenAiCompatibleChatModelFactory.API_KEY_ENV, "test-key",
                OpenAiCompatibleChatModelFactory.MODEL_NAME_ENV, "test-model"
        ));

        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void requiresBaseUrlApiKeyAndModelName() {
        assertThatThrownBy(() -> factory.fromEnvironment(Map.of(
                OpenAiCompatibleChatModelFactory.BASE_URL_ENV, "http://127.0.0.1:11434/v1",
                OpenAiCompatibleChatModelFactory.API_KEY_ENV, "test-key"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("missing required environment variable: SAAA_MODEL_NAME");
    }

    @Test
    void rejectsBlankValuesWithoutEchoingTheApiKey() {
        assertThatThrownBy(() -> factory.fromEnvironment(Map.of(
                OpenAiCompatibleChatModelFactory.BASE_URL_ENV, "http://127.0.0.1:11434/v1",
                OpenAiCompatibleChatModelFactory.API_KEY_ENV, "secret-key-value",
                OpenAiCompatibleChatModelFactory.MODEL_NAME_ENV, " "
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("environment variable must not be blank: SAAA_MODEL_NAME")
                .hasMessageNotContaining("secret-key-value");
    }
}
