package io.github.selfadaptingagenticarchitecture.core;

public record BenchmarkEvidence(String name, double value, String unit) {
    public BenchmarkEvidence {
        name = Require.nonBlank(name, "name");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        unit = Require.nonBlank(unit, "unit");
    }

    public static BenchmarkEvidence measurement(String name, double value, String unit) {
        return new BenchmarkEvidence(name, value, unit);
    }
}
