package io.github.selfadaptingagenticarchitecture.core;

import java.util.Objects;

final class Require {
    private Require() {
    }

    static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
