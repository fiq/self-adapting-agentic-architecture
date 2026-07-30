package com.dreamthought.saaa.adapters.journal;

import com.dreamthought.saaa.deterministic.CandidateDecisionSink;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Records the outcome without acting on it. Creating a promoted Git ref is CHG-002 task T5; this
 * slice deliberately stops at recording so promotion semantics stay unproven rather than assumed.
 */
public final class JournalDecisionSink implements CandidateDecisionSink {
    private final List<String> decisions = new ArrayList<>();

    @Override
    public void promote(Candidate candidate, FitnessResult result) {
        record("PROMOTE", candidate, result);
    }

    @Override
    public void discard(Candidate candidate, FitnessResult result) {
        record("DISCARD", candidate, result);
    }

    public List<String> decisions() {
        return List.copyOf(decisions);
    }

    private void record(String decision, Candidate candidate, FitnessResult result) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(result, "result");
        decisions.add(decision + " " + candidate.id() + " " + result.aggregateScore());
    }
}
