package com.dreamthought.saaa.adapters.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a {@code git} subprocess in a given directory and captures its combined output. Shared by
 * every adapter in this package so subprocess execution semantics (error stream merging, output
 * decoding, interrupt handling) live in exactly one place.
 */
final class GitCommand {
    private GitCommand() {
    }

    static Result run(Path directory, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Result(process.waitFor(), output);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to run git", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running git", exception);
        }
    }

    record Result(int exitCode, String output) {
        String requireSuccess(String operation) {
            if (exitCode != 0) {
                throw new IllegalStateException(operation + " failed: " + output);
            }
            return output;
        }
    }
}
