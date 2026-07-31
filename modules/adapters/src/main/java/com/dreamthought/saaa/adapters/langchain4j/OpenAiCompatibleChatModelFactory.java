package com.dreamthought.saaa.adapters.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import com.dreamthought.saaa.deterministic.MutationProposer;
import java.util.Map;
import java.util.Objects;

public final class OpenAiCompatibleChatModelFactory {
    public static final String BASE_URL_ENV = "SAAA_MODEL_BASE_URL";
    public static final String API_KEY_ENV = "SAAA_MODEL_API_KEY";
    public static final String MODEL_NAME_ENV = "SAAA_MODEL_NAME";

    public ChatModel fromProcessEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public MutationProposer mutationProposerFromEnvironment(Map<String, String> environment) {
        return LangChain4jMutationProposalAdapter.from(fromEnvironment(environment));
    }

    public ChatModel fromEnvironment(Map<String, String> environment) {
        var config = OpenAiCompatibleChatModelConfig.fromEnvironment(environment);
        return OpenAiChatModel.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .modelName(config.modelName())
                .build();
    }

    private static final class OpenAiCompatibleChatModelConfig {
        private final String baseUrl;
        private final String apiKey;
        private final String modelName;

        private OpenAiCompatibleChatModelConfig(String baseUrl, String apiKey, String modelName) {
            requireNonBlank(baseUrl, BASE_URL_ENV);
            requireNonBlank(apiKey, API_KEY_ENV);
            requireNonBlank(modelName, MODEL_NAME_ENV);
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.modelName = modelName;
        }

        private static OpenAiCompatibleChatModelConfig fromEnvironment(Map<String, String> environment) {
            Objects.requireNonNull(environment, "environment");
            return new OpenAiCompatibleChatModelConfig(
                    requireEnvironmentValue(environment, BASE_URL_ENV),
                    requireEnvironmentValue(environment, API_KEY_ENV),
                    requireEnvironmentValue(environment, MODEL_NAME_ENV)
            );
        }

        private String baseUrl() {
            return baseUrl;
        }

        private String apiKey() {
            return apiKey;
        }

        private String modelName() {
            return modelName;
        }

        private static String requireEnvironmentValue(Map<String, String> environment, String name) {
            String value = environment.get(name);
            if (value == null) {
                throw new IllegalStateException("missing required environment variable: " + name);
            }
            return value;
        }

        private static void requireNonBlank(String value, String name) {
            if (value.isBlank()) {
                throw new IllegalStateException("environment variable must not be blank: " + name);
            }
        }
    }
}
