package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.deterministic.FitnessResultFixtures.context;
import static com.dreamthought.saaa.deterministic.FitnessResultFixtures.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CHG-026. Ranking a generation.
 *
 * <p>These are assertions about a decision, so each one is proved able to fail before it is cited.
 */
final class PopulationRankingPolicyTest {
    private final PopulationRankingPolicy policy = new PopulationRankingPolicy();

    @Test
    @DisplayName("the order is the same whatever order the candidates were evaluated in")
    void rankingIsTotalAndIndependentOfEvaluationOrder() {
        var a = result("candidate-a", 0.90, FitnessDecision.PROMOTE);
        var b = result("candidate-b", 0.90, FitnessDecision.PROMOTE);
        var c = result("candidate-c", 0.95, FitnessDecision.PROMOTE);

        var oneOrder = policy.rank(List.of(a, b, c), List.of());
        var another = policy.rank(List.of(c, b, a), List.of());

        assertThat(ids(oneOrder.ranked()))
                .as("a tie must be broken by a declared rule, not by the order results arrived in")
                .containsExactly("candidate-c", "candidate-a", "candidate-b");
        assertThat(ids(another.ranked())).isEqualTo(ids(oneOrder.ranked()));
    }

    @Test
    @DisplayName("a discarded candidate never outranks a promoted one, however good its score")
    void aDiscardedCandidateNeverOutranksAPromotedOne() {
        var nearMiss = result("candidate-discarded", 0.95, FitnessDecision.DISCARD);
        var promoted = result("candidate-promoted", 0.81, FitnessDecision.PROMOTE);

        var generation = policy.rank(List.of(nearMiss, promoted), List.of());

        assertThat(ids(generation.ranked()))
                .as("ranking selects among candidates the gates promoted; it does not overrule them")
                .containsExactly("candidate-promoted", "candidate-discarded");
        assertThat(generation.winner()).contains(promoted);
        assertThat(generation.ranked().get(1).fitnessScore().rawMagnitude().doubleValue())
                .as("the discard keeps its magnitude, so a near miss stays apart from a total miss")
                .isEqualTo(0.95);
    }

    @Test
    @DisplayName("a generation with no promotion records no winner rather than the least bad candidate")
    void aGenerationWithNoPromotionRecordsNoWinner() {
        var better = result("candidate-better", 0.79, FitnessDecision.DISCARD);
        var worse = result("candidate-worse", 0.10, FitnessDecision.DISCARD);

        var generation = policy.rank(List.of(worse, better), List.of());

        assertThat(generation.winner())
                .as("promoting the best of a bad generation moves the deciding step out of fixed code")
                .isEmpty();
        assertThat(ids(generation.ranked()))
                .as("they are still ordered, because which failure to mutate from next is a real question")
                .containsExactly("candidate-better", "candidate-worse");
    }

    @Test
    @DisplayName("candidates measured against different configurations are refused, not ranked")
    void candidatesScoredUnderDifferentFingerprintsAreNotRanked() {
        var measuredOneWay = result("candidate-a", 0.90, FitnessDecision.PROMOTE, context(0.80));
        var measuredAnother = result("candidate-b", 0.90, FitnessDecision.PROMOTE, context(0.60));

        assertThatThrownBy(() -> policy.rank(List.of(measuredOneWay, measuredAnother), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(measuredOneWay.scoringFingerprint())
                .hasMessageContaining(measuredAnother.scoringFingerprint());
    }

    private static List<String> ids(List<FitnessResult> results) {
        return results.stream().map(result -> result.candidate().id()).toList();
    }
}
