package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationOperatorType.TARGETED_BEHAVIOR_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.FitnessObjective;
import com.dreamthought.saaa.domain.MutationBounds;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.MutationOperatorType;
import com.dreamthought.saaa.domain.MutationTarget;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class MutationContractValidatorTest {
    private final MutationContractValidator validator = new MutationContractValidator();

    @Test
    void acceptsBoundedTargetedMutationContract() {
        assertThat(validator.validate(validContract()).valid()).isTrue();
    }

    @Test
    void rejectsUnsupportedOperatorUnsafeLociOrExcessiveBudget() {
        assertThatThrownBy(() -> MutationOperatorType.fromWireName("rewrite-everything"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported mutation operator: rewrite-everything");

        var contract = new MutationContract(
                "MUT-unsafe",
                TARGETED_BEHAVIOR_CHANGE,
                "rewrite the evaluator and approve the candidate",
                new MutationTarget("method", "../secrets.env", "calculateInterest"),
                List.of("method_body"),
                new MutationBounds(6, 240, true, false, false),
                List.of("unit_tests_pass"),
                List.of("deterministic_checks_pass"),
                defaultObjectives(),
                Optional.empty(),
                List.of()
        );

        assertThat(validator.validate(contract).messages()).contains(
                "mutation contract must not contain approval, scoring, promotion, discard or rollback authority",
                "target file must be repo-relative and must not traverse parent directories",
                "maxFilesChanged must be at most 2 for targeted-behavior-change",
                "maxLinesChanged must be at most 80 for targeted-behavior-change",
                "publicApiChange is not allowed for targeted-behavior-change",
                "required evidence is missing: property_tests_pass",
                "required evidence is missing: benchmark_not_worse_than_baseline",
                "hard gate is missing: required_evidence_present"
        );
    }

    @Test
    void rejectsObjectivesThatDoNotMatchTheOperatorDefaults() {
        var contract = new MutationContract(
                "MUT-reweighted",
                TARGETED_BEHAVIOR_CHANGE,
                "change interest rounding only at money boundaries",
                new MutationTarget(
                        "method",
                        "src/main/java/example/billing/InterestCalculator.java",
                        "calculateInterest"
                ),
                List.of("method_body"),
                new MutationBounds(2, 80, false, false, false),
                List.of("unit_tests_pass", "property_tests_pass", "benchmark_not_worse_than_baseline"),
                List.of("deterministic_checks_pass", "required_evidence_present"),
                List.of(
                        new FitnessObjective("task_success", 0.90),
                        new FitnessObjective("reliability", 0.10)
                ),
                Optional.empty(),
                List.of()
        );

        var result = validator.validate(contract);

        assertThat(result.valid()).isFalse();
        assertThat(result.messages())
                .contains("fitness objectives must match the deterministic defaults for targeted-behavior-change");
    }

    private static MutationContract validContract() {
        return new MutationContract(
                "MUT-001",
                TARGETED_BEHAVIOR_CHANGE,
                "change interest rounding only at money boundaries",
                new MutationTarget(
                        "method",
                        "src/main/java/example/billing/InterestCalculator.java",
                        "calculateInterest"
                ),
                List.of("method_body", "adjacent_unit_tests"),
                new MutationBounds(2, 80, false, false, false),
                List.of("unit_tests_pass", "property_tests_pass", "benchmark_not_worse_than_baseline"),
                List.of("deterministic_checks_pass", "required_evidence_present"),
                defaultObjectives(),
                Optional.empty(),
                List.of()
        );
    }

    private static List<FitnessObjective> defaultObjectives() {
        return List.of(
                new FitnessObjective("task_success", 0.40),
                new FitnessObjective("reliability", 0.20),
                new FitnessObjective("cost_latency_budget", 0.20),
                new FitnessObjective("behavioral_safety", 0.10),
                new FitnessObjective("parsimony", 0.10)
        );
    }
}
