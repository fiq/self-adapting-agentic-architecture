package com.dreamthought.saaa.domain;

import java.util.Objects;

/** Provider and model observed for a usage measurement's price identity. */
public record PriceIdentity(String provider, String model) {
    public PriceIdentity {
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
    }

    private static String requireText(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
