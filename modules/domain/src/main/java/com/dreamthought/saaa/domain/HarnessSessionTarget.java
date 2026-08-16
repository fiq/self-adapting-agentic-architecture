package com.dreamthought.saaa.domain;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit operator-selected target retained by an interactive harness session. */
public record HarnessSessionTarget(EvolutionTargetKind kind, Path folder) {
    public HarnessSessionTarget {
        kind = Objects.requireNonNull(kind, "kind");
        folder = Objects.requireNonNull(folder, "folder").toAbsolutePath().normalize();
    }
}
