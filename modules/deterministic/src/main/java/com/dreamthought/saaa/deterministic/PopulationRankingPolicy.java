package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.RankedGeneration;
import com.dreamthought.saaa.domain.UnevaluatedCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orders a generation of evaluated candidates.
 *
 * <p>Deterministic by construction: the order comes from {@link RankedGeneration#bestFirst()}, which
 * is total, so the same candidates rank the same way whatever order they were evaluated in. That
 * matters more than it sounds — a ranking that depends on evaluation order cannot be reproduced from
 * the record, and a reader could only trust it rather than check it.
 *
 * <p>Selection happens here; promotion does not. Each candidate's PROMOTE or DISCARD was already
 * decided by its own gates, and this only says which of them is best.
 */
public final class PopulationRankingPolicy {
    /**
     * Ranks what produced evidence and carries forward what did not.
     *
     * @throws IllegalArgumentException when the candidates were not measured against the same
     *                                  scoring context, because then there is no axis to rank on
     */
    public RankedGeneration rank(List<FitnessResult> results, List<UnevaluatedCandidate> unevaluated) {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(unevaluated, "unevaluated");
        var ordered = new ArrayList<>(results);
        ordered.sort(RankedGeneration.bestFirst());
        return new RankedGeneration(ordered, unevaluated);
    }
}
