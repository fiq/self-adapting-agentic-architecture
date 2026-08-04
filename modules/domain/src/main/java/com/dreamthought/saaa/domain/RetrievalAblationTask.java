package com.dreamthought.saaa.domain;

public record RetrievalAblationTask(String id, String description, double baselineFitness) {
    public RetrievalAblationTask {
        id = Require.nonBlank(id, "id");
        description = Require.nonBlank(description, "description");
        if (!Double.isFinite(baselineFitness)) {
            throw new IllegalArgumentException("baselineFitness must be finite");
        }
    }
}
