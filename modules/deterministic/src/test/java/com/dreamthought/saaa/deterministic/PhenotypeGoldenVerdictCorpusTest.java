package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Iterates every entry in {@link GoldenCorpus} and asserts {@link PhenotypeBridgeScorer} reproduces
 * the recorded decision and aggregate score.
 *
 * <p>Regression protection for the scorer. A future scorer-as-target slice depends on this holding:
 * if a scorer mutation changes the decision for any corpus entry, the mutation is a change to how
 * we score, which means it needs a spec change with rationale rather than a silent commit.
 *
 * <p>See {@code CHG-004} scenario {@code S7} for the coverage floor.
 */
final class PhenotypeGoldenVerdictCorpusTest {
    private static final Candidate CANDIDATE = new Candidate(
            "cand-golden",
            "MUT-golden",
            "candidate/golden",
            Path.of("/tmp/wt-golden"),
            "0123456789abcdef0123456789abcdef01234567");

    static Stream<GoldenCorpus.Entry> corpus() {
        List<GoldenCorpus.Entry> entries = GoldenCorpus.entries();
        assertThat(entries)
                .as("coverage floor: at least 9 entries per CHG-004 S7")
                .hasSizeGreaterThanOrEqualTo(9);
        return entries.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void everyCorpusEntryReproducesItsRecordedDecision(GoldenCorpus.Entry entry) {
        var scorer = new PhenotypeBridgeScorer(
                candidate -> entry.realization(),
                new ScoringConfig(
                        entry.declaredBehaviourCases(),
                        entry.maxLinesChanged(),
                        entry.benchmarkBudgets()));

        FitnessResult result = scorer.score(CANDIDATE, entry.evidence());

        assertThat(result.decision())
                .as("corpus entry '%s' — %s", entry.name(), entry.rationale())
                .isEqualTo(entry.expectedDecision());
        assertThat(result.fitnessScore().rawMagnitude())
                .as("corpus entry '%s' — %s", entry.name(), entry.rationale())
                .isEqualByComparingTo(entry.expectedRawMagnitude());
    }
}
