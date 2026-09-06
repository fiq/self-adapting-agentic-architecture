package com.dreamthought.saaa.domain;

/**
 * A candidate in a generation that produced no evidence, and why.
 *
 * <p>Recorded rather than dropped. Absent evidence counts as failure everywhere in this loop, and a
 * generation that ranked two of three without saying so would hide a systematic failure behind a
 * plausible-looking winner.
 *
 * @param reference the candidate id when one was created, and otherwise the attempt's position in
 *                  the generation. A realisation that failed before a candidate existed has no id,
 *                  and inventing one would imply a candidate that was never created
 * @param reason    what stopped it, in words a later reader can act on
 */
public record UnevaluatedCandidate(String reference, String reason) {
    public UnevaluatedCandidate {
        reference = Require.nonBlank(reference, "reference");
        reason = Require.nonBlank(reason, "reason");
    }
}
