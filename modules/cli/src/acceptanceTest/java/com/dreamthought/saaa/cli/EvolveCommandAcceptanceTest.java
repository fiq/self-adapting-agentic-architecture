package com.dreamthought.saaa.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class EvolveCommandAcceptanceTest {
    private static final String BASE_URL_PROPERTY = "saaa.model.base-url";
    private static final String API_KEY_PROPERTY = "saaa.model.api-key";
    private static final String MODEL_NAME_PROPERTY = "saaa.model.name";

    @Test
    void runsOneGenerationWithFixtureProfileAndReportsADecision(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", target.toString(),
                "--profile", "fixture",
                "--behaviour-case", "workflow-check",
                "--max-lines", "80");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("enforce the draft check")
                .contains("PROMOTE");
    }

    @Test
    void runsEveryDeclaredBehaviourCaseNotOnlyTheFirst(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        writeCheck(target, "second-check", """
                #!/usr/bin/env bash
                exit 0
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", target.toString(),
                "--behaviour-case", "workflow-check",
                "--behaviour-case", "second-check");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("workflow-check PASSED")
                .contains("second-check PASSED")
                .contains("PROMOTE");
    }

    /**
     * The gate must not pass on the strength of the first case alone. Wiring only
     * behaviourCases.get(0) into the check runner while declaring every name to the scorer would
     * leave this candidate promoted with a declared required behaviour never verified.
     */
    @Test
    void discardsWhenALaterDeclaredBehaviourCaseFails(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        writeCheck(target, "second-check", """
                #!/usr/bin/env bash
                echo "second behaviour case is not satisfied"
                exit 1
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", target.toString(),
                "--behaviour-case", "workflow-check",
                "--behaviour-case", "second-check");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("workflow-check PASSED")
                .contains("second-check FAILED")
                .contains("DISCARD");
    }

    /**
     * The relative check directory is empty when the target folder is the Git root. A command with
     * no path separator would be resolved against PATH instead of the candidate worktree, so this
     * run would score whatever PATH happened to provide, or fail every candidate when it provided
     * nothing.
     */
    @Test
    void runsTheCheckFromTheCandidateWhenTheTargetFolderIsTheGitRoot(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        writeFixture(repo);
        writeCheck(repo, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", repo.toString(),
                "--behaviour-case", "workflow-check");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(repo.resolve("journal.md")))
                .contains("workflow-check PASSED")
                .contains("PROMOTE");
    }

    /**
     * A committed symlink is recreated faithfully in the candidate worktree, so without containment
     * a script that is not in the candidate at all satisfies a required behaviour and the candidate
     * promotes on evidence about the wrong file.
     */
    @Test
    void refusesASymlinkedCheckScriptPointingOutsideTheRepository(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        Path outside = tempDir.resolve("outside.sh");
        Files.writeString(outside, """
                #!/usr/bin/env bash
                exit 0
                """);
        outside.toFile().setExecutable(true);
        Files.createSymbolicLink(target.resolve("workflow-check.sh"), outside);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", target.toString(),
                "--behaviour-case", "workflow-check");

        assertThat(exitCode).isNotZero();
        assertThat(Files.exists(target.resolve("journal.md"))).isFalse();
    }

    /**
     * Parsimony rewards a smaller diff, so a realization that wrote the file back unchanged measures
     * zero lines and scores 1.0. Nothing else in the score notices, so a candidate that changed
     * nothing promotes on evidence about the baseline it did not touch. Ranking several candidates
     * would then actively select for doing nothing.
     */
    @Test
    void discardsACandidateWhoseRealizationChangedNothing(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "leave the workflow exactly as it is\ndraft-check: skip\n");
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: skip$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", target.toString(),
                "--behaviour-case", "workflow-check");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("workflow-check PASSED")
                .contains("DISCARD");
    }

    @Test
    void failsFastWhenADeclaredBehaviourCaseHasNoScript(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                exit 0
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", target.toString(),
                "--behaviour-case", "workflow-check",
                "--behaviour-case", "absent-check");

        assertThat(exitCode).isNotZero();
        assertThat(Files.exists(target.resolve("journal.md"))).isFalse();
    }

    @Test
    void rejectsPatchWhoseDiffExceedsMaxLinesBeforeCandidateCreation(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), """
                alpha
                beta
                gamma
                """);
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"), """
                rewrite every line
                one
                two
                three
                """);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                exit 0
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", target.toString(),
                "--profile", "fixture",
                "--behaviour-case", "workflow-check",
                "--max-lines", "1");

        assertThat(exitCode).isNotZero();
        assertThat(Files.exists(target.resolve("journal.md"))).isFalse();
        assertThat(Files.exists(repo.resolve(".worktrees"))).isFalse();
    }

    @Test
    void recordsLiveProposerPromptDigestAndRawResponseInCandidateBookkeeping(@TempDir Path tempDir) throws Exception {
        var server = startOpenAiStub();
        try {
            stubOpenAiMutationResponse(server);
            configureOpenAiCompatibleProfile(server);
            Path repo = tempDir.resolve("repo");
            Path target = repo.resolve("toy");
            Files.createDirectories(target);
            Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
            writeCheck(target, "workflow-check", """
                    #!/usr/bin/env bash
                    set -euo pipefail
                    grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                    """);
            initRepo(repo);

            int exitCode = new CommandLine(new MutationLoopCli()).execute(
                    "evolve", target.toString(),
                    "--profile", "openai-compatible",
                    "--behaviour-case", "workflow-check",
                    "--max-lines", "8");

            assertThat(exitCode).isZero();
            Path candidateFile = repo.resolve(
                    ".worktrees/candidate-toy-mut-live-001/.saaa/candidates/candidate-mut-live-001.toon");
            assertThat(candidateFile).isRegularFile();
            assertThat(Files.readString(candidateFile))
                    .contains(
                            "proposer:",
                            "id: openai-compatible",
                            "prompt_digest:",
                            "raw_response: |",
                            "mut-live-001",
                            "draft-check: enforce"
                    );
            server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                    .withHeader("Authorization", equalTo("Bearer local-test-key"))
                    .withRequestBody(matchingJsonPath("$.model", equalTo("local-test-model"))));
        } finally {
            clearOpenAiCompatibleProfileConfig();
            server.stop();
        }
    }

    private static void writeFixture(Path target) throws Exception {
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
    }

    private static WireMockServer startOpenAiStub() {
        var server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    private static void stubOpenAiMutationResponse(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer local-test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("local-test-model")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", containing("bounded workflow mutations")))
                .withRequestBody(matchingJsonPath("$.messages[1].content", containing("draft-check: skip")))
                .willReturn(okJson("""
                        {
                          "id": "chatcmpl-local",
                          "object": "chat.completion",
                          "created": 1,
                          "model": "local-test-model",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "{\\"id\\":\\"mut-live-001\\",\\"summary\\":\\"enforce the draft check\\",\\"scope\\":\\"WORKFLOW_DEFINITION\\",\\"patch\\":\\"draft-check: enforce\\\\n\\"}"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 1,
                            "completion_tokens": 1,
                            "total_tokens": 2
                          }
                        }
                        """)));
    }

    private static void configureOpenAiCompatibleProfile(WireMockServer server) {
        System.setProperty(BASE_URL_PROPERTY, server.baseUrl() + "/v1");
        System.setProperty(API_KEY_PROPERTY, "local-test-key");
        System.setProperty(MODEL_NAME_PROPERTY, "local-test-model");
    }

    private static void clearOpenAiCompatibleProfileConfig() {
        System.clearProperty(BASE_URL_PROPERTY);
        System.clearProperty(API_KEY_PROPERTY);
        System.clearProperty(MODEL_NAME_PROPERTY);
    }

    private static void writeCheck(Path target, String caseName, String script) throws Exception {
        Path check = target.resolve(caseName + ".sh");
        Files.writeString(check, script);
        check.toFile().setExecutable(true);
    }

    private static void initRepo(Path repo) throws Exception {
        git(repo, "init", "--initial-branch=main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "baseline");
    }

    private static void git(Path dir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        Files.createDirectories(dir);
        var process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", args) + "\n" + output);
        }
    }
}
