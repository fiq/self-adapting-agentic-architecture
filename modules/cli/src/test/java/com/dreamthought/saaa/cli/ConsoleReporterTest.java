package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import com.dreamthought.saaa.domain.RankedGeneration;
import com.dreamthought.saaa.domain.UnevaluatedCandidate;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ConsoleReporterTest {
    /** Any scoring context; this test asserts rendering, not comparability. */
    private static final com.dreamthought.saaa.domain.ScoringContext TEST_SCORING_CONTEXT =
            new com.dreamthought.saaa.domain.ScoringContext(
                    java.util.List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                    java.util.Set.of(), java.util.Set.of(), 0.80,
                    java.util.Set.of("case"), 80, java.util.Map.of());

    @Test
    void roundsRawMagnitudeOnlyWhenRenderingTheConsoleReport() {
        var output = new StringWriter();
        var reporter = new ConsoleReporter(new PrintWriter(output));
        var result = new FitnessResult(
                new Candidate("candidate-1", "mutation-1", "candidate/1", Path.of("/tmp/candidate"), "abc123"),
                new EvaluationEvidence(List.of(), List.of(), Instant.EPOCH), Map.of(),
                new FitnessScore(new BigDecimal("0.5949"), FitnessDecision.DISCARD),
                TEST_SCORING_CONTEXT);

        reporter.scored(result);

        assertThat(output.toString()).isEqualTo("  score      0.59\n  DISCARD\n");
        assertThat(result.fitnessScore().rawMagnitude()).isEqualByComparingTo("0.5949");
    }
    /**
     * S7. The generation summary is what a person running {@code --candidates} actually sees, and it
     * has to carry the four things the ranking is for: the order, the winner, how many of N produced
     * evidence, and the spread. The spread is there because ADR-0002 names "population ships but
     * ranking is not measurably useful" as a revisit trigger, and a run whose candidates all land on
     * the same score is that trigger firing rather than a detail worth omitting.
     *
     * <p>The discarded candidate has the higher magnitude on purpose: it must appear below the
     * promoted one and it must not be named as the winner.
     */
    @Test
    void reportsTheGenerationOrderWinnerEvidenceCountAndSpread() {
        var output = new StringWriter();
        var reporter = new ConsoleReporter(new PrintWriter(output));
        var promoted = result("candidate-run-c1", "0.81", FitnessDecision.PROMOTE);
        var discarded = result("candidate-run-c2", "0.95", FitnessDecision.DISCARD);
        var generation = new RankedGeneration(
                List.of(promoted, discarded),
                List.of(new UnevaluatedCandidate("attempt-3-of-3", "IllegalStateException: no worktree")));

        reporter.generationRanked(generation);

        assertThat(output.toString().lines().toList()).containsExactly(
                "  generation 2 of 3 candidates produced evidence",
                "  rank 1     candidate-run-c1                         0.81  PROMOTE",
                "  rank 2     candidate-run-c2                         0.95  DISCARD",
                "  no evidence attempt-3-of-3  IllegalStateException: no worktree",
                "  spread     0.14",
                "  winner     candidate-run-c1");
    }

    /**
     * S4. Ranking selects among the candidates the gates promoted; it is not a second opinion on the
     * gates. A generation where nothing promoted must say so rather than name its best discard,
     * because printing a winner there would move the deciding step out of fixed code and into
     * whoever reads the transcript.
     */
    @Test
    void namesNoWinnerWhenNothingInTheGenerationPromoted() {
        var output = new StringWriter();
        var reporter = new ConsoleReporter(new PrintWriter(output));
        var generation = new RankedGeneration(
                List.of(result("candidate-run-c1", "0.79", FitnessDecision.DISCARD)), List.of());

        reporter.generationRanked(generation);

        assertThat(output.toString()).contains("  winner     none promoted");
    }

    private static FitnessResult result(String candidateId, String magnitude, FitnessDecision decision) {
        return new FitnessResult(
                new Candidate(candidateId, "mutation-1", "candidate/1", Path.of("/tmp/" + candidateId), "abc123"),
                new EvaluationEvidence(List.of(), List.of(), Instant.EPOCH), Map.of(),
                new FitnessScore(new BigDecimal(magnitude), decision),
                TEST_SCORING_CONTEXT);
    }
}
