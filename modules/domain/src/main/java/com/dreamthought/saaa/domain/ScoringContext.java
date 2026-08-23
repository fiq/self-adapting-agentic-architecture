package com.dreamthought.saaa.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a fitness magnitude was measured against, reduced to one comparable fingerprint.
 *
 * <p>A magnitude only means something beside the configuration that produced it. Two runs scoring
 * the same candidate under different objective weights, or with a different set of cases held out,
 * produce numbers that look comparable and are not. {@code RISK-007} records what that costs: before
 * {@code CHG-021} a discarded candidate stored {@code 0.0} and afterwards it stored its weighted
 * magnitude, so one field name carried two different measurements and nothing in the schema
 * distinguished them.
 *
 * <p>{@code CHG-024} changes the meaning of {@code task_success} for every future run, which is the
 * same kind of boundary. Rather than repeat the mistake, every score now carries the fingerprint of
 * the configuration it was produced under, and consumers that rank or aggregate refuse to mix them.
 *
 * <p>The fingerprint deliberately covers more than the objective ids. Hashing the ids alone would
 * treat two runs with different weights, a different held-out set or a different probe set as
 * comparable, which is exactly the silent mixing this type exists to prevent.
 *
 * <p>This is a domain value and must stay free of the deterministic layer's {@code ScoringConfig}:
 * deterministic code constructs it, the domain only carries it.
 */
public record ScoringContext(
        List<FitnessObjective> objectives,
        Set<String> heldOutCaseNames,
        // Every check withheld from the deterministic-checks gate: safety probes, reliability
        // repeats and held-out cases. Named for what it is rather than for probes alone, because the
        // scorer sees the withheld set and not the configuration that produced it. It also captures
        // the reliability run count implicitly: raising the count adds repeat-run names here, so a
        // run scored over more repeats cannot fingerprint the same as one scored over fewer.
        Set<String> withheldCheckNames,
        double promotionThreshold
) {
    /**
     * A record written before scoring context was captured. Such a record is readable as history but
     * is never comparable with a fingerprinted one, because there is no honest way to recover which
     * configuration produced it. Backfilling a fingerprint would invent provenance.
     */
    public static final String LEGACY_UNVERSIONED = "legacy-unversioned";

    public ScoringContext {
        objectives = List.copyOf(Objects.requireNonNull(objectives, "objectives"));
        heldOutCaseNames = Set.copyOf(Objects.requireNonNull(heldOutCaseNames, "heldOutCaseNames"));
        withheldCheckNames = Set.copyOf(Objects.requireNonNull(withheldCheckNames, "withheldCheckNames"));
        if (!Double.isFinite(promotionThreshold)) {
            throw new IllegalArgumentException("promotionThreshold must be finite");
        }
    }

    /**
     * A short, stable hash of everything that changes what a magnitude means.
     *
     * <p>Name sets are sorted before hashing because declaration order does not change the
     * measurement, while objectives keep their declared order because the weighted sum is defined
     * over that order. Two configurations that differ only in how their names were listed therefore
     * fingerprint the same, and two that differ in any weight, threshold, held-out case, probe or run
     * count do not.
     */
    public String fingerprint() {
        var canonical = new StringBuilder();
        for (FitnessObjective objective : objectives) {
            field(canonical, objective.id());
            field(canonical, Double.toString(objective.weight()));
        }
        new TreeSet<>(heldOutCaseNames).forEach(name -> field(canonical, "held-out:" + name));
        new TreeSet<>(withheldCheckNames).forEach(name -> field(canonical, "withheld:" + name));
        field(canonical, "threshold:" + promotionThreshold);
        return HexFormat.of().formatHex(digest(canonical.toString())).substring(0, 16);
    }

    /**
     * Appends one length-prefixed field.
     *
     * <p>Joining with delimiters is not safe here. Objective ids and case names are only required to
     * be non-blank, so a separator can appear inside a value: the single objective
     * {@code ("a=0.1,b", 0.2)} joins to exactly the same string as the two objectives
     * {@code ("a", 0.1), ("b", 0.2)}, and the held-out set {@code {"a,b"}} to the same string as
     * {@code {"a", "b"}}. Those are deterministic pre-hash collisions between configurations this
     * type exists to tell apart. Prefixing each field with its length makes the encoding
     * unambiguous whatever the value contains.
     */
    private static void field(StringBuilder canonical, String value) {
        canonical.append(value.length()).append(':').append(value);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to fingerprint a scoring context", exception);
        }
    }
}
