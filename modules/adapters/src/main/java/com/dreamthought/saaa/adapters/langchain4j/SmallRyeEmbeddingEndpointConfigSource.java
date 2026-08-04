package com.dreamthought.saaa.adapters.langchain4j;

import io.smallrye.config.SmallRyeConfigBuilder;

public final class SmallRyeEmbeddingEndpointConfigSource {
    public EmbeddingEndpointConfig load() {
        var config = new SmallRyeConfigBuilder().addDefaultSources().build();
        return new EmbeddingEndpointConfig(
                config.getValue(EmbeddingEndpointConfig.BASE_URL_PROPERTY, String.class),
                config.getValue(EmbeddingEndpointConfig.API_KEY_PROPERTY, String.class),
                config.getValue(EmbeddingEndpointConfig.MODEL_ID_PROPERTY, String.class),
                config.getValue(EmbeddingEndpointConfig.DIMENSIONS_PROPERTY, Integer.class));
    }
}
