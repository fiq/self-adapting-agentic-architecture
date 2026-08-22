package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationOperatorType.TARGETED_BEHAVIOR_CHANGE;

import com.dreamthought.saaa.domain.MutationBounds;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.MutationTarget;
import java.util.List;
import java.util.Optional;

/** A minimal accepted contract, so tests can state only the required evidence they care about. */
final class ContractFixture {
    private ContractFixture() {
    }

    static MutationContract declaring(String... requiredEvidence) {
        return new MutationContract(
                "MUT-1", TARGETED_BEHAVIOR_CHANGE, "tighten the guard",
                new MutationTarget("file", "workflow.txt", "guard"),
                List.of("workflow_definition"),
                new MutationBounds(2, 80, false, false, false),
                List.of(requiredEvidence),
                List.of("subject.invariant.deterministic_checks_pass",
                        "subject.invariant.required_evidence_present"),
                MutationOperatorPolicy.DEFAULT_OBJECTIVES, Optional.empty(), List.of());
    }
}
