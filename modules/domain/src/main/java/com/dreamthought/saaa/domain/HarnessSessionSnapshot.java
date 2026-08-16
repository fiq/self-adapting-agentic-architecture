package com.dreamthought.saaa.domain;

import java.util.Objects;
import java.util.Optional;

/** Immutable observable state of a local interactive harness session. */
public record HarnessSessionSnapshot(
        HarnessSessionStatus status,
        Optional<HarnessSessionTarget> target,
        String route
) {
    public HarnessSessionSnapshot {
        status = Objects.requireNonNull(status, "status");
        target = Objects.requireNonNull(target, "target");
        route = Require.nonBlank(route, "route");
    }
}
