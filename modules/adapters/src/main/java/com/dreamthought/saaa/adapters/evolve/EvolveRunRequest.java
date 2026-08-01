package com.dreamthought.saaa.adapters.evolve;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record EvolveRunRequest(
        Path targetFolder,
        String profile,
        String workflowFile,
        List<String> behaviourCases,
        int maxLines
) {
    public EvolveRunRequest {
        targetFolder = Objects.requireNonNull(targetFolder, "targetFolder");
        profile = requireNonBlank(profile, "profile");
        workflowFile = requireNonBlank(workflowFile, "workflowFile");
        behaviourCases = List.copyOf(Objects.requireNonNull(behaviourCases, "behaviourCases"));
        if (behaviourCases.isEmpty()) {
            throw new IllegalArgumentException("at least one behaviour case is required");
        }
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be positive");
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
