package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
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
        objectives.put("task_success", passedFraction(behaviorCases));
        objectives.put("reliability", allChecksRan(evidence) ? 1.0 : 0.0);
        objectives.put("cost_latency_budget", budgetScore(evidence.benchmarks()));
        objectives.put("behavioral_safety", 1.0);
        objectives.put("parsimony", parsimony(realization));

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

    /** A check that timed out records "timed out" in its summary; anything else ran to completion. */
    private static boolean allChecksRan(EvaluationEvidence evidence) {
        return evidence.checks().stream().noneMatch(check -> check.summary().contains("timed out"));
    }

    private double budgetScore(List<BenchmarkEvidence> benchmarks) {
        double worst = 1.0;
        for (BenchmarkEvidence benchmark : benchmarks) {
            Double budget = config.benchmarkBudgets().get(benchmark.name());
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
