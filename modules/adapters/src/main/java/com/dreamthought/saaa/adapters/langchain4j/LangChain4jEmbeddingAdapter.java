package com.dreamthought.saaa.adapters.langchain4j;

import com.dreamthought.saaa.deterministic.SemanticEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Keeps all LangChain4j embedding types at the provider adapter boundary. */
public final class LangChain4jEmbeddingAdapter implements SemanticEmbeddingModel {
    private final EmbeddingModel model;
    private final String modelId;
    private final int dimensions;

    public static LangChain4jEmbeddingAdapter openAiCompatible(EmbeddingEndpointConfig config) {
        var model = OpenAiEmbeddingModel.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .modelName(config.modelId())
                .dimensions(config.dimensions())
                .timeout(Duration.ofSeconds(30))
                .maxRetries(1)
                .build();
        return new LangChain4jEmbeddingAdapter(model, config.modelId(), config.dimensions());
    }

    LangChain4jEmbeddingAdapter(EmbeddingModel model, String modelId, int dimensions) {
        this.model = Objects.requireNonNull(model, "model");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.dimensions = dimensions;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<Float> embed(String semanticText) {
        return List.copyOf(model.embed(semanticText).content().vectorAsList());
    }
}
