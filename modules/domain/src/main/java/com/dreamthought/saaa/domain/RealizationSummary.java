package com.dreamthought.saaa.domain;

/** How much a candidate's realization actually changed, measured from its Git diff. */
public record RealizationSummary(int filesChanged, int linesChanged) {
    public RealizationSummary {
        if (filesChanged < 0) {
            throw new IllegalArgumentException("filesChanged must not be negative");
        }
        if (linesChanged < 0) {
            throw new IllegalArgumentException("linesChanged must not be negative");
        }
    }
}
