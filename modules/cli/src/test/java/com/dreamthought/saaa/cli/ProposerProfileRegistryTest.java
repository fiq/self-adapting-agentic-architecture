package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.adapters.langchain4j.OpenAiCompatibleChatModelFactory;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProposerProfileRegistryTest {
    private final ProposerProfileRegistry registry = new ProposerProfileRegistry(Map.of(
            OpenAiCompatibleChatModelFactory.BASE_URL_ENV, "http://127.0.0.1:11434/v1",
            OpenAiCompatibleChatModelFactory.API_KEY_ENV, "test-key",
            OpenAiCompatibleChatModelFactory.MODEL_NAME_ENV, "test-model"
    ));

    @Test
    void resolvesKnownProfileAndListsKnownNamesOnFailure() {
        assertThat(registry.knownNames()).containsExactly("fixture", "openai-compatible");
        assertThat(registry.resolve("fixture", Path.of("some/folder"))).isNotNull();
        assertThat(registry.resolve("openai-compatible", Path.of("some/folder"))).isNotNull();

        assertThatThrownBy(() -> registry.resolve("gpt-cloud", Path.of("some/folder")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown proposer profile: gpt-cloud; known profiles: fixture, openai-compatible");
    }

    @Test
    void openAiCompatibleProfileUsesTheSaaaEnvironmentContract() {
        var registry = new ProposerProfileRegistry(Map.of(
                OpenAiCompatibleChatModelFactory.BASE_URL_ENV, "http://127.0.0.1:11434/v1",
                OpenAiCompatibleChatModelFactory.API_KEY_ENV, "test-key"
        ));

        assertThatThrownBy(() -> registry.resolve("openai-compatible", Path.of("some/folder")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("missing required environment variable: SAAA_MODEL_NAME");
    }
}
