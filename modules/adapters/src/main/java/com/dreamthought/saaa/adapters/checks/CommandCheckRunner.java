package com.dreamthought.saaa.adapters.checks;

import com.dreamthought.saaa.deterministic.CheckRunner;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class CommandCheckRunner implements CheckRunner {
    private static final int MAX_SUMMARY_OUTPUT_LENGTH = 4_000;

    private final List<CommandCheck> checks;
    private final Supplier<Map<String, String>> environmentSource;

    public CommandCheckRunner(List<CommandCheck> checks) {
        this(checks, System::getenv);
    }

    public CommandCheckRunner(List<CommandCheck> checks, Supplier<Map<String, String>> environmentSource) {
        this.checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        if (this.checks.isEmpty()) {
            throw new IllegalArgumentException("checks must not be empty");
        }
        this.environmentSource = Objects.requireNonNull(environmentSource, "environmentSource");
    }

    @Override
    public List<CheckEvidence> runChecks(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!Files.isDirectory(candidate.worktreePath())) {
            throw new IllegalArgumentException("candidate worktreePath must be an existing directory");
        }
        checks.forEach(check -> requireContainedProgram(candidate.worktreePath(), check));
        return checks.stream()
                .map(check -> runCheck(candidate, check))
                .toList();
    }

    /**
     * A command whose program name is a path must resolve inside the candidate. Symlinks are
     * followed before the comparison, because a tracked symlink out of the tree is recreated
     * faithfully by {@code git worktree add} and would otherwise let a script that is not in the
     * candidate produce evidence about a required behaviour.
     *
     * <p>A program name with no path separator is left alone: it is resolved by the operating system
     * against {@code PATH}, which is how an interpreter such as {@code sh} is named, and is the
     * caller's explicit choice rather than something the candidate can influence.
     *
     * <p>This throws rather than recording a failed check. An escaping command is a broken setup,
     * not an observation about the mutation, and recording it as evidence would read as a candidate
     * that regressed the behaviour.
     */
    private static void requireContainedProgram(Path worktreePath, CommandCheck check) {
        String program = check.command().get(0);
        if (!program.contains("/") && !program.contains(File.separator)) {
            return;
        }
        Path resolved = worktreePath.resolve(program);
        try {
            Path realProgram = resolved.toRealPath();
            Path realWorktree = worktreePath.toRealPath();
            if (!realProgram.startsWith(realWorktree)) {
                throw new IllegalStateException("check " + check.name() + " resolves to a program outside the "
                        + "candidate worktree: " + program + " -> " + realProgram);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "check " + check.name() + " names a program that cannot be resolved inside the "
                            + "candidate worktree: " + program, exception);
        }
    }

    private CheckEvidence runCheck(Candidate candidate, CommandCheck check) {
        ProcessBuilder processBuilder = new ProcessBuilder(check.command())
                .directory(candidate.worktreePath().toFile())
                .redirectErrorStream(true);
        applyScrubbedEnvironment(processBuilder, check.environmentAllowList());
        try {
            Process process = processBuilder.start();
            CompletableFuture<String> output = captureOutput(process);
            boolean completed = process.waitFor(check.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminate(process);
                return CheckEvidence.timedOut(check.name(), timeoutSummary(check.timeout(), outputOf(output)));
            }
            int exitCode = process.exitValue();
            String summary = summary(exitCode, outputOf(output));
            if (exitCode == 0) {
                return CheckEvidence.passed(check.name(), summary);
            }
            return CheckEvidence.failed(check.name(), summary);
        } catch (IOException exception) {
            return CheckEvidence.failed(check.name(), "failed to start: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CheckEvidence.failed(check.name(), "interrupted while waiting for command");
        }
    }

    private void applyScrubbedEnvironment(ProcessBuilder processBuilder, List<String> allowList) {
        Map<String, String> target = processBuilder.environment();
        target.clear();
        target.putAll(allowedEnvironment(environmentSource.get(), allowList));
    }

    private static Map<String, String> allowedEnvironment(Map<String, String> source, List<String> allowList) {
        Map<String, String> allowed = new HashMap<>();
        for (String entry : allowList) {
            if (entry.endsWith("*")) {
                String prefix = entry.substring(0, entry.length() - 1);
                source.forEach((key, value) -> {
                    if (key.startsWith(prefix) && !isDeniedCredentialName(key)) {
                        allowed.put(key, value);
                    }
                });
            } else if (source.containsKey(entry) && !isDeniedCredentialName(entry)) {
                allowed.put(entry, source.get(entry));
            }
        }
        return allowed;
    }

    private static boolean isDeniedCredentialName(String key) {
        return key.startsWith("SAAA_MODEL_")
                || key.equals("OPENAI_API_KEY")
                || key.equals("ANTHROPIC_API_KEY")
                || key.equals("CLAUDE_API_KEY");
    }

    private static void terminate(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor();
    }

    private static CompletableFuture<String> captureOutput(Process process) {
        CompletableFuture<String> output = new CompletableFuture<>();
        Thread.ofVirtual().name("command-check-output-capture").start(() -> {
            try {
                output.complete(readOutput(process.getInputStream()));
            } catch (IOException exception) {
                output.complete("");
            }
        });
        return output;
    }

    private static String readOutput(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8_192];
        ByteArrayOutputStream captured = new ByteArrayOutputStream(MAX_SUMMARY_OUTPUT_LENGTH);
        int capturedLength = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            int remaining = MAX_SUMMARY_OUTPUT_LENGTH + 1 - capturedLength;
            if (remaining > 0) {
                int bytesToCapture = Math.min(read, remaining);
                captured.write(buffer, 0, bytesToCapture);
                capturedLength += bytesToCapture;
            }
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static String outputOf(CompletableFuture<String> output) {
        try {
            return output.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException exception) {
            return "";
        }
    }

    private static String summary(int exitCode, String output) {
        String trimmed = output.strip();
        if (trimmed.length() > MAX_SUMMARY_OUTPUT_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SUMMARY_OUTPUT_LENGTH) + "...";
        }
        if (trimmed.isEmpty()) {
            return "exit=" + exitCode;
        }
        return "exit=" + exitCode + " output=" + trimmed;
    }

    private static String timeoutSummary(Duration timeout, String output) {
        String trimmed = output.strip();
        String summary = "timed out after " + timeout;
        if (!trimmed.isEmpty()) {
            summary = summary + " output=" + trimmed;
        }
        return summary;
    }

    public record CommandCheck(String name, List<String> command, Duration timeout, List<String> environmentAllowList) {
        public CommandCheck(String name, List<String> command) {
            this(name, command, Duration.ofMinutes(5));
        }

        public CommandCheck(String name, List<String> command, Duration timeout) {
            this(name, command, timeout, defaultEnvironmentAllowList());
        }

        public CommandCheck {
            name = requireNonBlank(name, "name");
            command = List.copyOf(Objects.requireNonNull(command, "command"));
            if (command.isEmpty()) {
                throw new IllegalArgumentException("command must not be empty");
            }
            command.forEach(part -> requireNonBlank(part, "command part"));
            timeout = Objects.requireNonNull(timeout, "timeout");
            if (timeout.toMillis() <= 0) {
                throw new IllegalArgumentException("timeout must be at least one millisecond");
            }
            environmentAllowList = List.copyOf(Objects.requireNonNull(environmentAllowList, "environmentAllowList"));
            environmentAllowList.forEach(entry -> requireNonBlank(entry, "environment allow-list entry"));
        }
    }

    private static List<String> defaultEnvironmentAllowList() {
        return List.of("PATH", "HOME", "LANG", "LC_*", "JAVA_HOME");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
