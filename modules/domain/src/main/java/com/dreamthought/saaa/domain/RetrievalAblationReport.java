package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;

public record RetrievalAblationReport(
        String experimentId,
        List<RetrievalAblationTask> tasks,
        List<RetrievalAttemptMetrics> attempts,
        List<RetrievalTreatmentSummary> treatments
) {
    public RetrievalAblationReport {
        experimentId = Require.nonBlank(experimentId, "experimentId");
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
        treatments = List.copyOf(Objects.requireNonNull(treatments, "treatments"));
    }
}
