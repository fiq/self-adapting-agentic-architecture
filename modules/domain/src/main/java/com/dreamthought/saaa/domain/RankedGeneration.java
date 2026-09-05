package com.dreamthought.saaa.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * One generation of candidates, evaluated against one baseline and ordered best first.
 *
 * <p>The winner, the fingerprint and the spread are derived rather than stored, so no generation can
 * be built whose winner disagrees with its own ordering or whose recorded fingerprint is not the one
 * its candidates were measured under.
 *
 * <p>The point of ranking is selection among candidates the gates already promoted. It is not a
 * second opinion on the gates: {@link #winner()} is empty when nothing promoted, because promoting
 * the best of a bad generation would move the deciding step back out of fixed code.
 *
 * @param ranked      every candidate that produced evidence, best first
 * @param unevaluated every candidate that produced none, with the reason. Absent evidence counts as
 *                    failure everywhere in this loop, so these are recorded rather than dropped: a
 *                    generation that ranked two of three without saying so would hide a systematic
 *                    failure behind a plausible-looking winner
 */
public record RankedGeneration(List<FitnessResult> ranked, List<UnevaluatedCandidate> unevaluated) {
    public RankedGeneration {
        ranked = List.copyOf(Objects.requireNonNull(ranked, "ranked"));
        unevaluated = List.copyOf(Objects.requireNonNull(unevaluated, "unevaluated"));

        // Two candidates sharing an id would make the tie-break below non-total, so the order would
        // stop being reproducible from the record. It is also the symptom RISK-003 describes.
        Set<String> ids = ranked.stream().map(result -> result.candidate().id())
                .collect(Collectors.toCollection(TreeSet::new));
        if (ids.size() != ranked.size()) {
            throw new IllegalArgumentException(
                    "two candidates in one generation share an id, so the ranking cannot be total");
        }

        // A generation is a comparison, and CHG-024 stamped the fingerprint so that scores measured
        // under different objectives, weights, thresholds, probes or budgets are never compared. A
        // single-candidate run had nothing to compare against; this is where that guard earns its
        // keep.
        Set<String> fingerprints = ranked.stream().map(FitnessResult::scoringFingerprint)
                .collect(Collectors.toCollection(TreeSet::new));
        if (fingerprints.size() > 1) {
            throw new IllegalArgumentException(
                    "a generation cannot mix scoring fingerprints: " + String.join(", ", fingerprints));
        }

        for (int i = 1; i < ranked.size(); i++) {
            if (bestFirst().compare(ranked.get(i - 1), ranked.get(i)) > 0) {
                throw new IllegalArgumentException(
                        "ranked is not in best-first order at position " + i + ": "
                                + ranked.get(i - 1).candidate().id() + " before "
                                + ranked.get(i).candidate().id());
            }
        }
    }

    /**
     * The order a generation is ranked in: promotions ahead of discards, then larger magnitudes,
     * then candidate id.
     *
     * <p>The id tie-break is what makes the order total. Without it two equally scored candidates
     * would be ordered by the sort's input order, and the ranking would depend on the order the
     * candidates happened to be evaluated in — the shape that has produced two defects here already.
     */
    public static Comparator<FitnessResult> bestFirst() {
        return Comparator.comparing(FitnessResult::fitnessScore).reversed()
                .thenComparing(result -> result.candidate().id());
    }

    /** The best candidate the gates promoted, or empty when none did. */
    public Optional<FitnessResult> winner() {
        return ranked.stream().findFirst()
                .filter(result -> result.decision() == FitnessDecision.PROMOTE);
    }

    /** The fingerprint every candidate here was measured under, absent when none were evaluated. */
    public Optional<String> scoringFingerprint() {
        return ranked.stream().findFirst().map(FitnessResult::scoringFingerprint);
    }

    /**
     * The distance between the best and worst magnitudes in the generation, discards included.
     *
     * <p>This is what answers whether ranking discriminated at all. ADR-0002 names "population
     * ships but ranking is not measurably useful" as a revisit trigger, and a generation whose
     * candidates all land at the same score is that trigger firing rather than a detail.
     */
    public Optional<BigDecimal> spread() {
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        var magnitudes = ranked.stream().map(result -> result.fitnessScore().rawMagnitude()).toList();
        var highest = magnitudes.stream().max(Comparator.naturalOrder()).orElseThrow();
        var lowest = magnitudes.stream().min(Comparator.naturalOrder()).orElseThrow();
        return Optional.of(highest.subtract(lowest));
    }

    /** How many candidates produced evidence. */
    public int evaluatedCount() {
        return ranked.size();
    }

    /** How many candidates the generation set out to evaluate. */
    public int requestedCount() {
        return ranked.size() + unevaluated.size();
    }
}
