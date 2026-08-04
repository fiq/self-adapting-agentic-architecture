package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.RetrievalBundle;
import java.io.PrintWriter;
import java.util.Objects;

/** Prints one line per stage. Printing belongs here, never in the deterministic layer. */
public final class ConsoleReporter implements EvolutionReporter {
    private final PrintWriter out;

    public ConsoleReporter(PrintWriter out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void proposed(Mutation mutation) {
        out.printf("  propose    %s  %s%n", mutation.id(), mutation.summary());
    }

    @Override
    public void retrievalPrepared(RetrievalBundle retrieval) {
        out.printf("  retrieval %s  config=%s evidence=%d tokens~%d%n",
                retrieval.mode(), retrieval.configurationId(), retrieval.capsules().size(), retrieval.estimatedTokens());
    }

    @Override
    public void candidateCreated(Candidate candidate) {
        out.printf("  candidate  %s  %s%n", candidate.id(), candidate.commitSha());
    }

    @Override
    public void evidenceCollected(EvaluationEvidence evidence) {
        evidence.checks().forEach(check ->
                out.printf("  check      %-24s %s%n", check.name(), check.status()));
    }

    @Override
    public void scored(FitnessResult result) {
        out.printf("  score      %.2f%n", result.aggregateScore());
        out.printf("  %s%n", result.decision());
        out.flush();
    }
}
