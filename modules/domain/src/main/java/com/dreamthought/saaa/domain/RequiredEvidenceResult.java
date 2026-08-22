package com.dreamthought.saaa.domain;

/**
 * One observed outcome for an evidence id a {@link MutationContract} declared in
 * {@code requiredEvidence}.
 *
 * <p>This is an outcome channel, not a measurement channel. {@code PhenotypeEvidence.objectiveScores}
 * carries numbers for weighting; a declared evidence id needs a verdict and a reason, so it gets its
 * own type rather than being encoded as a score.
 *
 * <p>The diagnostic is required on both outcomes so that a caller supplying evidence must state what
 * it observed rather than asserting an outcome bare. It does not currently reach the scored result:
 * {@code FitnessResult} carries only {@code Map<String, Double>}, so a discard records 0.0 against
 * the id and not the reason. Giving the discard reason an output carrier is CHG-014 task T8.
 */
public record RequiredEvidenceResult(String evidenceId, boolean passed, String diagnostic) {
    public RequiredEvidenceResult {
        evidenceId = Require.nonBlank(evidenceId, "evidenceId");
        diagnostic = Require.nonBlank(diagnostic, "diagnostic");
    }

    public static RequiredEvidenceResult passed(String evidenceId, String diagnostic) {
        return new RequiredEvidenceResult(evidenceId, true, diagnostic);
    }

    public static RequiredEvidenceResult failed(String evidenceId, String diagnostic) {
        return new RequiredEvidenceResult(evidenceId, false, diagnostic);
    }
}
