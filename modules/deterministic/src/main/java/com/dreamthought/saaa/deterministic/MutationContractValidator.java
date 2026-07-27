package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.MutationBounds;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.MutationOperatorType;
import com.dreamthought.saaa.domain.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic gate between a proposed mutation contract and candidate creation. It accepts only
 * contracts that stay inside the operator's declared bounds, target a repo-relative locus, carry the
 * operator's required evidence and hard gates, and claim no candidate authority.
 */
public final class MutationContractValidator {
    private static final String AUTHORITY_MESSAGE =
            "mutation contract must not contain approval, scoring, promotion, discard or rollback authority";
    private static final String TARGET_FILE_MESSAGE =
            "target file must be repo-relative and must not traverse parent directories";
    private static final String OBJECTIVES_MESSAGE =
            "fitness objectives must match the deterministic default objectives";
    private static final String PARENT_SEGMENT = "..";

    public ValidationResult validate(MutationContract contract) {
        Objects.requireNonNull(contract, "contract");

        List<String> messages = new ArrayList<>();
        rejectAuthority(messages, contract);
        rejectUnsafeTargetFile(messages, contract);
        rejectExcessiveBounds(messages, contract);
        requireOperatorEvidenceAndGates(messages, contract);
        requireDeterministicObjectives(messages, contract);
        requireSearchPosture(messages, contract);
        requirePrimaryLocus(messages, contract);

        if (messages.isEmpty()) {
            return ValidationResult.passed();
        }
        return new ValidationResult(false, messages);
    }

    /** Scans every model-authored field the contract carries forward, not just the hypothesis. */
    private static void rejectAuthority(List<String> messages, MutationContract contract) {
        List<String> text = new ArrayList<>(List.of(
                contract.id(),
                contract.hypothesis(),
                contract.target().kind(),
                contract.target().file(),
                contract.target().symbol()
        ));
        text.addAll(contract.loci());
        text.addAll(contract.requiredEvidence());
        text.addAll(contract.hardGates());
        contract.searchPosture().ifPresent(posture -> text.addAll(List.of(
                posture.parentCandidateId(),
                posture.objectiveFocus(),
                posture.expectedDelta(),
                posture.riskBudget()
        )));
        for (var parentTrait : contract.parentTraits()) {
            text.addAll(List.of(parentTrait.parentCandidateId(), parentTrait.trait(), parentTrait.evidenceId()));
        }

        if (AuthorityLanguage.isPresentIn(String.join("\n", text))) {
            messages.add(AUTHORITY_MESSAGE);
        }
    }

    private static void rejectUnsafeTargetFile(List<String> messages, MutationContract contract) {
        String file = contract.target().file();
        boolean traverses = file.startsWith("/")
                || file.contains("\\")
                || List.of(file.split("/", -1)).contains(PARENT_SEGMENT);
        if (traverses) {
            messages.add(TARGET_FILE_MESSAGE);
        }
    }

    private static void rejectExcessiveBounds(List<String> messages, MutationContract contract) {
        MutationOperatorType operator = contract.operator();
        MutationBounds bounds = contract.bounds();
        MutationBounds allowed = MutationOperatorPolicy.defaultsFor(operator).bounds();
        String suffix = " for " + operator.wireName();

        if (bounds.maxFilesChanged() > allowed.maxFilesChanged()) {
            messages.add("maxFilesChanged must be at most " + allowed.maxFilesChanged() + suffix);
        }
        if (bounds.maxLinesChanged() > allowed.maxLinesChanged()) {
            messages.add("maxLinesChanged must be at most " + allowed.maxLinesChanged() + suffix);
        }
        if (bounds.publicApiChange() && !allowed.publicApiChange()) {
            messages.add("publicApiChange is not allowed" + suffix);
        }
        if (bounds.persistenceChange() && !allowed.persistenceChange()) {
            messages.add("persistenceChange is not allowed" + suffix);
        }
        if (bounds.productionConfigChange() && !allowed.productionConfigChange()) {
            messages.add("productionConfigChange is not allowed" + suffix);
        }
    }

    private static void requireOperatorEvidenceAndGates(List<String> messages, MutationContract contract) {
        MutationOperatorDefaults defaults = MutationOperatorPolicy.defaultsFor(contract.operator());
        for (String evidence : defaults.requiredEvidence()) {
            if (!contract.requiredEvidence().contains(evidence)) {
                messages.add("required evidence is missing: " + evidence);
            }
        }
        for (String gate : defaults.hardGates()) {
            if (!contract.hardGates().contains(gate)) {
                messages.add("hard gate is missing: " + gate);
            }
        }
    }

    private static void requireDeterministicObjectives(List<String> messages, MutationContract contract) {
        if (!contract.objectives().equals(MutationOperatorPolicy.DEFAULT_OBJECTIVES)) {
            messages.add(OBJECTIVES_MESSAGE);
        }
    }

    private static void requireSearchPosture(List<String> messages, MutationContract contract) {
        if (MutationOperatorPolicy.requiresSearchPosture(contract.operator()) && contract.searchPosture().isEmpty()) {
            messages.add(contract.operator().wireName()
                    + " requires search posture with parent candidate, objective focus and risk budget");
        }
    }

    private static void requirePrimaryLocus(List<String> messages, MutationContract contract) {
        if (contract.loci().isEmpty()) {
            messages.add("mutation contract must declare at least one locus");
        }
    }
}
