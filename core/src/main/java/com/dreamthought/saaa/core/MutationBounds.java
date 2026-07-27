package com.dreamthought.saaa.core;

public record MutationBounds(
        int maxFilesChanged,
        int maxLinesChanged,
        boolean publicApiChange,
        boolean persistenceChange,
        boolean productionConfigChange
) {
    public MutationBounds {
        if (maxFilesChanged <= 0) {
            throw new IllegalArgumentException("maxFilesChanged must be positive");
        }
        if (maxLinesChanged <= 0) {
            throw new IllegalArgumentException("maxLinesChanged must be positive");
        }
    }
}
