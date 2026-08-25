package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CHG-024 S4. A held-out case is withheld from both gates while still feeding {@code task_success},
 * so a name that also appears in another declared set would mean two different things at once.
 *
 * <p>Only the structural-gate rule is inherited from the existing configuration checks. The other
 * three are new: until held-out cases existed, no two declared name sets could name one check with
 * conflicting meanings, so nothing compared them.
 */
final class HeldOutCaseConfigTest {
    private static final Map<String, Double> NO_BUDGETS = Map.of();

    @Test
    void rejectsAHeldOutCaseThatIsAlsoAGatingBehaviourCase() {
        assertThatThrownBy(() -> new ScoringConfig(
                Set.of("workflow_check"), 80, NO_BUDGETS, Set.of(), 1, Set.of("workflow_check")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("also a gating behaviour case");
    }

    @Test
    void rejectsAHeldOutCaseThatIsAlsoASafetyProbe() {
        assertThatThrownBy(() -> new ScoringConfig(
                Set.of("workflow_check"), 80, NO_BUDGETS, Set.of("probe"), 1, Set.of("probe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("also a safety probe");
    }

    @Test
    void rejectsAHeldOutCaseNamedForAStructuralGate() {
        assertThatThrownBy(() -> new ScoringConfig(
                Set.of("workflow_check"), 80, NO_BUDGETS, Set.of(), 1, Set.of("non_empty_realization")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collides with a structural gate");
    }

    /**
     * A held-out case named like a derived repeat run would be withheld twice and attributed to the
     * wrong base case when reliability counts runs, so the shape is refused rather than resolved.
     */
    @Test
    void rejectsAHeldOutCaseShapedLikeARepeatedRun() {
        assertThatThrownBy(() -> new ScoringConfig(
                Set.of("workflow_check"), 80, NO_BUDGETS, Set.of(), 1, Set.of("workflow_check.run2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not look like a repeated run");
    }

    /**
     * The gating view is what {@code required_behavior_cases} reads. A held-out case stays in
     * {@code behaviorCases} so it still lowers {@code task_success}; it is removed only from what the
     * gate sees. Asserting both halves keeps the test sensitive in both directions: dropping the
     * held-out case from the scored list entirely would satisfy a gate-only assertion.
     */
    @Test
    void withholdsAHeldOutCaseFromTheGateWhileKeepingItInTheScoredList() {
        var phenotype = new PhenotypeEvidence(
                new com.dreamthought.saaa.domain.EvaluationEvidence(
                        java.util.List.of(), java.util.List.of(), java.time.Instant.EPOCH),
                java.util.List.of(
                        BehaviorCaseEvidence.passed("gating", "held"),
                        BehaviorCaseEvidence.failed("held_out", "did not hold")),
                Map.of(),
                new com.dreamthought.saaa.domain.RealizationSummary(1, 1),
                Set.of("held_out"),
                Set.of("held_out"));

        assertThat(phenotype.gatingBehaviorCases())
                .as("the gate must not see the held-out case")
                .extracting(BehaviorCaseEvidence::id)
                .containsExactly("gating");
        assertThat(phenotype.behaviorCases())
                .as("task_success must still see it, or holding it out would score nothing")
                .extracting(BehaviorCaseEvidence::id)
                .containsExactly("gating", "held_out");
    }
}
