package com.dreamthought.saaa.adapters.journal;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Appends a human-readable entry per run.
 *
 * <p>The journal is a narrative view, never the source of truth for a decision. Git commits and the
 * experiment metadata store hold provenance, which is why a later slice can safely compact this file
 * for readability without weakening the audit trail.
 */
public final class JournalReporter implements EvolutionReporter {
    private final Path journalFile;
    private final Clock clock;

    private Mutation mutation;
    private Candidate candidate;
    private EvaluationEvidence evidence;

    public JournalReporter(Path journalFile, Clock clock) {
        this.journalFile = Objects.requireNonNull(journalFile, "journalFile");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void proposed(Mutation mutation) {
        this.mutation = mutation;
    }

    @Override
    public void candidateCreated(Candidate candidate) {
        this.candidate = candidate;
    }

    @Override
    public void evidenceCollected(EvaluationEvidence evidence) {
        this.evidence = evidence;
    }

    @Override
    public void scored(FitnessResult result) {
        Objects.requireNonNull(result, "result");
        append(entry(result));
    }

    private String entry(FitnessResult result) {
        String checks = evidence == null
                ? "none"
                : evidence.checks().stream()
                        .map(check -> check.name() + " " + check.status())
                        .collect(Collectors.joining(", "));
        String hypothesis = mutation == null ? "unknown" : mutation.summary();
        String commit = candidate == null ? "unknown" : candidate.commitSha();
        String candidateId = candidate == null ? "unknown" : candidate.id();

        return """

                ## %s  %s

                **Hypothesis** %s

                | | |
                |---|---|
                | commit | %s |
                | checks | %s |
                | score | %.2f |
                | decision | %s |

                Scored %.2f against a threshold of 0.80.
                """.formatted(
                        Instant.now(clock),
                        candidateId,
                        hypothesis,
                        commit,
                        checks,
                        result.aggregateScore(),
                        result.decision(),
                        result.aggregateScore());
    }

    private void append(String text) {
        try {
            Files.createDirectories(journalFile.toAbsolutePath().getParent());
            Files.writeString(
                    journalFile, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to append to journal: " + journalFile, exception);
        }
    }
}
