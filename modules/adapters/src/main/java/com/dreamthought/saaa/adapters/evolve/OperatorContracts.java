package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.deterministic.MutationContractValidator;
import com.dreamthought.saaa.deterministic.PhenotypeFitnessScorer;
import com.dreamthought.saaa.deterministic.MutationOperatorPolicy;
import com.dreamthought.saaa.domain.FitnessSignalId;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.MutationOperatorType;
import com.dreamthought.saaa.domain.MutationTarget;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Builds the contract an operator declared for a run, from the operator type plus any extra required
 * evidence, and validates it before anything else happens.
 *
 * <p>The operator's own defaults are always included. Declaring {@code repair} therefore commits the
 * run to {@code failing_case_reproduced}, {@code unit_tests_pass} and {@code regression_case_added},
 * which is exactly the case RISK-002 describes: a repair contract that could previously be realized
 * with none of them and still promote.
 *
 * <p>Each declared id names a check that must exist and pass, so the operator must name their check
 * scripts to match. That is why the ids are lower snake_case: they are emitted as
 * {@code subject.invariant.<id>} audit keys.
 */
public final class OperatorContracts {
    private OperatorContracts() {
    }

    public static MutationContract declare(
            String operatorWireName, List<String> extraRequiredEvidence, String workflowFile) {
        MutationOperatorType operator = MutationOperatorType.fromWireName(operatorWireName);
        var defaults = MutationOperatorPolicy.defaultsFor(operator);

        if (defaults.hardGates().isEmpty()) {
            throw new IllegalArgumentException("operator declares no hard gates: " + operatorWireName);
        }
        if (operator == MutationOperatorType.HILL_CLIMB
                || operator == MutationOperatorType.EXPLORATORY_LEAP) {
            throw new IllegalArgumentException(
                    "operator " + operatorWireName + " requires a search posture, which has no options yet. "
                            + "Use an operator that does not, or declare the contract another way.");
        }

        // Reject a non-canonical id here rather than at scoring. FitnessSignalId requires lower
        // snake_case because the id becomes a subject.invariant audit key, so a hyphenated id would
        // otherwise be accepted, pass contract validation, create a candidate, and only then throw
        // part-way through a run.
        for (String id : extraRequiredEvidence) {
            try {
                FitnessSignalId.invariant(id);
            } catch (IllegalArgumentException rejected) {
                throw new IllegalArgumentException(
                        "required evidence id " + id + " must be lower snake_case, because it is "
                                + "recorded as a subject.invariant audit key and must name a check "
                                + "of exactly that name", rejected);
            }
        }

        // A declared id that canonicalises onto a structural gate's audit key would be rejected by
        // the scorer mid-run. Reject it here, where the message can say which id and why.
        for (String id : extraRequiredEvidence) {
            if (PhenotypeFitnessScorer.STRUCTURAL_GATE_NAMES.contains(id)) {
                throw new IllegalArgumentException(
                        "required evidence id " + id + " is the name of a structural gate, which the "
                                + "scorer already decides; choose a different id");
            }
        }

        var requiredEvidence = new LinkedHashSet<>(defaults.requiredEvidence());
        requiredEvidence.addAll(extraRequiredEvidence);

        var contract = new MutationContract(
                "contract-" + operator.wireName(),
                operator,
                "operator-declared contract for " + workflowFile,
                new MutationTarget("file", workflowFile, "workflow"),
                List.of("workflow_definition"),
                defaults.bounds(),
                List.copyOf(requiredEvidence),
                defaults.hardGates(),
                defaults.objectives(),
                Optional.empty(),
                List.of());

        var validation = new MutationContractValidator().validate(contract);
        if (!validation.valid()) {
            throw new IllegalArgumentException(
                    "declared contract is not valid: " + String.join("; ", validation.messages()));
        }
        return contract;
    }

    /** Every id an operator's contract will require, so a caller can report them before a run. */
    public static List<String> requiredEvidenceFor(String operatorWireName, List<String> extra) {
        var ids = new ArrayList<>(
                MutationOperatorPolicy.defaultsFor(MutationOperatorType.fromWireName(operatorWireName))
                        .requiredEvidence());
        extra.stream().filter(id -> !ids.contains(id)).forEach(ids::add);
        return List.copyOf(ids);
    }
}
