package com.dreamthought.saaa.adapters.langchain4j;

import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.Map;
import java.util.Optional;

public final class SmallRyeModelEndpointConfigSource {
    private final ConfigLookup configLookup;

    public SmallRyeModelEndpointConfigSource() {
        this(new SmallRyeConfigBuilder().addDefaultSources().build()::getOptionalValue);
    }

    SmallRyeModelEndpointConfigSource(Map<String, String> properties) {
        this((name, type) -> Optional.ofNullable(properties.get(name)));
    }

    private SmallRyeModelEndpointConfigSource(ConfigLookup configLookup) {
        this.configLookup = configLookup;
    }

    public ModelEndpointConfig load() {
        return new ModelEndpointConfig(
                required(ModelEndpointConfig.BASE_URL_PROPERTY, ModelEndpointConfig.BASE_URL_ENV),
                required(ModelEndpointConfig.API_KEY_PROPERTY, ModelEndpointConfig.API_KEY_ENV),
                required(ModelEndpointConfig.MODEL_NAME_PROPERTY, ModelEndpointConfig.MODEL_NAME_ENV)
        );
    }

    private String required(String propertyName, String environmentName) {
        // Keep framework config names at this adapter boundary; callers use ModelEndpointConfig.
        return configLookup.get(propertyName, String.class)
                .orElseThrow(() -> new IllegalStateException(
                        "missing required configuration property: "
                                + propertyName
                                + " (environment: "
                                + environmentName
                                + ")"));
    }

    @FunctionalInterface
    private interface ConfigLookup {
        Optional<String> get(String name, Class<String> type);
    }
}
