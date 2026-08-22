package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessSignalId;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts the loop's evidence into phenotype evidence and delegates to the hard-gated scorer.
 *
 * <p>Every derivation here is deterministic and evidence-only. The derivations are deliberately
 * crude for a first slice; the one that carries real signal is parsimony, because it reads the
 * realized diff and therefore differs between candidates.
 */
public final class PhenotypeBridgeScorer implements FitnessScorer {
    private final RealizationInspector inspector;
    private final ScoringConfig config;
    private final PhenotypeFitnessScorer delegate = new PhenotypeFitnessScorer();

    public PhenotypeBridgeScorer(RealizationInspector inspector, ScoringConfig config) {
        this.inspector = Objects.requireNonNull(inspector, "inspector");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public FitnessResult score(Candidate candidate, EvaluationEvidence evidence) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(evidence, "evidence");

        // Merge rather than overwrite: two check entries can share a name, and keeping the last one
        // seen would let a passing entry hide a failing one for the same declared case.
        Map<String, BehaviorCaseEvidence> observed = new LinkedHashMap<>();
        evidence.checks().stream()
                .filter(check -> config.behaviorCaseNames().contains(check.name()))
                .forEach(check -> observed.merge(
                        check.name(),
                        toBehaviorCase(check),
                        (existing, latest) -> existing.status() == CheckStatus.FAILED ? existing : latest));

        // Fail closed on any declared case that produced no evidence. Filtering alone would drop it
        // silently and let the gate pass on the cases that did report, which is the same weakness as
        // a declared-but-unenforced contract gate: the required behaviour was never shown to hold.
        List<BehaviorCaseEvidence> behaviorCases = config.behaviorCaseNames().stream()
                .map(name -> observed.getOrDefault(
                        name, BehaviorCaseEvidence.failed(name, "no check evidence was produced for this case")))
                .toList();

        RealizationSummary realization = Objects.requireNonNull(
                inspector.inspect(candidate), "realization summary");

        Map<String, Double> objectives = new LinkedHashMap<>();
        objectives.put(FitnessSignalId.objective("task_success").canonical(), passedFraction(behaviorCases));
        objectives.put(FitnessSignalId.objective("reliability").canonical(), allChecksRan(evidence) ? 1.0 : 0.0);
        objectives.put(
                FitnessSignalId.objective("cost_latency_budget").canonical(), budgetScore(evidence.benchmarks()));
        objectives.put(FitnessSignalId.objective("behavioral_safety").canonical(), 1.0);
        objectives.put(FitnessSignalId.objective("parsimony").canonical(), parsimony(realization));

        return delegate.score(
                candidate, new PhenotypeEvidence(evidence, behaviorCases, objectives, realization));
    }

    private static BehaviorCaseEvidence toBehaviorCase(CheckEvidence check) {
        if (check.status() == CheckStatus.PASSED) {
            return BehaviorCaseEvidence.passed(check.name(), check.summary());
        }
        return BehaviorCaseEvidence.failed(check.name(), check.summary());
    }

    private static double passedFraction(List<BehaviorCaseEvidence> behaviorCases) {
        if (behaviorCases.isEmpty()) {
            return 0.0;
        }
        long passed = behaviorCases.stream().filter(c -> c.status() == CheckStatus.PASSED).count();
        return (double) passed / behaviorCases.size();
    }

    /** Timeout is structured evidence; summaries remain candidate-controlled diagnostic text. */
    private static boolean allChecksRan(EvaluationEvidence evidence) {
        return evidence.checks().stream().noneMatch(check -> check.status() == CheckStatus.TIMED_OUT);
    }

    /**
     * A benchmark definition whose include pattern matches more than one benchmark yields evidence
     * named {@code definition:fully.qualified.Benchmark}, so an exact lookup misses every result and
     * the objective silently stays at 1.0 for a run the caller believed it had budgeted. The declared
     * budget therefore applies to the definition and to every result derived from it, and because
     * {@code budgetScore} keeps the worst ratio, the slowest matched benchmark is the one that gates.
     */
    private Double budgetFor(String evidenceName) {
        Double exact = config.benchmarkBudgets().get(evidenceName);
        if (exact != null) {
            return exact;
        }
        // The appended suffix is a Java fully-qualified name and therefore contains no colon, so
        // the last colon is always the separator. Splitting on the first would mangle a definition
        // name that itself contains one, and silently miss its budget — the bug this exists to fix.
        int separator = evidenceName.lastIndexOf(BenchmarkRunner.DERIVED_NAME_SEPARATOR);
        return separator <= 0 ? null : config.benchmarkBudgets().get(evidenceName.substring(0, separator));
    }

    private double budgetScore(List<BenchmarkEvidence> benchmarks) {
        double worst = 1.0;
        for (BenchmarkEvidence benchmark : benchmarks) {
            Double budget = budgetFor(benchmark.name());
            if (budget == null || benchmark.value() <= 0.0) {
                continue;
            }
            worst = Math.min(worst, clamp(budget / benchmark.value()));
        }
        return worst;
    }

    private double parsimony(RealizationSummary realization) {
        return clamp(1.0 - ((double) realization.linesChanged() / config.maxLinesChanged()));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
