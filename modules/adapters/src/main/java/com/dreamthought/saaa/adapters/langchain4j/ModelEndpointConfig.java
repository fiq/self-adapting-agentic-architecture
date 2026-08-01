package com.dreamthought.saaa.adapters.langchain4j;

public record ModelEndpointConfig(String baseUrl, String apiKey, String modelName) {
    public static final String BASE_URL_PROPERTY = "saaa.model.base-url";
    public static final String API_KEY_PROPERTY = "saaa.model.api-key";
    public static final String MODEL_NAME_PROPERTY = "saaa.model.name";
    public static final String BASE_URL_ENV = "SAAA_MODEL_BASE_URL";
    public static final String API_KEY_ENV = "SAAA_MODEL_API_KEY";
    public static final String MODEL_NAME_ENV = "SAAA_MODEL_NAME";

    public ModelEndpointConfig {
        baseUrl = requireNonBlank(baseUrl, BASE_URL_PROPERTY);
        apiKey = requireNonBlank(apiKey, API_KEY_PROPERTY);
        modelName = requireNonBlank(modelName, MODEL_NAME_PROPERTY);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null) {
            throw new IllegalStateException("missing required configuration property: " + name);
        }
        if (value.isBlank()) {
            throw new IllegalStateException("configuration property must not be blank: " + name);
        }
        return value;
    }
}
