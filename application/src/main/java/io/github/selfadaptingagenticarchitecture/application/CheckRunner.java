package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.CheckEvidence;
import java.util.List;

@FunctionalInterface
public interface CheckRunner {
    List<CheckEvidence> runChecks(Candidate candidate);
}
