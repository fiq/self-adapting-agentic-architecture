package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.domain.MutationContract;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.util.Optional;
import java.util.Set;

public record EvolveRunRequest(
        Path targetFolder,
        String profile,
        String workflowFile,
        List<String> behaviourCases,
        int maxLines,
        RetrievalMode retrievalMode,
        String task,
        Optional<String> runId,
        Map<String, Double> benchmarkBudgets,
        Optional<MutationContract> contract,
        // Ordered, like behaviourCases: probes are executed in this order and their check evidence is
        // recorded in it. A Set here would hand execution an order that varies between JVM runs, so
        // the same declaration would produce a differently ordered audit trail each time.
        List<String> safetyProbes,
        // How many times each behaviour case runs. The first run gates; the rest grade reliability.
        int reliabilityRuns
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
                Map.of(), Optional.empty(), List.of(), 1);
    }

    public EvolveRunRequest(
            Path targetFolder, String profile, String workflowFile, List<String> behaviourCases,
            int maxLines, RetrievalMode retrievalMode, String task) {
        this(targetFolder, profile, workflowFile, behaviourCases, maxLines, retrievalMode, task, Optional.empty(),
                Map.of(), Optional.empty(), List.of(), 1);
    }

    public EvolveRunRequest(
            Path targetFolder, String profile, String workflowFile, List<String> behaviourCases,
            int maxLines, RetrievalMode retrievalMode, String task, Optional<String> runId) {
        this(targetFolder, profile, workflowFile, behaviourCases, maxLines, retrievalMode, task, runId,
                Map.of(), Optional.empty(), List.of(), 1);
    }

    /** Every prior caller keeps its behaviour: no contract declared means no declared gate. */
    public EvolveRunRequest(
            Path targetFolder, String profile, String workflowFile, List<String> behaviourCases,
            int maxLines, RetrievalMode retrievalMode, String task, Optional<String> runId,
            Map<String, Double> benchmarkBudgets) {
        this(targetFolder, profile, workflowFile, behaviourCases, maxLines, retrievalMode, task, runId,
                benchmarkBudgets, Optional.empty(), List.of(), 1);
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
        safetyProbes = List.copyOf(new java.util.LinkedHashSet<>(
                Objects.requireNonNull(safetyProbes, "safetyProbes")));
        if (reliabilityRuns < 1) {
            throw new IllegalArgumentException("reliabilityRuns must be at least 1");
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
