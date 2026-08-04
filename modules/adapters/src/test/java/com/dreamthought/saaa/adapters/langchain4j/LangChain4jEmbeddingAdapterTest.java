package com.dreamthought.saaa.adapters.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LangChain4jEmbeddingAdapterTest {
    @Test
    void mapsProviderEmbeddingWithoutLeakingLangChain4jTypes() {
        EmbeddingModel provider = new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                return Response.from(Embedding.from(new float[] {0.25f, 0.75f}));
            }
        };
        var adapter = new LangChain4jEmbeddingAdapter(provider, "fixture-model", 2);

        assertThat(adapter.modelId()).isEqualTo("fixture-model");
        assertThat(adapter.dimensions()).isEqualTo(2);
        assertThat(adapter.embed("semantic evidence")).containsExactly(0.25f, 0.75f);
        assertThat(adapter).isInstanceOf(com.dreamthought.saaa.deterministic.SemanticEmbeddingModel.class);
    }
}
