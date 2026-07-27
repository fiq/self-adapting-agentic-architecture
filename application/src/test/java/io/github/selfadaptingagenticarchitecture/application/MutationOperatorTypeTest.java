package io.github.selfadaptingagenticarchitecture.application;

import static io.github.selfadaptingagenticarchitecture.core.MutationOperatorType.TARGETED_BEHAVIOR_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.selfadaptingagenticarchitecture.core.FitnessObjective;
import io.github.selfadaptingagenticarchitecture.core.MutationBounds;
import io.github.selfadaptingagenticarchitecture.core.MutationOperatorType;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class MutationOperatorTypeTest {
    @Test
    void mapsOperatorToDefaultBoundsEvidenceAndFitnessDimensions() {
        assertThat(Stream.of(MutationOperatorType.values()).map(MutationOperatorPolicy::defaultsFor))
                .allSatisfy(defaults -> {
                    assertThat(defaults.bounds().maxFilesChanged()).isPositive();
                    assertThat(defaults.bounds().maxLinesChanged()).isPositive();
                    assertThat(defaults.requiredEvidence()).isNotEmpty();
                    assertThat(defaults.objectives())
                            .extracting(FitnessObjective::id)
                            .containsExactly(
                                    "task_success",
                                    "reliability",
                                    "cost_latency_budget",
                                    "behavioral_safety",
                                    "parsimony"
                            );
                    assertThat(defaults.objectives().stream().mapToDouble(FitnessObjective::weight).sum())
                            .isEqualTo(1.0);
                });

        assertThat(MutationOperatorPolicy.defaultsFor(TARGETED_BEHAVIOR_CHANGE).bounds())
                .isEqualTo(new MutationBounds(2, 80, false, false, false));
    }

    @Test
    void rejectsUnknownOperatorsIncludingConceptualCrossover() {
        assertThatThrownBy(() -> MutationOperatorType.fromWireName("rewrite-everything"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported mutation operator: rewrite-everything");

        assertThatThrownBy(() -> MutationOperatorType.fromWireName("conceptual-crossover"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported mutation operator: conceptual-crossover");
        assertThat(Arrays.stream(MutationOperatorType.values()).map(MutationOperatorType::wireName))
                .doesNotContain("conceptual-crossover");
    }
}
