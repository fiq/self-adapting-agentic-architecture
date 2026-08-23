package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.failed;
import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static com.dreamthought.saaa.domain.FitnessDecision.DISCARD;
import static com.dreamthought.saaa.domain.FitnessDecision.PROMOTE;

import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable golden-verdict corpus for {@link PhenotypeBridgeScorer}.
 *
 * <p>Each entry is a recorded {@code (EvaluationEvidence, RealizationSummary, ScoringConfig, expected
 * decision)} triple. The scorer must reproduce the expected decision on every entry;
 * {@link PhenotypeGoldenVerdictCorpusTest} iterates them.
 *
 * <p>Fixtures are treated as immutable evidence. Editing any entry — including adding a new one —
 * requires a spec change with rationale. The check would otherwise be self-referential.
 *
 * <p>The persona-review pass on {@code CHG-004} pinned the corpus format to TOON. There is no Java
 * TOON reader yet; per {@code docs/structured-data.md}, the fixtures ship as Java constants for now
 * and migrate to TOON files when {@code CHG-002 T3d} lands the envelope reader. The deviation is
 * scoped to this file.
 *
 * <p>Coverage floor met here (see CHG-004 S7):
 * <ul>
 *   <li>one entry per hard gate that can fire through the bridge: {@code checks},
 *       {@code subject.invariant.required_behavior_cases}, {@code subject.invariant.non_empty_realization}
 *       (three of four gates);
 *   <li>the fourth gate, {@code subject.invariant.required_objective_scores}, cannot fire through the bridge because
 *       the bridge always produces all five objective values, so it is covered instead by
 *       {@link PhenotypeFitnessScorerTest} and by property {@code noObjectiveCombinationCanPromoteWhileAGateFails}
 *       in {@link PhenotypeFitnessScorerPropertyTest};
 *   <li>two {@code PROMOTE} entries with different objective profiles;
 *   <li>one entry that scores exactly {@code PROMOTION_THRESHOLD} and promotes;
 *   <li>one entry that scores just below {@code PROMOTION_THRESHOLD} and discards;
 *   <li>one entry that would score high on {@code subject.objective.task_success} alone but is rescued from
 *       over-promotion by the non-empty-realization gate (the {@code CHG-003 T10} case);
 *   <li>one entry captured from a real CHG-003 fixture acceptance run so the corpus is grounded
 *       in observed evidence rather than only constructed cases.
 * </ul>
 */
final class GoldenCorpus {
    private static final Instant WHEN = Instant.parse("2026-08-01T00:00:00Z");
    private static final int DEFAULT_MAX_LINES = 80;

    static List<Entry> entries() {
        return List.of(
                gateChecksFires(),
                gateBehaviourCasesFires(),
                gateNonEmptyRealizationFires(),
                promoteWithPerfectObjectives(),
                promoteWithMixedButPassingObjectives(),
                promoteAtExactThreshold(),
                discardJustBelowThreshold(),
                overPromotionRescuedByNonEmptyRealizationGate(),
                observedCandidateFromChg003FixtureRun()
        );
    }

    /**
     * Gate: {@code subject.invariant.deterministic_checks}. Empty {@code checks} list violates the
     * "absent evidence is not passing evidence" invariant.
     */
    private static Entry gateChecksFires() {
        return new Entry(
                "gate-checks-fires-on-empty-check-evidence",
                "checks list is empty → deterministic_checks gate fails → DISCARD regardless of objective scores",
                new EvaluationEvidence(List.of(), List.of(), WHEN),
                new RealizationSummary(1, 8),
                Set.of("publish-guard"),
                DEFAULT_MAX_LINES,
                Map.of(),
                DISCARD,
                "0.59");
    }

    /**
     * Gate: {@code subject.invariant.required_behavior_cases}. A declared case with a FAILED check
     * entry fails the gate.
     */
    private static Entry gateBehaviourCasesFires() {
        return new Entry(
                "gate-behaviour-cases-fires-on-failed-declared-case",
                "declared behaviour case failed → required_behavior_cases gate fails → DISCARD",
                new EvaluationEvidence(
                        List.of(passed("build", "ok"), failed("publish-guard", "regressed")),
                        List.of(),
                        WHEN),
                new RealizationSummary(1, 8),
                Set.of("publish-guard"),
                DEFAULT_MAX_LINES,
                Map.of(),
                DISCARD,
                "0.59");
    }

    /**
     * Gate: {@code subject.invariant.non_empty_realization}. A candidate that changed no file scores
     * {@code subject.objective.parsimony} 1.0 but must not promote — this is the {@code CHG-003 T10}
     * invariant.
     */
    private static Entry gateNonEmptyRealizationFires() {
        return new Entry(
                "gate-non-empty-realization-fires-on-zero-files-changed",
                "realization changed no file → non_empty_realization gate fails → DISCARD (guards the T10 hole)",
                new EvaluationEvidence(
                        List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                        List.of(),
                        WHEN),
                new RealizationSummary(0, 0),
                Set.of("publish-guard"),
                DEFAULT_MAX_LINES,
                Map.of(),
                DISCARD,
                "1.00");
    }

    /**
     * All checks pass, one declared behaviour case passes, tiny realization. Aggregate is close to
     * perfect.
     */
    private static Entry promoteWithPerfectObjectives() {
        return new Entry(
                "promote-with-perfect-objectives-and-tiny-diff",
                "all objectives near ideal, tiny diff → PROMOTE at aggregate 1.00",
                new EvaluationEvidence(
                        List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                        List.of(),
                        WHEN),
                new RealizationSummary(1, 1),
                Set.of("publish-guard"),
                DEFAULT_MAX_LINES,
                Map.of(),
                PROMOTE,
                "0.99875");
    }

    /**
     * Different objective profile: larger diff (parsimony 0.5), no benchmarks (cost_latency 1.0),
     * behaviour cases pass. Aggregate still comfortably above threshold.
     */
    private static Entry promoteWithMixedButPassingObjectives() {
        return new Entry(
                "promote-with-mixed-but-passing-objectives",
                "checks and behaviour cases pass, medium-sized diff pulls parsimony to 0.5 → PROMOTE at aggregate 0.95",
                new EvaluationEvidence(
                        List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                        List.of(),
                        WHEN),
                new RealizationSummary(1, 40),
                Set.of("publish-guard"),
                DEFAULT_MAX_LINES,
                Map.of(),
                PROMOTE,
                "0.95");
    }

    /**
     * Weighted objectives sum to exactly {@code PROMOTION_THRESHOLD}. The threshold is inclusive
     * (raw sum {@code >= 0.80}), so this promotes. Parsimony 0.00 with a 20-line change picks the
     * boundary numerically: {@code 0.40 + 0.20 + 0.20 + 0.10 + 0} using an 80-line budget puts
     * parsimony at {@code 1 - 80/80 = 0}, giving weighted sum {@code 0.90}. Choose realization to
     * push weighted sum to exactly {@code 0.80}: parsimony must contribute exactly {@code 0.00} to
     * bring the total to {@code 0.90}, so we need weight * parsimony = 0.10 * X = -0.10 which is
     * impossible for positive X. Solve differently: reduce {@code subject.objective.task_success} instead — with
     * {@code task_success = 0.5}, weighted = {@code 0.5*0.4 + 1*0.7 = 0.20 + 0.70 = 0.90}. Still
     * over. To hit exactly 0.80 with defaults elsewhere, use {@code cost_latency_budget = 0.5} via
     * a benchmark budget half of measured, giving {@code 0.40 + 0.20 + 0.10 + 0.10 + 0.10 = 0.90}.
     * Simplest path: use benchmark evidence at 2x its budget so cost_latency scores 0.5 (weighted
     * 0.10), and realization 0.8 * 80 = 64 lines so parsimony is 0.20 (weighted 0.02) — but that
     * lands at 0.72. Cleaner: benchmark at 4x budget → cost_latency 0.25 → weighted 0.05, plus
     * realization 40 → parsimony 0.5 → weighted 0.05, gives {@code 0.40 + 0.20 + 0.05 + 0.10 + 0.05
     * = 0.80}. That is the entry.
     */
    private static Entry promoteAtExactThreshold() {
        return new Entry(
                "promote-at-exact-promotion-threshold",
                "raw weighted sum is exactly 0.80 → PROMOTE (boundary is inclusive; catches a regression that moves the comparison from `>=` to `>`)",
                new EvaluationEvidence(
                        List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                        List.of(com.dreamthought.saaa.domain.BenchmarkEvidence.measurement("publish-latency", 200.0, "ms")),
                        WHEN),
                new RealizationSummary(1, 40),
                Set.of("publish-guard"),
                DEFAULT_MAX_LINES,
                Map.of("publish-latency", 50.0),
                PROMOTE,
                "0.80");
    }

    /**
     * Same shape as {@link #promoteAtExactThreshold()} but tuned to land just below the threshold.
     * Increase the benchmark overshoot slightly so cost_latency drops another notch. With
     * {@code publish-latency=50/210=0.238}, weighted contribution is {@code 0.20*0.238 = 0.0476}.
     * Total {@code 0.40 + 0.20 + 0.0476 + 0.10 + 0.05 = 0.7976}, well below 0.80 → DISCARD.
     */
    private static Entry discardJustBelowThreshold() {
        return new Entry(
                "discard-just-below-promotion-threshold",
                "raw weighted sum is 0.7976 which rounds to 0.80 but is strictly less → DISCARD; the reported aggregate is the rounded value even for a DISCARD (all gates pass), so this entry ALSO covers property P8 (decision from raw sum, not rounded aggregate) at the corpus level",
                new EvaluationEvidence(
                        List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                        List.of(com.dreamthought.saaa.domain.BenchmarkEvidence.measurement("publish-latency", 210.0, "ms")),
                        WHEN),
                new RealizationSummary(1, 40),
                Set.of("publish-guard"),
                DEFAULT_MAX_LINES,
                Map.of("publish-latency", 50.0),
                DISCARD,
                "0.797619047619047616");
    }

    /**
     * Every objective at 1.0 but the realization is empty. Weighted sum would be 1.00 but the
     * non-empty-realization gate fires first and fails, so the candidate is discarded while keeping its weighted score. Guards the {@code
     * CHG-003 T10} case where parsimony 1.0 would have promoted a no-op candidate.
     */
    private static Entry overPromotionRescuedByNonEmptyRealizationGate() {
        return new Entry(
                "over-promotion-rescued-by-non-empty-realization-gate",
                "every objective would score 1.0 but the realization changed no file → gate discards the candidate before weighting (the T10 rescue)",
                new EvaluationEvidence(
                        List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                        List.of(),
                        WHEN),
                new RealizationSummary(0, 0),
                Set.of("publish-guard"),
                DEFAULT_MAX_LINES,
                Map.of(),
                DISCARD,
                "1.00");
    }

    /**
     * Captured from the CHG-003 {@code toy-workflow} fixture run:
     * <ul>
     *   <li>{@code workflow.txt} baseline is {@code "draft-check: skip\n"};
     *   <li>the fixture proposer rewrites it to {@code "draft-check: enforce\n"};
     *   <li>{@code git diff --numstat} reports one file, one added line + one removed line = 2;
     *   <li>{@code workflow-check.sh} passes on the rewritten file;
     *   <li>no benchmarks, so cost_latency_budget scores 1.0;
     *   <li>reliability 1.0 (nothing timed out), behavioral_safety 1.0 (inert this slice),
     *       parsimony {@code 1 - 2/80 = 0.975};
     *   <li>weighted sum {@code 0.40*1 + 0.20*1 + 0.20*1 + 0.10*1 + 0.10*0.975 = 0.9975}, rounds
     *       to {@code 1.00}, above threshold → PROMOTE.
     * </ul>
     * Grounds the corpus in observed evidence rather than only constructed cases.
     */
    private static Entry observedCandidateFromChg003FixtureRun() {
        return new Entry(
                "observed-chg-003-fixture-workflow-check-promote",
                "captured from the CHG-003 fixture toy-workflow acceptance run; single-line workflow rewrite promotes at aggregate 1.00",
                new EvaluationEvidence(
                        List.of(passed("workflow-check", "exit=0")),
                        List.of(),
                        WHEN),
                new RealizationSummary(1, 2),
                Set.of("workflow-check"),
                DEFAULT_MAX_LINES,
                Map.of(),
                PROMOTE,
                "0.9975");
    }

    private GoldenCorpus() {
    }

    /**
     * A single corpus entry.
     *
     * @param name unique identifier; appears in test failure messages
     * @param rationale one-line reason the entry is in the corpus; explains why the expected
     *                  decision is what it is
     * @param evidence the raw evaluation evidence a run would have produced
     * @param realization the diff summary a real inspector would have produced
     * @param declaredBehaviourCases the behaviour case names configured for the run; feeds
     *                               {@link ScoringConfig}
     * @param maxLinesChanged the change budget parsimony is scored against
     * @param benchmarkBudgets budgets used to score {@code cost_latency_budget}
     * @param expectedDecision the scorer must reproduce this decision
     * @param expectedRawMagnitude the scorer must reproduce this raw weighted magnitude
     */
    record Entry(
            String name,
            String rationale,
            EvaluationEvidence evidence,
            RealizationSummary realization,
            Set<String> declaredBehaviourCases,
            int maxLinesChanged,
            Map<String, Double> benchmarkBudgets,
            FitnessDecision expectedDecision,
            String expectedRawMagnitude) {
    }
}
