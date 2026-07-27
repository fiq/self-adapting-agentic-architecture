package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.FitnessObjective;
import io.github.selfadaptingagenticarchitecture.core.MutationBounds;
import io.github.selfadaptingagenticarchitecture.core.MutationOperatorType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps the closed operator enum to the bounds, evidence and fitness dimensions the next loop must
 * apply. Every operator shares the same objective set and hard gates so candidates stay comparable;
 * operators differ only in how much change they may realize and what evidence they must produce.
 */
public final class MutationOperatorPolicy {
    public static final List<String> DEFAULT_HARD_GATES =
            List.of("deterministic_checks_pass", "required_evidence_present");

    public static final List<FitnessObjective> DEFAULT_OBJECTIVES = List.of(
            new FitnessObjective("task_success", 0.40),
            new FitnessObjective("reliability", 0.20),
            new FitnessObjective("cost_latency_budget", 0.20),
            new FitnessObjective("behavioral_safety", 0.10),
            new FitnessObjective("parsimony", 0.10)
    );

    private static final Map<MutationOperatorType, MutationOperatorDefaults> DEFAULTS = Map.of(
            MutationOperatorType.TARGETED_BEHAVIOR_CHANGE,
            defaults(2, 80, "unit_tests_pass", "property_tests_pass", "benchmark_not_worse_than_baseline"),
            MutationOperatorType.REPAIR,
            defaults(3, 120, "failing_case_reproduced", "unit_tests_pass", "regression_case_added"),
            MutationOperatorType.SIMPLIFY,
            defaults(3, 120, "unit_tests_pass", "behavior_cases_unchanged"),
            MutationOperatorType.PERFORMANCE_TUNE,
            defaults(2, 80, "unit_tests_pass", "benchmark_not_worse_than_baseline"),
            MutationOperatorType.GUARDRAIL_CHANGE,
            defaults(2, 80, "unit_tests_pass", "guardrail_cases_pass"),
            MutationOperatorType.TOOL_STRATEGY_CHANGE,
            defaults(2, 80, "unit_tests_pass", "behavior_cases_pass", "benchmark_not_worse_than_baseline"),
            MutationOperatorType.MODEL_ROUTING_CHANGE,
            defaults(2, 60, "behavior_cases_pass", "cost_latency_budget_met"),
            MutationOperatorType.PROMPT_POLICY_CHANGE,
            defaults(2, 60, "behavior_cases_pass", "behavioral_safety_cases_pass"),
            MutationOperatorType.HILL_CLIMB,
            defaults(2, 80, "unit_tests_pass", "parent_fitness_recorded", "benchmark_not_worse_than_baseline"),
            MutationOperatorType.EXPLORATORY_LEAP,
            defaults(4, 160, "unit_tests_pass", "behavior_cases_pass", "risk_budget_recorded")
    );

    private MutationOperatorPolicy() {
    }

    public static MutationOperatorDefaults defaultsFor(MutationOperatorType operator) {
        MutationOperatorDefaults operatorDefaults = DEFAULTS.get(Objects.requireNonNull(operator, "operator"));
        if (operatorDefaults == null) {
            throw new IllegalArgumentException("unsupported mutation operator: " + operator.wireName());
        }
        return operatorDefaults;
    }

    public static boolean requiresSearchPosture(MutationOperatorType operator) {
        return operator == MutationOperatorType.HILL_CLIMB || operator == MutationOperatorType.EXPLORATORY_LEAP;
    }

    private static MutationOperatorDefaults defaults(int maxFilesChanged, int maxLinesChanged, String... evidence) {
        return new MutationOperatorDefaults(
                new MutationBounds(maxFilesChanged, maxLinesChanged, false, false, false),
                List.of(evidence),
                DEFAULT_HARD_GATES,
                DEFAULT_OBJECTIVES
        );
    }
}
