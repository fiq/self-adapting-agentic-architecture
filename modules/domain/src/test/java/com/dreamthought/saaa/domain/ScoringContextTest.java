package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CHG-024. The fingerprint has to be sensitive to everything that changes what a magnitude means.
 *
 * <p>A single assertion that the field is present would pass against a constant, and a fingerprint
 * over objective ids alone would pass a naive test while still treating two incomparable
 * configurations as comparable. Each component therefore gets its own assertion, and the held-out
 * one matters most because it is the field CHG-024 adds.
 */
final class ScoringContextTest {
    private static final List<FitnessObjective> OBJECTIVES = List.of(
            new FitnessObjective("subject.objective.task_success", 0.40),
            new FitnessObjective("subject.objective.parsimony", 0.60));

    private static ScoringContext context() {
        return new ScoringContext(OBJECTIVES, Set.of("held_out"), Set.of("probe", "held_out"), 0.80);
    }

    @Test
    void anIdenticalConfigurationFingerprintsIdentically() {
        assertThat(context().fingerprint()).isEqualTo(context().fingerprint());
    }

    @Test
    void changingTheHeldOutSetChangesTheFingerprint() {
        var other = new ScoringContext(
                OBJECTIVES, Set.of("held_out", "second"), Set.of("probe", "held_out", "second"), 0.80);
        assertThat(other.fingerprint()).isNotEqualTo(context().fingerprint());
    }

    @Test
    void changingAWeightChangesTheFingerprint() {
        var reweighted = new ScoringContext(
                List.of(new FitnessObjective("subject.objective.task_success", 0.50),
                        new FitnessObjective("subject.objective.parsimony", 0.50)),
                Set.of("held_out"), Set.of("probe", "held_out"), 0.80);
        assertThat(reweighted.fingerprint()).isNotEqualTo(context().fingerprint());
    }

    @Test
    void changingTheProbeSetChangesTheFingerprint() {
        var other = new ScoringContext(
                OBJECTIVES, Set.of("held_out"), Set.of("other_probe", "held_out"), 0.80);
        assertThat(other.fingerprint()).isNotEqualTo(context().fingerprint());
    }

    /**
     * Raising the reliability run count adds repeat-run names to the withheld set, so a run scored
     * over more repeats cannot fingerprint the same as one scored over fewer. The run count is
     * captured through the names it produces rather than stored separately, because the withheld set
     * is what the scorer actually sees.
     */
    @Test
    void scoringOverMoreReliabilityRepeatsChangesTheFingerprint() {
        var other = new ScoringContext(
                OBJECTIVES, Set.of("held_out"),
                Set.of("probe", "held_out", "gating.run2", "gating.run3"), 0.80);
        assertThat(other.fingerprint()).isNotEqualTo(context().fingerprint());
    }

    @Test
    void changingThePromotionThresholdChangesTheFingerprint() {
        var other = new ScoringContext(OBJECTIVES, Set.of("held_out"), Set.of("probe", "held_out"), 0.75);
        assertThat(other.fingerprint()).isNotEqualTo(context().fingerprint());
    }

    /**
     * Declaration order of a name set is not a measurement, so it must not split the fingerprint.
     * Without this the same configuration would look incomparable with itself between JVM runs.
     */
    @Test
    void theOrderNamesWereDeclaredInDoesNotChangeTheFingerprint() {
        var one = new ScoringContext(
                OBJECTIVES, new java.util.LinkedHashSet<>(List.of("a", "b")), Set.of("probe"), 0.80);
        var other = new ScoringContext(
                OBJECTIVES, new java.util.LinkedHashSet<>(List.of("b", "a")), Set.of("probe"), 0.80);
        assertThat(one.fingerprint()).isEqualTo(other.fingerprint());
    }
}
