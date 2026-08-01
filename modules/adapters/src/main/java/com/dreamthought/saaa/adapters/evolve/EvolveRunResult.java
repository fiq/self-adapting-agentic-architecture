package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.domain.FitnessResult;
import java.nio.file.Path;
import java.util.Objects;

public record EvolveRunResult(FitnessResult fitnessResult, Path journalPath) {
    public EvolveRunResult {
        fitnessResult = Objects.requireNonNull(fitnessResult, "fitnessResult");
        journalPath = Objects.requireNonNull(journalPath, "journalPath").toAbsolutePath().normalize();
    }
}
