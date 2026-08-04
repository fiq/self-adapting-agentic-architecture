package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.domain.FitnessResult;
import java.nio.file.Path;
import java.util.Objects;
import com.dreamthought.saaa.domain.RetrievalBundle;
import com.dreamthought.saaa.domain.RetrievalDiagnostics;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.util.List;
import java.util.Optional;
import com.dreamthought.saaa.domain.ProposerEvidence;

public record EvolveRunResult(
        FitnessResult fitnessResult,
        Path journalPath,
        RetrievalBundle retrieval,
        Optional<ProposerEvidence> proposerEvidence,
        long wallClockMillis,
        long retrievalMillis
) {
    public EvolveRunResult(FitnessResult fitnessResult, Path journalPath) {
        this(
                fitnessResult,
                journalPath,
                new RetrievalBundle(
                        RetrievalMode.NONE,
                        "retrieval-config-v1",
                        fitnessResult.candidate().commitSha(),
                        "none",
                        "none",
                        "none",
                        "none",
                        "lineage-novelty-v1",
                        List.of(),
                RetrievalDiagnostics.empty(),
                        ""), Optional.empty(), 0, 0);
    }

    public EvolveRunResult(FitnessResult fitnessResult, Path journalPath, RetrievalBundle retrieval) {
        this(fitnessResult, journalPath, retrieval, Optional.empty(), 0, 0);
    }

    public EvolveRunResult {
        fitnessResult = Objects.requireNonNull(fitnessResult, "fitnessResult");
        journalPath = Objects.requireNonNull(journalPath, "journalPath").toAbsolutePath().normalize();
        retrieval = Objects.requireNonNull(retrieval, "retrieval");
        proposerEvidence = Objects.requireNonNull(proposerEvidence, "proposerEvidence");
        if (wallClockMillis < 0 || retrievalMillis < 0 || retrievalMillis > wallClockMillis) {
            throw new IllegalArgumentException("run durations are invalid");
        }
    }
}
