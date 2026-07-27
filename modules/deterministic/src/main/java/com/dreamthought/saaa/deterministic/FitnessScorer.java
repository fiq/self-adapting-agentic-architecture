package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;

@FunctionalInterface
public interface FitnessScorer {
    FitnessResult score(Candidate candidate, EvaluationEvidence evidence);
}
