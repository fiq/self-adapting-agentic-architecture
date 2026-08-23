package com.dreamthought.saaa.deterministic;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Policy inputs the bridge needs that do not come from evidence.
 *
 * @param behaviorCaseNames checks that are required behaviour rather than build health; these
 *                          hard-gate promotion
 * @param maxLinesChanged   the change budget parsimony is measured against
 * @param benchmarkBudgets  benchmark name to its budget in the benchmark's own unit
 * @param safetyProbeNames  checks whose pass fraction becomes the behavioural-safety objective.
 *                          These grade rather than gate: a failing probe lowers the score and does
 *                          not discard, so a safety property that must hold belongs in a contract's
 *                          required evidence instead, where absence or failure discards. Declaring
 *                          none leaves the objective at its 1.0 starting point
 */
public record ScoringConfig(
        Set<String> behaviorCaseNames,
        int maxLinesChanged,
        Map<String, Double> benchmarkBudgets,
        Set<String> safetyProbeNames,
        int reliabilityRuns,
        Set<String> heldOutCaseNames) {

    /**
     * Separates a behaviour case from the index of a repeated run of it, as in
     * {@code unit_tests_pass.run2}. Repeated runs carry the same command and a distinct name so each
     * result is separately attributable, and they are withheld from the deterministic-checks gate:
     * the canonical run decides whether the candidate is eligible, the repeats grade how reliably it
     * holds. Without the withholding a single flaky run would discard rather than lower a score,
     * which is the trap that kept reliability pinned at 1.0 for every candidate that promoted.
     */
    public static final String REPEAT_RUN_SEPARATOR = ".run";

    /**
     * Each repeat re-executes a candidate-authored script, so the run count multiplies wall-clock
     * cost directly. An unbounded count would let one flag schedule billions of checks before
     * anything executes, which exhausts memory rather than producing evidence.
     */
    public static final int MAX_RELIABILITY_RUNS = 50;

    /**
     * The exact names the repeated runs of this configuration carry.
     *
     * <p>Derived from the declared cases and the run count rather than recognised by pattern, so a
     * check that merely looks like a repeat cannot be withheld from the gate. Inferring from the name
     * would let an unexpected result called {@code compile.run2} silently escape the checks gate.
     */
    public Set<String> repeatRunNames() {
        if (reliabilityRuns <= 1) {
            return Set.of();
        }
        var names = new java.util.LinkedHashSet<String>();
        for (String caseName : behaviorCaseNames) {
            for (int run = 2; run <= reliabilityRuns; run++) {
                names.add(caseName + REPEAT_RUN_SEPARATOR + run);
            }
        }
        return Set.copyOf(names);
    }

    /** The behaviour case a check result belongs to, collapsing any repeated-run suffix. */
    public static String baseCaseName(String checkName) {
        int separator = checkName.lastIndexOf(REPEAT_RUN_SEPARATOR);
        if (separator <= 0) {
            return checkName;
        }
        String suffix = checkName.substring(separator + REPEAT_RUN_SEPARATOR.length());
        return !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)
                ? checkName.substring(0, separator)
                : checkName;
    }

    /** No held-out cases: every prior caller keeps `task_success` over the gating cases alone. */
    public ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged,
            Map<String, Double> benchmarkBudgets, Set<String> safetyProbeNames, int reliabilityRuns) {
        this(behaviorCaseNames, maxLinesChanged, benchmarkBudgets, safetyProbeNames, reliabilityRuns,
                Set.of());
    }

    /** Every check gates and each behaviour case runs once. */
    public ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged,
            Map<String, Double> benchmarkBudgets, Set<String> safetyProbeNames) {
        this(behaviorCaseNames, maxLinesChanged, benchmarkBudgets, safetyProbeNames, 1, Set.of());
    }

    /** Every prior caller keeps its behaviour: no probes declared means the objective stays 1.0. */
    public ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged, Map<String, Double> benchmarkBudgets) {
        this(behaviorCaseNames, maxLinesChanged, benchmarkBudgets, Set.of(), 1);
    }

    public ScoringConfig {
        // A held-out case runs, is recorded, and feeds `task_success`, but decides no gate. It is
        // deliberately NOT another entry in `nonGatingCheckNames`: that set only narrows the
        // deterministic-checks gate, while `required_behavior_cases` reads the behaviour-case list
        // directly. Held-out cases are therefore withheld from the gate by
        // `PhenotypeEvidence.gatingBehaviorCases()` instead. See CHG-024.
        heldOutCaseNames = Set.copyOf(Objects.requireNonNull(heldOutCaseNames, "heldOutCaseNames"));
        if (heldOutCaseNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("held-out case names must not be blank");
        }
        safetyProbeNames = Set.copyOf(Objects.requireNonNull(safetyProbeNames, "safetyProbeNames"));
        if (safetyProbeNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("safety probe names must not be blank");
        }
        // A probe named for a structural gate is withheld from that gate while the gate's own signal
        // still records its outcome, so the audit trail would show a failed probe beside a passing
        // gate of the same name. Declared required evidence already rejects these reserved names.
        var reserved = safetyProbeNames.stream()
                .filter(PhenotypeFitnessScorer.STRUCTURAL_GATE_NAMES::contains)
                .toList();
        if (!reserved.isEmpty()) {
            throw new IllegalArgumentException(
                    "safety probe name collides with a structural gate: " + reserved);
        }
        behaviorCaseNames = Set.copyOf(Objects.requireNonNull(behaviorCaseNames, "behaviorCaseNames"));
        benchmarkBudgets = Map.copyOf(Objects.requireNonNull(benchmarkBudgets, "benchmarkBudgets"));
        if (behaviorCaseNames.isEmpty()) {
            throw new IllegalArgumentException("at least one check must be declared a behaviour case");
        }
        // A blank name cannot be matched against any check evidence, so it would be a declared
        // required behaviour that no check can ever satisfy.
        if (behaviorCaseNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("behaviour case names must not be blank");
        }
        if (maxLinesChanged <= 0) {
            throw new IllegalArgumentException("maxLinesChanged must be positive");
        }
        // These four collision rules are new, not inherited. The existing checks above validate
        // safety probe names against structural gate names only; nothing previously compared one
        // declared name set against another, because until held-out cases no two sets could name
        // the same check with different meanings.
        var gatingCollision = heldOutCaseNames.stream().filter(behaviorCaseNames::contains).toList();
        if (!gatingCollision.isEmpty()) {
            throw new IllegalArgumentException(
                    "held-out case is also a gating behaviour case, so it would both gate and be "
                            + "withheld from the gate: " + gatingCollision);
        }
        var probeCollision = heldOutCaseNames.stream().filter(safetyProbeNames::contains).toList();
        if (!probeCollision.isEmpty()) {
            throw new IllegalArgumentException(
                    "held-out case is also a safety probe, so one check would feed two objectives: "
                            + probeCollision);
        }
        var structuralCollision = heldOutCaseNames.stream()
                .filter(PhenotypeFitnessScorer.STRUCTURAL_GATE_NAMES::contains)
                .toList();
        if (!structuralCollision.isEmpty()) {
            throw new IllegalArgumentException(
                    "held-out case name collides with a structural gate: " + structuralCollision);
        }
        // A held-out case named like a derived repeat run, such as `unit_tests_pass.run2`, would be
        // withheld twice and attributed to the wrong base case when reliability counts runs.
        var repeatShaped = heldOutCaseNames.stream()
                .filter(name -> !baseCaseName(name).equals(name))
                .toList();
        if (!repeatShaped.isEmpty()) {
            throw new IllegalArgumentException(
                    "held-out case name must not look like a repeated run: " + repeatShaped);
        }
        if (reliabilityRuns < 1 || reliabilityRuns > MAX_RELIABILITY_RUNS) {
            throw new IllegalArgumentException(
                    "reliabilityRuns must be between 1 and " + MAX_RELIABILITY_RUNS
                            + ", got " + reliabilityRuns);
        }
    }
}
