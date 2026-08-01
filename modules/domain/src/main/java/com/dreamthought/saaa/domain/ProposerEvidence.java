package com.dreamthought.saaa.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ProposerEvidence(String proposerId, Map<String, String> attributes) {
    public ProposerEvidence {
        proposerId = Require.nonBlank(proposerId, "proposerId");
        if (!proposerId.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("proposerId must be a TOON-safe identifier: " + proposerId);
        }
        attributes = new LinkedHashMap<>(Objects.requireNonNull(attributes, "attributes"));
        attributes.forEach((key, value) -> {
            Require.nonBlank(key, "attribute key");
            if (!key.matches("[a-z][a-z0-9_]*")) {
                throw new IllegalArgumentException("attribute key must be a TOON-safe snake_case name: " + key);
            }
            Require.nonBlank(value, "attribute value");
        });
        attributes = Collections.unmodifiableMap(attributes);
    }

    public static ProposerEvidence of(String proposerId, Map<String, String> attributes) {
        return new ProposerEvidence(proposerId, new LinkedHashMap<>(attributes));
    }
}
