package io.github.selfadaptingagenticarchitecture.adapters.checks;

import io.github.selfadaptingagenticarchitecture.application.CheckRunner;
import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.CheckEvidence;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CommandCheckRunner implements CheckRunner {
    private static final int MAX_SUMMARY_OUTPUT_LENGTH = 4_000;

    private final List<CommandCheck> checks;

    public CommandCheckRunner(List<CommandCheck> checks) {
        this.checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        if (this.checks.isEmpty()) {
            throw new IllegalArgumentException("checks must not be empty");
        }
    }

    @Override
    public List<CheckEvidence> runChecks(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!Files.isDirectory(candidate.worktreePath())) {
            throw new IllegalArgumentException("candidate worktreePath must be an existing directory");
        }
        return checks.stream()
                .map(check -> runCheck(candidate, check))
                .toList();
    }

    private static CheckEvidence runCheck(Candidate candidate, CommandCheck check) {
        ProcessBuilder processBuilder = new ProcessBuilder(check.command())
                .directory(candidate.worktreePath().toFile())
                .redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            CompletableFuture<String> output = captureOutput(process);
            boolean completed = process.waitFor(check.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminate(process);
                return CheckEvidence.failed(check.name(), timeoutSummary(check.timeout(), outputOf(output)));
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

    public record CommandCheck(String name, List<String> command, Duration timeout) {
        public CommandCheck(String name, List<String> command) {
            this(name, command, Duration.ofMinutes(5));
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
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
