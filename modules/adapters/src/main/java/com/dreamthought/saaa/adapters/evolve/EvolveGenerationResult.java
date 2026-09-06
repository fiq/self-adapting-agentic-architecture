package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.domain.RankedGeneration;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What one generation of {@code saaa evolve} produced: the ranking, and where it was journalled.
 *
 * <p>Deliberately not {@link EvolveRunResult} with extra fields. A single-candidate run always has a
 * result to report, and every caller of {@code fitnessResult()} assumes it is there. A generation
 * may have none, because every candidate can fail to produce evidence and that is recorded rather
 * than thrown, so folding the two together would make an always-present value absent on one path.
 */
public record EvolveGenerationResult(
        RankedGeneration generation,
        Path journalPath,
        long wallClockMillis
) {
    public EvolveGenerationResult {
        generation = Objects.requireNonNull(generation, "generation");
        journalPath = Objects.requireNonNull(journalPath, "journalPath").toAbsolutePath().normalize();
        if (wallClockMillis < 0) {
            throw new IllegalArgumentException("run duration is invalid");
        }
    }
}
