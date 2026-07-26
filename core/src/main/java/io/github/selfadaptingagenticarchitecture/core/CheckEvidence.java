package io.github.selfadaptingagenticarchitecture.core;

import java.util.Objects;

public record CheckEvidence(String name, CheckStatus status, String summary) {
    public CheckEvidence {
        name = Require.nonBlank(name, "name");
        status = Objects.requireNonNull(status, "status");
        summary = Require.nonBlank(summary, "summary");
    }

    public static CheckEvidence passed(String name, String summary) {
        return new CheckEvidence(name, CheckStatus.PASSED, summary);
    }

    public static CheckEvidence failed(String name, String summary) {
        return new CheckEvidence(name, CheckStatus.FAILED, summary);
    }
}
