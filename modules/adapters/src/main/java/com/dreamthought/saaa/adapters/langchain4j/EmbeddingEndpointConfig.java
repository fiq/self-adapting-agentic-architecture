package com.dreamthought.saaa.adapters.langchain4j;

public record EmbeddingEndpointConfig(String baseUrl, String apiKey, String modelId, int dimensions) {
    public static final String BASE_URL_PROPERTY = "saaa.embedding.base-url";
    public static final String API_KEY_PROPERTY = "saaa.embedding.api-key";
    public static final String MODEL_ID_PROPERTY = "saaa.embedding.model-id";
    public static final String DIMENSIONS_PROPERTY = "saaa.embedding.dimensions";

    public EmbeddingEndpointConfig {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()
                || modelId == null || modelId.isBlank() || dimensions < 1) {
            throw new IllegalStateException("embedding endpoint requires base URL, API key, model ID and dimensions");
        }
    }
}
