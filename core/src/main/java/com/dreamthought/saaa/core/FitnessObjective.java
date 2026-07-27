package com.dreamthought.saaa.core;

public record FitnessObjective(String id, double weight) {
    public FitnessObjective {
        id = Require.nonBlank(id, "id");
        if (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be a finite fraction between 0.0 and 1.0");
        }
    }
}
