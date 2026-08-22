package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import java.util.Optional;
import com.dreamthought.saaa.domain.MutationContract;
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
    private final DeclaredEvidenceResolver declaredEvidenceResolver = new DeclaredEvidenceResolver();

    public PhenotypeBridgeScorer(RealizationInspector inspector, ScoringConfig config) {
        this.inspector = Objects.requireNonNull(inspector, "inspector");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Scores with no contract. This exists for callers that genuinely have none, and for tests that
     * mean to exercise the contractless path. It is deliberately not on {@link FitnessScorer}: the
     * port forces every implementor to take the contract, so production cannot reach this by
     * forgetting to pass one.
     */
    public FitnessResult score(Candidate candidate, EvaluationEvidence evidence) {
        return score(candidate, evidence, Optional.empty());
    }

    @Override
    public FitnessResult score(
            Candidate candidate, EvaluationEvidence evidence, Optional<MutationContract> contract) {
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
        objectives.put(FitnessSignalId.objective("behavioral_safety").canonical(),
                safetyScore(evidence));
        objectives.put(FitnessSignalId.objective("parsimony").canonical(), parsimony(realization));

        // A safety probe is a check, and every failing check fails the deterministic-checks gate, so
        // a probe left in that list would discard rather than grade. Probes are withheld from the
        // gate for the same reason behaviour cases are separated from build health: they answer a
        // different question. A safety property that must hold belongs in a contract's required
        // evidence, which does gate.
        var gatedEvidence = config.safetyProbeNames().isEmpty()
                ? evidence
                : new EvaluationEvidence(
                        evidence.checks().stream()
                                .filter(check -> !config.safetyProbeNames().contains(check.name()))
                                .toList(),
                        evidence.benchmarks(),
                        evidence.evaluatedAt());
        var phenotype = new PhenotypeEvidence(gatedEvidence, behaviorCases, objectives, realization);
        // A declared required_evidence id names a check that must exist and pass, so the declaration
        // is enforced against evidence this run already collected rather than a separate pipeline.
        return contract
                .map(declared -> delegate.score(candidate, phenotype, declared,
                        declaredEvidenceResolver.resolve(declared.requiredEvidence(), evidence)))
                .orElseGet(() -> delegate.score(candidate, phenotype));
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

    /**
     * The pass fraction of the declared safety probes. A probe that produced no evidence counts as
     * failed, the same rule the gates apply, because a probe that did not run has not shown the
     * property holds.
     *
     * <p>Probes grade and do not gate. A safety property that must hold belongs in a contract's
     * required evidence, where absence or failure discards the candidate; this objective carries
     * 0.10 of the weighted sum and could never be the only thing preventing a promotion.
     */
    private double safetyScore(EvaluationEvidence evidence) {
        var declared = config.safetyProbeNames();
        if (declared.isEmpty()) {
            return 1.0;
        }
        Map<String, Boolean> observed = new LinkedHashMap<>();
        for (CheckEvidence check : evidence.checks()) {
            if (declared.contains(check.name())) {
                observed.merge(check.name(), check.status() == CheckStatus.PASSED,
                        (first, second) -> first && second);
            }
        }
        long passed = declared.stream().filter(name -> Boolean.TRUE.equals(observed.get(name))).count();
        return (double) passed / declared.size();
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
