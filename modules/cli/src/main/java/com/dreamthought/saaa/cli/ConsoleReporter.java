package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.RankedGeneration;
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
        out.printf("  score      %.2f%n", result.fitnessScore().rawMagnitude());
        out.printf("  %s%n", result.decision());
        out.flush();
    }

    /**
     * The generation's ranking, printed once every candidate has been evaluated and reported.
     *
     * <p>Four things, because each answers a question the transcript otherwise leaves open: the
     * order, so the selection can be checked rather than trusted; how many of N produced evidence,
     * so a generation that ranked two of three cannot hide a systematic failure behind a
     * plausible-looking winner; the spread, which is what says whether ranking discriminated at all;
     * and the winner, which is empty when nothing promoted because ranking selects among promotions
     * and is not a second opinion on the gates.
     */
    @Override
    public void generationRanked(RankedGeneration generation) {
        Objects.requireNonNull(generation, "generation");
        out.printf("  generation %d of %d candidates produced evidence%n",
                generation.evaluatedCount(), generation.requestedCount());
        int position = 1;
        for (FitnessResult result : generation.ranked()) {
            out.printf("  rank %-5d %-40s %.2f  %s%n", position++, result.candidate().id(),
                    result.fitnessScore().rawMagnitude(), result.decision());
        }
        generation.unevaluated().forEach(candidate ->
                out.printf("  no evidence %s  %s%n", candidate.reference(), candidate.reason()));
        generation.spread().ifPresent(spread -> out.printf("  spread     %.2f%n", spread));
        out.printf("  winner     %s%n",
                generation.winner().map(result -> result.candidate().id()).orElse("none promoted"));
        out.flush();
    }
}
