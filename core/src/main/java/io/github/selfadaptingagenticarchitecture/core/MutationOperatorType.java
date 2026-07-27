package io.github.selfadaptingagenticarchitecture.core;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Closed initial mutation operator enum. The operator is semi-declarative input into the next loop:
 * it selects deterministic defaults for bounds, required evidence and fitness dimensions.
 *
 * <p>Conceptual crossover is deliberately absent. It is a recombination policy that produces a child
 * contract using one of these values, not an operator.
 */
public enum MutationOperatorType {
    TARGETED_BEHAVIOR_CHANGE,
    REPAIR,
    SIMPLIFY,
    PERFORMANCE_TUNE,
    GUARDRAIL_CHANGE,
    TOOL_STRATEGY_CHANGE,
    MODEL_ROUTING_CHANGE,
    PROMPT_POLICY_CHANGE,
    HILL_CLIMB,
    EXPLORATORY_LEAP;

    private static final Map<String, MutationOperatorType> BY_WIRE_NAME = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(MutationOperatorType::wireName, Function.identity()));

    public String wireName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static MutationOperatorType fromWireName(String wireName) {
        MutationOperatorType operator = BY_WIRE_NAME.get(Require.nonBlank(wireName, "wireName"));
        if (operator == null) {
            throw new IllegalArgumentException("unsupported mutation operator: " + wireName);
        }
        return operator;
    }
}
