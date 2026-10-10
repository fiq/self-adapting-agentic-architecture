package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import java.util.Objects;

/** The proposed mutation and the deterministic evaluation evidence it produced. */
public record CandidateEvaluation(Mutation mutation, FitnessResult fitnessResult) {
    public CandidateEvaluation {
        mutation = Objects.requireNonNull(mutation, "mutation");
        fitnessResult = Objects.requireNonNull(fitnessResult, "fitnessResult");
    }
}
