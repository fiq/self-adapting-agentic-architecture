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
        var model = factory.fromConfig(new ModelEndpointConfig(
                "http://127.0.0.1:11434/v1",
                "test-key",
                "test-model"));

        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void requiresBaseUrlApiKeyAndModelName() {
        var source = new SmallRyeModelEndpointConfigSource(Map.of(
                ModelEndpointConfig.BASE_URL_PROPERTY, "http://127.0.0.1:11434/v1",
                ModelEndpointConfig.API_KEY_PROPERTY, "test-key"
        ));

        assertThatThrownBy(source::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "missing required configuration property: saaa.model.name "
                                + "(environment: SAAA_MODEL_NAME)");
    }

    @Test
    void rejectsBlankValuesWithoutEchoingTheApiKey() {
        assertThatThrownBy(() -> new ModelEndpointConfig(
                "http://127.0.0.1:11434/v1",
                "secret-key-value",
                " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("configuration property must not be blank: saaa.model.name")
                .hasMessageNotContaining("secret-key-value");
    }
}
