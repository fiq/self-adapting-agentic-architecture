package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.EvaluationEvidence;
import io.github.selfadaptingagenticarchitecture.core.FitnessResult;

@FunctionalInterface
public interface FitnessScorer {
    FitnessResult score(Candidate candidate, EvaluationEvidence evidence);
}
