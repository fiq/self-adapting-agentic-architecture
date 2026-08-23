package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.adapters.checks.CommandCheckRunner.CommandCheck;
import com.dreamthought.saaa.deterministic.ScoringConfig;
import java.nio.file.Path;
import java.util.Collection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Maps each declared behaviour case to the script that verifies it.
 *
 * <p>One case, one script named after it. The scorer hard-gates promotion on every declared case,
 * so every declared case must produce its own check evidence; mapping only some of them would let a
 * candidate be promoted with a required behaviour never verified.
 */
public final class BehaviourCaseChecks {
    /** The case name becomes a path segment, so it is restricted to a single safe file-name segment. */
    private static final Pattern SAFE_CASE_NAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]*");

    private static final Duration CHECK_TIMEOUT = Duration.ofMinutes(1);

    private BehaviourCaseChecks() {
    }

    /**
     * @param checkDirectory the target folder relative to the Git root, because
     *     {@code CommandCheckRunner} executes each command with the candidate worktree as its
     *     working directory; an absolute path would resolve against the coordination checkout and
     *     silently score the wrong content
     */
    public static List<CommandCheck> forCases(List<String> caseNames, Path checkDirectory) {
        Objects.requireNonNull(caseNames, "caseNames");
        Objects.requireNonNull(checkDirectory, "checkDirectory");
        if (caseNames.isEmpty()) {
            throw new IllegalArgumentException("at least one behaviour case is required");
        }
        if (checkDirectory.isAbsolute() || checkDirectory.normalize().startsWith("..")) {
            throw new IllegalArgumentException(
                    "checkDirectory must be a relative path that descends from the Git root, so the "
                            + "command resolves inside the candidate worktree rather than escaping it: "
                            + checkDirectory);
        }

        var seen = new HashSet<String>();
        var checks = new ArrayList<CommandCheck>(caseNames.size());
        for (String name : caseNames) {
            // A declared case ending .run<digits> would collapse onto a different base name when the
            // scorer groups repeated runs, so the two would be scored as one case. Reserving the
            // suffix is cheaper than making the grouping ambiguous.
            if (!ScoringConfig.baseCaseName(name).equals(name)) {
                throw new IllegalArgumentException(
                        "behaviour case name may not end with the repeated-run suffix "
                                + ScoringConfig.REPEAT_RUN_SEPARATOR + "<number>: " + name);
            }
            if (!SAFE_CASE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                        "behaviour case name must be a single safe file-name segment "
                                + "matching " + SAFE_CASE_NAME.pattern() + ": " + name);
            }
            // Case-insensitively, because on a case-insensitive filesystem "Foo" and "foo" would
            // both exec foo.sh and the gate would count one script as two cases.
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate behaviour case name: " + name);
            }
            checks.add(new CommandCheck(name, List.of(scriptPath(checkDirectory, name)), CHECK_TIMEOUT));
        }
        return List.copyOf(checks);
    }

    /**
     * Adds repeated runs of each behaviour case, named {@code <case>.run2}, {@code <case>.run3} and
     * so on, all executing the same script.
     *
     * <p>The names differ so each result is separately attributable in the evidence; the commands are
     * identical because the point is to run the same check again, not a different one. The scorer
     * withholds the repeats from the deterministic-checks gate, so the canonical run decides whether
     * the candidate is eligible and the repeats grade how reliably that result holds.
     *
     * <p>Safety probes are deliberately not repeated: they already grade rather than gate, and
     * repeating them would change what their pass fraction means.
     */
    public static List<CommandCheck> withRepeatedRuns(
            List<CommandCheck> checks, Collection<String> repeatableNames, int runs) {
        Objects.requireNonNull(checks, "checks");
        Objects.requireNonNull(repeatableNames, "repeatableNames");
        if (runs < 1) {
            throw new IllegalArgumentException("runs must be at least 1");
        }
        if (runs == 1) {
            return checks;
        }
        var withRepeats = new ArrayList<>(checks);
        for (CommandCheck check : checks) {
            if (!repeatableNames.contains(check.name())) {
                continue;
            }
            for (int run = 2; run <= runs; run++) {
                withRepeats.add(new CommandCheck(
                        check.name() + ScoringConfig.REPEAT_RUN_SEPARATOR + run,
                        check.command(), check.timeout(), check.environmentAllowList()));
            }
        }
        return List.copyOf(withRepeats);
    }

    /**
     * Always carries a {@code ./} prefix, which keeps the program out of {@code PATH} resolution.
     * {@code CommandCheckRunner} still enforces containment at the point of execution.
     */
    private static String scriptPath(Path checkDirectory, String caseName) {
        return Path.of(".").resolve(checkDirectory).resolve(caseName + ".sh").toString();
    }
}
