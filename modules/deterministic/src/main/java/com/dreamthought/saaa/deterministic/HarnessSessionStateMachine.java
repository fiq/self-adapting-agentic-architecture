package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.HarnessSessionSnapshot;
import com.dreamthought.saaa.domain.HarnessSessionStatus;
import com.dreamthought.saaa.domain.HarnessSessionTarget;
import java.util.Optional;

/** Deterministic lifecycle and selection state for one interactive harness session. */
public final class HarnessSessionStateMachine {
    private HarnessSessionSnapshot snapshot;

    public HarnessSessionStateMachine(String initialRoute) {
        snapshot = new HarnessSessionSnapshot(HarnessSessionStatus.ACTIVE, Optional.empty(),
                requireNonBlank(initialRoute, "initialRoute"));
    }

    public HarnessSessionSnapshot snapshot() {
        return snapshot;
    }

    public HarnessSessionSnapshot selectTarget(HarnessSessionTarget target) {
        requireActive();
        snapshot = new HarnessSessionSnapshot(snapshot.status(), Optional.of(target), snapshot.route());
        return snapshot;
    }

    public HarnessSessionSnapshot selectRoute(String route) {
        requireActive();
        snapshot = new HarnessSessionSnapshot(snapshot.status(), snapshot.target(),
                requireNonBlank(route, "route"));
        return snapshot;
    }

    public HarnessSessionSnapshot close() {
        requireActive();
        snapshot = new HarnessSessionSnapshot(HarnessSessionStatus.CLOSED, snapshot.target(), snapshot.route());
        return snapshot;
    }

    public void requireActive() {
        if (snapshot.status() != HarnessSessionStatus.ACTIVE) {
            throw new IllegalStateException("session is closed");
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
