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
 * @param usage     what the attempt cost when the model had answered before it failed, and empty
 *                  when it failed first. Empty is not zero: it means no usage is known
 */
public record UnevaluatedCandidate(String reference, String reason, java.util.Optional<AgentUsage> usage) {
    public UnevaluatedCandidate {
        reference = Require.nonBlank(reference, "reference");
        reason = Require.nonBlank(reason, "reason");
        usage = java.util.Objects.requireNonNull(usage, "usage");
    }

    public UnevaluatedCandidate(String reference, String reason) {
        this(reference, reason, java.util.Optional.empty());
    }

    /**
     * One line saying what the attempt cost, for the journal and the console, or empty when no
     * usage is known. Only the figures actually observed are named, so nothing reads as zero.
     */
    public java.util.Optional<String> usageSummary() {
        return usage.map(spent -> {
            var parts = new java.util.ArrayList<String>();
            spent.priceIdentity().ifPresent(price -> parts.add(price.provider() + " / " + price.model()));
            spent.inputTokens().ifPresent(tokens -> parts.add("input tokens " + tokens));
            spent.outputTokens().ifPresent(tokens -> parts.add("output tokens " + tokens));
            spent.creditsConsumed().ifPresent(credits -> parts.add("credits " + credits.toPlainString()));
            spent.wallClockMillis().ifPresent(millis -> parts.add("wall clock " + millis + " ms"));
            spent.retries().ifPresent(retries -> parts.add("retries " + retries));
            return parts.isEmpty() ? "usage unavailable" : "usage " + String.join("; ", parts);
        });
    }
}
