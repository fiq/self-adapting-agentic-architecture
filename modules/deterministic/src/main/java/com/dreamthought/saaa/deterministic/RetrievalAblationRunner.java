package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.RetrievalAblationReport;
import com.dreamthought.saaa.domain.RetrievalAblationTask;
import com.dreamthought.saaa.domain.RetrievalAttemptMetrics;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalTreatmentSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Executes an identical task list under explicit retrieval treatments and aggregates without claims. */
public final class RetrievalAblationRunner {
    private final AttemptRunner attemptRunner;

    public RetrievalAblationRunner(AttemptRunner attemptRunner) {
        this.attemptRunner = Objects.requireNonNull(attemptRunner, "attemptRunner");
    }

    public RetrievalAblationReport run(
            String experimentId,
            List<RetrievalAblationTask> tasks,
            List<RetrievalMode> modes,
            int attemptsPerTreatment) {
        if (tasks.isEmpty() || modes.isEmpty() || attemptsPerTreatment < 1) {
            throw new IllegalArgumentException("ablation needs tasks, modes and at least one attempt");
        }
        var observations = new ArrayList<RetrievalAttemptMetrics>();
        for (RetrievalMode mode : modes) {
            for (RetrievalAblationTask task : tasks) {
                for (int attempt = 1; attempt <= attemptsPerTreatment; attempt++) {
                    RetrievalAttemptMetrics result = attemptRunner.run(task, mode, attempt);
                    if (!result.taskId().equals(task.id()) || result.mode() != mode || result.attempt() != attempt) {
                        throw new IllegalStateException("attempt runner returned metrics for a different treatment");
                    }
                    observations.add(result);
                }
            }
        }
        List<RetrievalTreatmentSummary> summaries = modes.stream().distinct()
                .sorted().map(mode -> summarize(mode, observations)).toList();
        return new RetrievalAblationReport(experimentId, tasks, observations, summaries);
    }

    private static RetrievalTreatmentSummary summarize(
            RetrievalMode mode, List<RetrievalAttemptMetrics> all) {
        List<RetrievalAttemptMetrics> attempts = all.stream().filter(value -> value.mode() == mode).toList();
        int accepted = (int) attempts.stream().filter(RetrievalAttemptMetrics::accepted).count();
        double improvement = attempts.stream().filter(RetrievalAttemptMetrics::accepted)
                .mapToDouble(value -> Math.max(0, value.fitnessDelta())).sum();
        double cost = attempts.stream().mapToDouble(RetrievalAttemptMetrics::mutationCost).sum();
        int contextTokens = attempts.stream().mapToInt(RetrievalAttemptMetrics::evidenceContextTokens).sum();
        var firstAccepted = attempts.stream().collect(java.util.stream.Collectors.groupingBy(
                RetrievalAttemptMetrics::taskId)).values().stream()
                .mapToInt(taskAttempts -> taskAttempts.stream().filter(RetrievalAttemptMetrics::accepted)
                        .mapToInt(RetrievalAttemptMetrics::attempt).min().orElse(0))
                .filter(value -> value > 0).toArray();
        return new RetrievalTreatmentSummary(
                mode, attempts.size(), accepted,
                attempts.isEmpty() ? 0 : (double) accepted / attempts.size(),
                firstAccepted.length == 0 ? 0 : java.util.Arrays.stream(firstAccepted).average().orElse(0),
                attempts.stream().mapToDouble(RetrievalAttemptMetrics::fitness).max().orElse(0),
                improvement, cost, cost == 0 ? 0 : improvement / cost,
                accepted == 0 ? 0 : (double) contextTokens / accepted);
    }

    @FunctionalInterface
    public interface AttemptRunner {
        RetrievalAttemptMetrics run(RetrievalAblationTask task, RetrievalMode mode, int attempt);
    }
}
