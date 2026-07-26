package io.github.selfadaptingagenticarchitecture.adapters.checks;

import io.github.selfadaptingagenticarchitecture.application.CheckRunner;
import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.CheckEvidence;
import java.util.List;

public final class CommandCheckRunner implements CheckRunner {
    @Override
    public List<CheckEvidence> runChecks(Candidate candidate) {
        throw new UnsupportedOperationException("Deterministic check command runner is pending implementation");
    }
}
