package com.dreamthought.saaa.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies one fitness signal by whose property it is, whether it must hold, and what it is
 * called.
 *
 * <p>Role comes from {@link #force()}, never from the name. The previous convention encoded role in
 * a {@code hard_gate_} name prefix, which meant a signal could claim a role by being named for one
 * and every consumer had to sniff the string to find out.
 */
public record FitnessSignalId(FitnessScope scope, FitnessForce force, String name) {
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");
    private static final int SEGMENTS = 3;

    public FitnessSignalId {
        scope = Objects.requireNonNull(scope, "scope");
        force = Objects.requireNonNull(force, "force");
        Objects.requireNonNull(name, "name");
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "name must be lower snake_case with no separators of its own: " + name);
        }
    }

    public static FitnessSignalId invariant(String name) {
        return new FitnessSignalId(FitnessScope.SUBJECT, FitnessForce.INVARIANT, name);
    }

    public static FitnessSignalId objective(String name) {
        return new FitnessSignalId(FitnessScope.SUBJECT, FitnessForce.OBJECTIVE, name);
    }

    public static FitnessSignalId processInvariant(String name) {
        return new FitnessSignalId(FitnessScope.PROCESS, FitnessForce.INVARIANT, name);
    }

    /** The flat form used for map keys, persisted rows and JSON, where a nested shape is unavailable. */
    public String canonical() {
        return segment(scope.name()) + "." + segment(force.name()) + "." + name;
    }

    public static FitnessSignalId parse(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        if (canonical.startsWith("hard_gate_")) {
            return invariant(canonical.substring("hard_gate_".length()));
        }
        if (SAFE_NAME.matcher(canonical).matches()) {
            // Legacy measured objective keys were unscoped names. Accept them on read so existing
            // ledgers and result maps can be re-emitted in the typed canonical form.
            return objective(canonical);
        }
        String[] parts = canonical.split("\\.", -1);
        if (parts.length != SEGMENTS) {
            throw new IllegalArgumentException(
                    "fitness signal id needs exactly " + SEGMENTS + " dot-separated segments: " + canonical);
        }
        return new FitnessSignalId(scopeOf(parts[0], canonical), forceOf(parts[1], canonical), parts[2]);
    }

    private static FitnessScope scopeOf(String value, String canonical) {
        for (FitnessScope candidate : FitnessScope.values()) {
            if (segment(candidate.name()).equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown fitness scope in " + canonical + ": " + value);
    }

    private static FitnessForce forceOf(String value, String canonical) {
        for (FitnessForce candidate : FitnessForce.values()) {
            if (segment(candidate.name()).equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown fitness force in " + canonical + ": " + value);
    }

    private static String segment(String enumName) {
        return enumName.toLowerCase(Locale.ROOT);
    }
}
