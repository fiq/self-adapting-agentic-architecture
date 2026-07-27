package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.CheckStatus;
import java.util.Objects;

/**
 * One required deterministic behavior case observed on the candidate phenotype. These are what the
 * candidate must do, as distinct from the checks that say the candidate builds.
 */
public record BehaviorCaseEvidence(String id, CheckStatus status, String summary) {
    public BehaviorCaseEvidence {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        status = Objects.requireNonNull(status, "status");
        Objects.requireNonNull(summary, "summary");
        if (summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
    }

    public static BehaviorCaseEvidence passed(String id, String summary) {
        return new BehaviorCaseEvidence(id, CheckStatus.PASSED, summary);
    }

    public static BehaviorCaseEvidence failed(String id, String summary) {
        return new BehaviorCaseEvidence(id, CheckStatus.FAILED, summary);
    }
}
