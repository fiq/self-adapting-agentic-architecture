package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import java.util.List;
import java.util.Objects;

/** Fans one run's events out to several reporters. */
public final class CompositeReporter implements EvolutionReporter {
    private final List<EvolutionReporter> reporters;

    public CompositeReporter(List<EvolutionReporter> reporters) {
        this.reporters = List.copyOf(Objects.requireNonNull(reporters, "reporters"));
    }

    @Override
    public void proposed(Mutation mutation) {
        reporters.forEach(reporter -> reporter.proposed(mutation));
    }

    @Override
    public void candidateCreated(Candidate candidate) {
        reporters.forEach(reporter -> reporter.candidateCreated(candidate));
    }

    @Override
    public void evidenceCollected(EvaluationEvidence evidence) {
        reporters.forEach(reporter -> reporter.evidenceCollected(evidence));
    }

    @Override
    public void scored(FitnessResult result) {
        reporters.forEach(reporter -> reporter.scored(result));
    }
}
