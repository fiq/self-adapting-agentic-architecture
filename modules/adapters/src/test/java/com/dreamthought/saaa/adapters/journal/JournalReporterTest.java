package com.dreamthought.saaa.adapters.journal;

import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JournalReporterTest {
    /** Any scoring context; these tests assert reporting and transport, not comparability. */
    private static final com.dreamthought.saaa.domain.ScoringContext TEST_SCORING_CONTEXT =
            new com.dreamthought.saaa.domain.ScoringContext(
                    java.util.List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                    java.util.Set.of(), java.util.Set.of(), 0.80,
                    java.util.Set.of("case"), 80, java.util.Map.of());

    private static final Candidate CANDIDATE =
            new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", Path.of("/tmp/wt"), "abc1234");

    @Test
    void appendsATraceableEntryForOneRun(@TempDir Path dir) throws IOException {
        Path journal = dir.resolve("journal.md");
        var reporter = new JournalReporter(
                journal, Clock.fixed(Instant.parse("2026-07-28T09:14:02Z"), ZoneOffset.UTC));

        reporter.proposed(new Mutation("MUT-1", "tighten the publish guard",
                MutationScope.WORKFLOW_DEFINITION, "new content"));
        reporter.candidateCreated(CANDIDATE);
        reporter.evidenceCollected(evidence());
        reporter.scored(new FitnessResult(CANDIDATE, evidence(),
                Map.of("subject.objective.parsimony", 0.9), FitnessScore.of(0.87, FitnessDecision.PROMOTE), TEST_SCORING_CONTEXT));

        String written = Files.readString(journal);
        assertThat(written)
                .contains("## 2026-07-28T09:14:02Z")
                .contains("tighten the publish guard")
                .contains("abc1234")
                .contains("publish-guard")
                .contains("0.87")
                .contains("PROMOTE");
    }

    @Test
    void appendsRatherThanOverwritingPreviousRuns(@TempDir Path dir) throws IOException {
        Path journal = dir.resolve("journal.md");
        Files.writeString(journal, "# Journal\n\n## earlier run\n");
        var reporter = new JournalReporter(
                journal, Clock.fixed(Instant.parse("2026-07-28T09:14:02Z"), ZoneOffset.UTC));

        reporter.proposed(new Mutation("MUT-1", "tighten the publish guard",
                MutationScope.WORKFLOW_DEFINITION, "new content"));
        reporter.candidateCreated(CANDIDATE);
        reporter.evidenceCollected(evidence());
        reporter.scored(new FitnessResult(CANDIDATE, evidence(),
                Map.of(), FitnessScore.of(0.87, FitnessDecision.PROMOTE), TEST_SCORING_CONTEXT));

        assertThat(Files.readString(journal))
                .contains("## earlier run")
                .contains("## 2026-07-28T09:14:02Z");
    }

    private static EvaluationEvidence evidence() {
        return new EvaluationEvidence(
                List.of(passed("publish-guard", "ok")), List.of(), Instant.parse("2026-07-28T09:14:00Z"));
    }
    /**
     * S7 in the narrative surface. The journal is not the audit record — the ledger and the Git
     * commits are — but it is what a person actually reads after a run, and a generation that told
     * them a winner without telling them the order, the spread or how many candidates produced
     * evidence would be reporting a conclusion with none of what supports it.
     */
    @Test
    void aGenerationEntryCarriesTheOrderWinnerFingerprintEvidenceCountAndSpread(@TempDir Path dir)
            throws IOException {
        Path journal = dir.resolve("journal.md");
        var reporter = new JournalReporter(journal, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var promoted = scored("cand-1", 0.90, FitnessDecision.PROMOTE);
        var discarded = scored("cand-2", 0.95, FitnessDecision.DISCARD);

        reporter.generationRanked(new com.dreamthought.saaa.domain.RankedGeneration(
                List.of(promoted, discarded),
                List.of(new com.dreamthought.saaa.domain.UnevaluatedCandidate(
                        "attempt-3-of-3", "IllegalStateException: worktree already exists"))));

        String written = Files.readString(journal);
        assertThat(written)
                .contains("| evidence | 2 of 3 candidates |")
                .contains("| fingerprint | " + promoted.scoringFingerprint() + " |")
                .contains("| spread | 0.05 |")
                .contains("| winner | cand-1 |")
                // Raw, not rounded. CHG-023 left rendering to the console alone, so the journal
                // carries the magnitude the ranking actually used.
                .contains("1. cand-1  0.9  PROMOTE")
                .contains("2. cand-2  0.95  DISCARD")
                .contains("- no evidence: attempt-3-of-3");
    }

    /**
     * A generation that promoted nothing says so. Naming its best discard as a winner would move the
     * deciding step out of fixed code and into whoever reads the journal.
     */
    @Test
    void aGenerationThatPromotedNothingRecordsNoWinner(@TempDir Path dir) throws IOException {
        Path journal = dir.resolve("journal.md");
        var reporter = new JournalReporter(journal, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        reporter.generationRanked(new com.dreamthought.saaa.domain.RankedGeneration(
                List.of(scored("cand-1", 0.79, FitnessDecision.DISCARD)), List.of()));

        assertThat(Files.readString(journal)).contains("| winner | none promoted |");
    }

    private static FitnessResult scored(String candidateId, double magnitude, FitnessDecision decision) {
        return new FitnessResult(
                new Candidate(candidateId, "MUT-" + candidateId, "candidate/" + candidateId,
                        Path.of("/tmp", candidateId), "abc1234"),
                new EvaluationEvidence(List.of(), List.of(), Instant.EPOCH),
                Map.of(),
                FitnessScore.of(magnitude, decision),
                TEST_SCORING_CONTEXT);
    }
}
