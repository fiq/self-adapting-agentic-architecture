package com.dreamthought.saaa.domain;

/**
 * One observed outcome for an evidence id a {@link MutationContract} declared in
 * {@code requiredEvidence}.
 *
 * <p>This is an outcome channel, not a measurement channel. {@code PhenotypeEvidence.objectiveScores}
 * carries numbers for weighting; a declared evidence id needs a verdict and a reason, so it gets its
 * own type rather than being encoded as a score.
 *
 * <p>The diagnostic is required on both outcomes, so a discard can always say why and a pass records
 * what was actually observed rather than asserting success without evidence.
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
