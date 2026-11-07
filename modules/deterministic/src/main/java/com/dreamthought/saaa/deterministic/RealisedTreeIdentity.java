package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;

/** Identity of the complete committed files of a realised candidate. Lookup failures abort the run. */
@FunctionalInterface
public interface RealisedTreeIdentity {
    String treeOf(Candidate candidate);
}
