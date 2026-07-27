package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.Candidate;
import com.dreamthought.saaa.core.CheckEvidence;
import java.util.List;

@FunctionalInterface
public interface CheckRunner {
    List<CheckEvidence> runChecks(Candidate candidate);
}
