package com.dreamthought.saaa.adapters.evolve;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.util.Optional;

public record EvolveRunRequest(
        Path targetFolder,
        String profile,
        String workflowFile,
        List<String> behaviourCases,
        int maxLines,
        RetrievalMode retrievalMode,
        String task,
        Optional<String> runId,
        Map<String, Double> benchmarkBudgets
) {
    public EvolveRunRequest(
            Path targetFolder,
            String profile,
            String workflowFile,
            List<String> behaviourCases,
            int maxLines
    ) {
        this(
                targetFolder,
                profile,
                workflowFile,
                behaviourCases,
                maxLines,
                RetrievalMode.NONE,
                "Improve the target while preserving all declared behaviour cases",
                Optional.empty(),
                Map.of());
    }

    public EvolveRunRequest(
            Path targetFolder, String profile, String workflowFile, List<String> behaviourCases,
            int maxLines, RetrievalMode retrievalMode, String task) {
        this(targetFolder, profile, workflowFile, behaviourCases, maxLines, retrievalMode, task, Optional.empty(),
                Map.of());
    }

    public EvolveRunRequest(
            Path targetFolder, String profile, String workflowFile, List<String> behaviourCases,
            int maxLines, RetrievalMode retrievalMode, String task, Optional<String> runId) {
        this(targetFolder, profile, workflowFile, behaviourCases, maxLines, retrievalMode, task, runId, Map.of());
    }

    /**
     * @param benchmarkBudgets benchmark name to its budget in the benchmark's own unit, threaded
     *                         into {@code ScoringConfig} so {@code cost_latency_budget} has an
     *                         evidence source to compare against; empty means no budget is
     *                         configured and the objective stays at its {@code 1.0} starting point
     */
    public EvolveRunRequest {
        targetFolder = Objects.requireNonNull(targetFolder, "targetFolder");
        profile = requireNonBlank(profile, "profile");
        workflowFile = requireRelativeChildPath(requireNonBlank(workflowFile, "workflowFile"), "workflowFile");
        behaviourCases = List.copyOf(Objects.requireNonNull(behaviourCases, "behaviourCases"));
        if (behaviourCases.isEmpty()) {
            throw new IllegalArgumentException("at least one behaviour case is required");
        }
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be positive");
        }
        retrievalMode = Objects.requireNonNull(retrievalMode, "retrievalMode");
        task = requireNonBlank(task, "task");
        runId = Objects.requireNonNull(runId, "runId").map(value -> requireSafeId(value, "runId"));
        benchmarkBudgets = Map.copyOf(Objects.requireNonNull(benchmarkBudgets, "benchmarkBudgets"));
    }

    private static String requireSafeId(String value, String name) {
        String result = requireNonBlank(value, name);
        if (!result.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}")) {
            throw new IllegalArgumentException(name + " must contain only letters, digits, underscores or hyphens");
        }
        return result;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String requireRelativeChildPath(String value, String name) {
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || path.startsWith("..") || path.toString().equals("..")) {
            throw new IllegalArgumentException(name + " must stay inside targetFolder");
        }
        return path.toString();
    }
}
