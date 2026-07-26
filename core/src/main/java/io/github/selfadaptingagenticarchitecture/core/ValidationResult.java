package io.github.selfadaptingagenticarchitecture.core;

import java.util.List;
import java.util.Objects;

public record ValidationResult(boolean valid, List<String> messages) {
    public ValidationResult {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (!valid && messages.isEmpty()) {
            throw new IllegalArgumentException("invalid results must include at least one message");
        }
    }

    public static ValidationResult passed() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(String message) {
        return new ValidationResult(false, List.of(Require.nonBlank(message, "message")));
    }
}
