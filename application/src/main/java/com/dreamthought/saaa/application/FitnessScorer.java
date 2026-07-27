package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.Candidate;
import com.dreamthought.saaa.core.EvaluationEvidence;
import com.dreamthought.saaa.core.FitnessResult;

@FunctionalInterface
public interface FitnessScorer {
    FitnessResult score(Candidate candidate, EvaluationEvidence evidence);
}
