package com.dreamthought.saaa.adapters.journal;

import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
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
                Map.of("parsimony", 0.9), 0.87, FitnessDecision.PROMOTE));

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
                Map.of(), 0.87, FitnessDecision.PROMOTE));

        assertThat(Files.readString(journal))
                .contains("## earlier run")
                .contains("## 2026-07-28T09:14:02Z");
    }

    private static EvaluationEvidence evidence() {
        return new EvaluationEvidence(
                List.of(passed("publish-guard", "ok")), List.of(), Instant.parse("2026-07-28T09:14:00Z"));
    }
}
