package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import java.util.List;

@FunctionalInterface
public interface CheckRunner {
    List<CheckEvidence> runChecks(Candidate candidate);
}
