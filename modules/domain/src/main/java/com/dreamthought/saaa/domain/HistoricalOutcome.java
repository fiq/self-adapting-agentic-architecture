package com.dreamthought.saaa.domain;

public record HistoricalOutcome(
        String evaluationId,
        String decision,
        double fitness,
        String summary
) {
    public HistoricalOutcome {
        evaluationId = Require.nonBlank(evaluationId, "evaluationId");
        decision = Require.nonBlank(decision, "decision");
        summary = Require.nonBlank(summary, "summary");
        if (!Double.isFinite(fitness) || fitness < 0.0 || fitness > 1.0) {
            throw new IllegalArgumentException("fitness must be a finite fraction");
        }
    }
}
