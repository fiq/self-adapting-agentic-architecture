package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationOperatorType.EXPLORATORY_LEAP;
import static com.dreamthought.saaa.domain.MutationOperatorType.HILL_CLIMB;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.FitnessObjective;
import com.dreamthought.saaa.domain.MutationBounds;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.MutationOperatorType;
import com.dreamthought.saaa.domain.MutationTarget;
import com.dreamthought.saaa.domain.SearchPosture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SearchPosturePolicyTest {
    private final MutationContractValidator validator = new MutationContractValidator();
    private final MutationContractCanonicalizer canonicalizer = new MutationContractCanonicalizer();

    @Test
    void recordsHillClimbAndExploratoryLeapControls() {
        var hillClimb = searchContract(
                HILL_CLIMB,
                new SearchPosture(
                        "cand-parent",
                        "cost_latency_budget",
                        "reduce_latency_5_percent",
                        "max_files_2"
                )
        );
        var exploratoryLeap = searchContract(
                EXPLORATORY_LEAP,
                new SearchPosture(
                        "cand-parent",
                        "task_success",
                        "try_tool_reordering",
                        "max_files_4_manual_review"
                )
        );

        assertThat(validator.validate(hillClimb).valid()).isTrue();
        assertThat(validator.validate(exploratoryLeap).valid()).isTrue();
        assertThat(canonicalizer.canonicalize(hillClimb))
                .contains(
                        "(search-posture"
                                + " (parent cand-parent)"
                                + " (objective cost-latency-budget)"
                                + " (expected-delta reduce-latency-5-percent)"
                                + " (risk-budget max-files-2))"
                );
        assertThat(canonicalizer.canonicalize(exploratoryLeap))
                .contains(
                        "(search-posture"
                                + " (parent cand-parent)"
                                + " (objective task-success)"
                                + " (expected-delta try-tool-reordering)"
                                + " (risk-budget max-files-4-manual-review))"
                );
    }

    @Test
    void rejectsSearchOperatorsWithoutPostureEvidence() {
        var contract = searchContract(HILL_CLIMB, null);

        assertThat(validator.validate(contract).messages())
                .contains("hill-climb requires search posture with parent candidate, objective focus and risk budget");
    }

    @Test
    void rejectsAuthorityLanguageHiddenInSearchPostureFields() {
        var contract = searchContract(
                HILL_CLIMB,
                new SearchPosture(
                        "cand-parent",
                        "task_success",
                        "promote this candidate automatically",
                        "max_files_2"
                )
        );

        assertThat(validator.validate(contract).messages()).contains(
                "mutation contract must not contain approval, scoring, promotion, discard or rollback authority"
        );
    }

    private static MutationContract searchContract(MutationOperatorType operator, SearchPosture searchPosture) {
        var defaults = MutationOperatorPolicy.defaultsFor(operator);
        return new MutationContract(
                "MUT-" + operator.wireName(),
                operator,
                "use fitness feedback to try a bounded behavioral variant",
                new MutationTarget(
                        "method",
                        "src/main/java/example/agents/ToolPolicy.java",
                        "chooseTool"
                ),
                List.of("method_body"),
                defaults.bounds(),
                defaults.requiredEvidence(),
                defaults.hardGates(),
                List.of(
                        new FitnessObjective("subject.objective.task_success", 0.40),
                        new FitnessObjective("subject.objective.reliability", 0.20),
                        new FitnessObjective("subject.objective.cost_latency_budget", 0.20),
                        new FitnessObjective("subject.objective.behavioral_safety", 0.10),
                        new FitnessObjective("subject.objective.parsimony", 0.10)
                ),
                Optional.ofNullable(searchPosture),
                List.of()
        );
    }
}
