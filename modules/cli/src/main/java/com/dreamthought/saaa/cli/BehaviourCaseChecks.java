package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.checks.CommandCheckRunner.CommandCheck;
import java.nio.file.Path;
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
final class BehaviourCaseChecks {
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
    static List<CommandCheck> forCases(List<String> caseNames, Path checkDirectory) {
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
     * Always carries a {@code ./} prefix, which keeps the program out of {@code PATH} resolution: a
     * program name with no path separator is resolved against {@code PATH} rather than the child
     * process working directory, and an empty check directory — what the target folder being the Git
     * root produces — would otherwise yield a bare {@code <name>.sh}.
     *
     * <p>This makes the command worktree-relative; it does not by itself prove the program lies
     * inside the worktree, because a path can still traverse or follow a symlink out of it.
     * {@code CommandCheckRunner} enforces containment at the point of execution.
     */
    private static String scriptPath(Path checkDirectory, String caseName) {
        return Path.of(".").resolve(checkDirectory).resolve(caseName + ".sh").toString();
    }
}
