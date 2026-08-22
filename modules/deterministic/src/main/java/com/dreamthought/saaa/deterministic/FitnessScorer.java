package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import java.util.Optional;
import com.dreamthought.saaa.domain.MutationContract;

/**
 * Scores an evaluated candidate.
 *
 * <p>The contract is the third parameter rather than an overload with a default, deliberately. A
 * default that ignored it would let an implementor silently drop what the operator declared, and the
 * candidate would promote as though its declared evidence had been checked. Requiring every
 * implementor to take the contract makes ignoring it a visible choice. See RISK-002 and CHG-019.
 */
@FunctionalInterface
public interface FitnessScorer {
    FitnessResult score(
            Candidate candidate, EvaluationEvidence evidence, Optional<MutationContract> contract);
}
