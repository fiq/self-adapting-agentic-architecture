package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.RealizationSummary;

/**
 * Reports how large a candidate's realized change is. Scoring needs this for parsimony, and asking
 * through a port keeps Git out of the deterministic layer.
 */
@FunctionalInterface
public interface RealizationInspector {
    RealizationSummary inspect(Candidate candidate);
}
