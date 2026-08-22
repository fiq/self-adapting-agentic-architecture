package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.RequiredEvidenceResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a contract's declared {@code required_evidence} ids into observed outcomes.
 *
 * <p>A declared id names a check that must exist and pass. That keeps the declaration enforceable
 * against evidence the run already collects, rather than requiring a separate pipeline, and it means
 * the contract's vocabulary and the operator's check names have to agree. A mismatch discards rather
 * than silently passing, which is the safe direction, so the diagnostic names the id that was not
 * found.
 *
 * <p>A declared id must be a canonical signal name, lower snake_case, because it is emitted as a
 * {@code subject.invariant.<id>} audit key. Check names elsewhere in this repository are hyphenated
 * by convention, so an operator declaring required evidence must name that check to match the id
 * exactly. The alternative, translating hyphens to underscores silently, would be an unwritten
 * convention of the kind that has already caused one silent failure here.
 *
 * <p>Only declared ids are resolved. A check the contract never named produces no result and so can
 * never satisfy a gate the contract did declare.
 */
public final class DeclaredEvidenceResolver {
    public List<RequiredEvidenceResult> resolve(List<String> declared, EvaluationEvidence evidence) {
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(evidence, "evidence");

        // Fail wins for a repeated check name, matching how behaviour-case evidence is already
        // merged: keeping the last seen would let a passing run hide a failing one.
        Map<String, CheckEvidence> observed = new LinkedHashMap<>();
        for (CheckEvidence check : evidence.checks()) {
            observed.merge(check.name(), check,
                    (first, second) -> first.status() == CheckStatus.PASSED ? second : first);
        }

        List<RequiredEvidenceResult> results = new ArrayList<>();
        for (String id : declared) {
            CheckEvidence check = observed.get(id);
            if (check == null) {
                results.add(RequiredEvidenceResult.failed(id,
                        "no check named " + id + " ran, and absent evidence is not passing evidence"));
                continue;
            }
            results.add(check.status() == CheckStatus.PASSED
                    ? RequiredEvidenceResult.passed(id, check.summary())
                    : RequiredEvidenceResult.failed(id, check.summary()));
        }
        return List.copyOf(results);
    }
}
