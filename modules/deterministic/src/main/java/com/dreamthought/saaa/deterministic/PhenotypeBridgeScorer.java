package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import java.util.Optional;
import java.util.Set;
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
        // Held-out cases are behaviour cases for the objective and not for the gate, so they are
        // collected here alongside the gating ones and separated again at the scorer through
        // `PhenotypeEvidence.gatingBehaviorCases()`. Collecting them anywhere else would leave
        // `task_success` unable to see them, which is the entire defect CHG-024 exists to fix.
        var scoredCaseNames = new java.util.LinkedHashSet<>(config.behaviorCaseNames());
        scoredCaseNames.addAll(config.heldOutCaseNames());

        Map<String, BehaviorCaseEvidence> observed = new LinkedHashMap<>();
        evidence.checks().stream()
                .filter(check -> scoredCaseNames.contains(check.name()))
                .forEach(check -> observed.merge(
                        check.name(),
                        toBehaviorCase(check),
                        (existing, latest) -> existing.status() == CheckStatus.FAILED ? existing : latest));

        // Fail closed on any declared case that produced no evidence. Filtering alone would drop it
        // silently and let the gate pass on the cases that did report, which is the same weakness as
        // a declared-but-unenforced contract gate: the required behaviour was never shown to hold.
        List<BehaviorCaseEvidence> behaviorCases = scoredCaseNames.stream()
                .map(name -> observed.getOrDefault(
                        name, BehaviorCaseEvidence.failed(name, "no check evidence was produced for this case")))
                .toList();

        RealizationSummary realization = Objects.requireNonNull(
                inspector.inspect(candidate), "realization summary");

        Map<String, Double> objectives = new LinkedHashMap<>();
        objectives.put(FitnessSignalId.objective("task_success").canonical(), passedFraction(behaviorCases));
        objectives.put(FitnessSignalId.objective("reliability").canonical(), reliabilityScore(evidence));
        objectives.put(
                FitnessSignalId.objective("cost_latency_budget").canonical(), budgetScore(evidence.benchmarks()));
        objectives.put(FitnessSignalId.objective("behavioral_safety").canonical(),
                safetyScore(evidence));
        objectives.put(FitnessSignalId.objective("parsimony").canonical(), parsimony(realization));

        // A safety probe is a check, and every failing check fails the deterministic-checks gate, so
        // a probe left gating would discard rather than grade. Probes are withheld from the gate for
        // the same reason behaviour cases are separated from build health: they answer a different
        // question. A safety property that must hold belongs in a contract's required evidence,
        // which does gate. The probe stays in the evidence either way, so a lowered safety score can
        // always be traced to the probe that produced it.
        var phenotype = new PhenotypeEvidence(
                evidence, behaviorCases, objectives, realization, nonGatingCheckNames(),
                config.heldOutCaseNames(),
                config.behaviorCaseNames(), config.maxLinesChanged(), config.benchmarkBudgets(),
                unmeasuredObjectiveIds(evidence));
        // A declared required_evidence id names a check that must exist and pass, so the declaration
        // is enforced against evidence this run already collected rather than a separate pipeline.
        return contract
                .map(declared -> delegate.score(candidate, phenotype, declared,
                        declaredEvidenceResolver.resolve(declared.requiredEvidence(), evidence)))
                .orElseGet(() -> delegate.score(candidate, phenotype));
    }

    /**
     * The objectives this run had no evidence source for.
     *
     * <p>Keyed on what was <em>declared</em>, not on the value produced: a declared probe that passes
     * scores {@code 1.0} and was genuinely measured. Only absence of a source counts.
     *
     * <p>{@code task_success}, {@code reliability} and {@code parsimony} always have a source - the
     * behaviour cases, the checks that ran, and the realized diff - so only these two can be absent.
     */
    private Set<String> unmeasuredObjectiveIds(EvaluationEvidence evidence) {
        var unmeasured = new java.util.LinkedHashSet<String>();
        if (config.safetyProbeNames().isEmpty()) {
            unmeasured.add(FitnessSignalId.objective("behavioral_safety").canonical());
        }
        // Declaring a budget is not the same as measuring against one. budgetScore starts at 1.0
        // and only moves when a benchmark actually ran and matched a declared budget, so keying on
        // declaration alone left the original defect alive in a narrower shape: declare a budget,
        // run no benchmark, and the objective still scored full marks for measuring nothing.
        if (!anyBudgetApplied(evidence.benchmarks())) {
            unmeasured.add(FitnessSignalId.objective("cost_latency_budget").canonical());
        }
        return Set.copyOf(unmeasured);
    }

    /** Whether any benchmark that ran was actually compared against a declared budget. */
    private boolean anyBudgetApplied(List<BenchmarkEvidence> benchmarks) {
        return benchmarks.stream()
                .anyMatch(benchmark -> budgetFor(benchmark.name()) != null && benchmark.value() > 0.0);
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

    /**
     * The fraction of behaviour-case runs that passed, counting every repeated run separately.
     *
     * <p>This objective used to ask only whether nothing timed out, which no candidate that cleared
     * the deterministic-checks gate could ever fail: a timed-out check is not a passed check, so the
     * gate had already discarded it. The objective restated its own gate and sat at 1.0 for every
     * candidate that promoted, pinning a fifth of the weight.
     *
     * <p>With repeated runs it measures something the gate does not: the canonical run decides
     * eligibility, and the repeats show how reliably that result holds. A candidate passing eight of
     * ten runs is eligible and scores 0.8; one passing all ten scores 1.0. A timed-out run counts as
     * a failed run, which is the same rule everywhere else here — absent evidence is not passing
     * evidence.
     *
     * <p>With a single run per case the value is 1.0 for any candidate that reaches the objectives,
     * so a caller who declares no repeats is unaffected.
     */
    private double reliabilityScore(EvaluationEvidence evidence) {
        // With one run per case there is no repeat evidence, so there is nothing to say about
        // consistency and the old timeout rule is kept exactly. Returning a pass fraction here
        // instead would make this objective a second copy of task_success, which is already the pass
        // fraction of the same behaviour cases: it would double-weight failing a case rather than
        // measure anything new.
        if (config.reliabilityRuns() <= 1) {
            return allChecksRan(evidence) ? 1.0 : 0.0;
        }
        var counted = new java.util.LinkedHashSet<>(config.behaviorCaseNames());
        counted.addAll(config.repeatRunNames());
        var runs = evidence.checks().stream()
                .filter(check -> counted.contains(check.name()))
                .toList();
        if (runs.isEmpty()) {
            return 0.0;
        }
        long passed = runs.stream().filter(check -> check.status() == CheckStatus.PASSED).count();
        return (double) passed / runs.size();
    }

    /** Timeout is structured evidence; summaries remain candidate-controlled diagnostic text. */
    private static boolean allChecksRan(EvaluationEvidence evidence) {
        return evidence.checks().stream().noneMatch(check -> check.status() == CheckStatus.TIMED_OUT);
    }

    /** Repeated runs grade rather than gate, so only the canonical run of each case reaches the gate. */
    private Set<String> nonGatingCheckNames() {
        var withheld = new java.util.LinkedHashSet<>(config.safetyProbeNames());
        withheld.addAll(config.repeatRunNames());
        // A held-out case is a failing check when it fails, and every failing check fails the
        // deterministic-checks gate, so leaving it here would discard the candidate through the
        // other gate and defeat holding it out at all.
        withheld.addAll(config.heldOutCaseNames());
        return withheld;
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
