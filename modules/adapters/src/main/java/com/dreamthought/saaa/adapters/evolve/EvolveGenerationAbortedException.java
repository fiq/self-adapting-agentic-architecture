package com.dreamthought.saaa.adapters.evolve;

import java.nio.file.Path;
import java.util.Objects;

/** Carries the journal path for a generation whose abort has already been recorded. */
public final class EvolveGenerationAbortedException extends IllegalStateException {
    private final Path journalPath;

    public EvolveGenerationAbortedException(Path journalPath, RuntimeException cause) {
        super(Objects.requireNonNull(cause, "cause").getMessage(), cause);
        this.journalPath = Objects.requireNonNull(journalPath, "journalPath").toAbsolutePath().normalize();
    }

    public Path journalPath() {
        return journalPath;
    }
}
