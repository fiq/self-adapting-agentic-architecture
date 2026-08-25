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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                "saaa-evolve", target.toString(),
                "--profile", "fixture",
                "--behaviour-case", "workflow-check",
                "--max-lines", "80");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("enforce the draft check")
                .contains("PROMOTE");
    }

    /**
     * C3: {@code cost_latency_budget} was always {@code 1.0} because {@code :cli} had no dependency
     * on {@code :benchmarks} and {@code EvolveRunner} wired a constant empty benchmark list. With a
     * real {@code JmhBenchmarkRunner} wired through {@code --benchmark} and an unreachable
     * {@code --benchmark-budget}, the measured benchmark exceeds its budget by many orders of
     * magnitude, so the weighted score drops from a comfortable PROMOTE to a DISCARD below the 0.80
     * threshold even though every declared behaviour case still passes. The budget is set
     * astronomically small rather than pinned to an exact JMH timing so the assertion does not
     * depend on host-specific throughput.
     */
    @Test
    void wiresARealBenchmarkRunnerSoAnOverBudgetMeasurementDiscardsAnOtherwisePromotingCandidate(
            @TempDir Path tempDir) throws Exception {
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
                "saaa-evolve", target.toString(),
                "--profile", "fixture",
                "--behaviour-case", "workflow-check",
                "--max-lines", "80",
                "--benchmark", "workflow-graph-create="
                        + "com.dreamthought.saaa.benchmarks.WorkflowGraphBenchmark.createWorkflowGraph",
                "--benchmark-budget", "workflow-graph-create=0.0000001");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("enforce the draft check")
                .contains("DISCARD")
                .doesNotContain("PROMOTE");
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
                "saaa-evolve", target.toString(),
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
                "saaa-evolve", target.toString(),
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
                "saaa-evolve", repo.toString(),
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
                "saaa-evolve", target.toString(),
                "--behaviour-case", "workflow-check");

        assertThat(exitCode).isNotZero();
        assertThat(Files.exists(target.resolve("journal.md"))).isFalse();
    }

    @Test
    void refusesToMutateTheBehaviourCaseCheckScript(@TempDir Path tempDir) throws Exception {
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
                "saaa-evolve", target.toString(),
                "--workflow-file", "workflow-check.sh",
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
                "saaa-evolve", target.toString(),
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
                "saaa-evolve", target.toString(),
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
                "saaa-evolve", target.toString(),
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
                    "saaa-evolve", target.toString(),
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

    @Test
    void candidateThatAttemptsToReadTheApiKeyObservesTheEmptyString(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        writeCheck(target, "credential-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                test -z "${SAAA_MODEL_API_KEY:-}"
                """);
        initRepo(repo);

        int exitCode = runCliWithEnvironment(
                Map.of("SAAA_MODEL_API_KEY", "sk-parent-process-secret"),
                "saaa-evolve", target.toString(),
                "--behaviour-case", "credential-check");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("credential-check PASSED")
                .contains("PROMOTE");
    }

    @Test
    void hasNoFlagThatTurnsAnyScoreIntoAMerge() {
        var evolve = new CommandLine(new MutationLoopCli()).getCommandSpec().subcommands().get("saaa-evolve");

        assertThat(evolve.getCommandSpec().options())
                .extracting(option -> String.join(",", option.names()))
                .noneMatch(names -> names.contains("merge"))
                .noneMatch(names -> names.contains("main"))
                .noneMatch(names -> names.contains("promote"));
    }

    @Test
    void promotedCandidateLandsAsABranchPointerAndNotAMerge(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);
        String mainBefore = gitOutput(repo, "rev-parse", "main").strip();

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "saaa-evolve", target.toString(),
                "--behaviour-case", "workflow-check");

        assertThat(exitCode).isZero();
        assertThat(gitOutput(repo, "show-ref", "--verify", "refs/heads/candidate/toy-mut-toy-fixture"))
                .contains("refs/heads/candidate/toy-mut-toy-fixture");
        assertThat(gitOutput(repo, "rev-parse", "main").strip()).isEqualTo(mainBefore);
    }

    @Test
    void evolvingAFileInsideThisRepositoryLeavesNoUntrackedJournalMd(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(repo.resolve(".gitignore"), Files.readString(repositoryRoot().resolve(".gitignore")));
        Files.writeString(target.resolve("AuthorityLanguage.java"), """
                package com.dreamthought.saaa.deterministic;

                final class AuthorityLanguage {
                    static boolean allowed() {
                        return false;
                    }
                }
                """);
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"), """
                make the authority helper useful
                package com.dreamthought.saaa.deterministic;

                final class AuthorityLanguage {
                    static boolean allowed() {
                        return true;
                    }
                }
                """);
        writeCheck(target, "authority-language-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q 'return true;' "$(dirname "$0")/AuthorityLanguage.java"
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "saaa-evolve", target.toString(),
                "--workflow-file", "AuthorityLanguage.java",
                "--behaviour-case", "authority-language-check",
                "--max-lines", "20");

        assertThat(exitCode).isZero();
        assertThat(gitOutput(repo, "status", "--short")).doesNotContain("journal.md");
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

    /**
     * CHG-024 H0. A held-out case must actually execute. If it does not, the scorer still records it
     * as failed through {@code PhenotypeBridgeScorer}'s "no check evidence was produced" fallback,
     * and {@code task_success} drops from absence rather than from measurement — a green test over a
     * script that never ran, which is the disconnected assertion AGENTS.md warns about.
     *
     * <p>This asserts against the journal's {@code checks} row, which is rendered from
     * {@code EvaluationEvidence.checks()} — the list of checks that genuinely executed. The scorer's
     * synthetic fallback never reaches it, so a held-out name appearing there cannot be produced by
     * absence.
     */
    @Test
    void executesADeclaredHeldOutCaseRatherThanScoringItFromAbsence(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        writeCheck(target, "held-out-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "saaa-evolve", target.toString(),
                "--profile", "fixture",
                "--behaviour-case", "workflow-check",
                "--held-out-case", "held-out-check",
                "--max-lines", "80");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .as("the held-out script must appear in the executed-check evidence, not be inferred")
                .contains("held-out-check PASSED");
    }

    /**
     * CHG-024 H1, the load-bearing assertion. A failing held-out case must lower {@code task_success}
     * without discarding the candidate. Before this change every promoted candidate scored exactly
     * 1.0 on {@code task_success}, pinning 0.40 of the weight and leaving two promoted candidates
     * separable only by parsimony.
     *
     * <p>Asserted comparatively: two runs identical but for whether the held-out case passes. Both
     * must promote, and the failing one must score strictly lower. A single-run assertion on an
     * absolute score would pin host-dependent arithmetic; the comparison pins the behaviour.
     */
    @Test
    void aFailingHeldOutCaseLowersTheScoreWithoutDiscardingTheCandidate(@TempDir Path tempDir) throws Exception {
        String passingJournal = runWithHeldOutCase(tempDir.resolve("passing"), 3, true);
        String failingJournal = runWithHeldOutCase(tempDir.resolve("failing"), 3, false);

        assertThat(failingJournal)
                .as("a held-out case decides no gate, so the candidate must still promote")
                .contains("held-out-check FAILED")
                .contains("| decision | PROMOTE |");
        assertThat(scoreOf(failingJournal))
                .as("a failing held-out case must lower task_success and therefore the raw magnitude")
                .isLessThan(scoreOf(passingJournal));
    }

    /**
     * CHG-024, the resolution limit made explicit. {@code task_success} carries 0.40, so one failing
     * case out of {@code n} costs {@code 0.40/n} of the raw magnitude. A promoted candidate normally
     * sits around 0.90, leaving roughly 0.10 of headroom above the 0.80 threshold, so with a single
     * gating case beside a single held-out case the 0.20 loss falls straight through the floor.
     *
     * <p>That is correct behaviour rather than a defect — a candidate failing half its behaviour
     * cases has not earned a promotion — but it means "held out" does not mean "free". The ratio of
     * held-out to gating cases decides both the ranking resolution and whether a held-out failure is
     * survivable at all, so it is pinned here rather than left for someone to rediscover.
     */
    @Test
    void aThinGatingRatioLetsOneHeldOutFailureFallThroughThePromotionFloor(@TempDir Path tempDir)
            throws Exception {
        String journal = runWithHeldOutCase(tempDir.resolve("thin"), 1, false);

        assertThat(journal)
                .as("one gating case beside one held-out case leaves too little headroom")
                .contains("held-out-check FAILED")
                .contains("| decision | DISCARD |");
        assertThat(scoreOf(journal))
                .as("the discard is by threshold arithmetic, not by a gate the held-out case tripped")
                .isLessThan(new java.math.BigDecimal("0.80"));
    }

    /**
     * Runs one generation with {@code gatingCases} passing behaviour cases and one held-out case that
     * either passes or fails, and returns the journal.
     */
    private static String runWithHeldOutCase(Path root, int gatingCases, boolean heldOutPasses)
            throws Exception {
        Path repo = root.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);

        var arguments = new ArrayList<>(List.of(
                "saaa-evolve", target.toString(), "--profile", "fixture", "--max-lines", "80"));
        for (int index = 1; index <= gatingCases; index++) {
            String caseName = "workflow-check-" + index;
            writeCheck(target, caseName, ENFORCED_CHECK);
            arguments.add("--behaviour-case");
            arguments.add(caseName);
        }
        // The held-out case reads the same realized workflow; only its expectation differs, so two
        // runs differ in the held-out outcome alone and in nothing else that feeds the score.
        writeCheck(target, "held-out-check", heldOutPasses ? ENFORCED_CHECK : NEVER_MATCHING_CHECK);
        arguments.add("--held-out-case");
        arguments.add("held-out-check");
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli())
                .execute(arguments.toArray(String[]::new));

        assertThat(exitCode).as("a failing held-out case must not fail the run").isZero();
        return Files.readString(target.resolve("journal.md"));
    }

    private static java.math.BigDecimal scoreOf(String journal) {
        return journal.lines()
                .filter(line -> line.startsWith("| score |"))
                .map(line -> new java.math.BigDecimal(line.split("\\|")[2].trim()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no score row in journal:\n" + journal));
    }

    private static final String ENFORCED_CHECK = """
            #!/usr/bin/env bash
            set -euo pipefail
            grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
            """;

    private static final String NEVER_MATCHING_CHECK = """
            #!/usr/bin/env bash
            set -euo pipefail
            grep -q '^this-will-never-match$' "$(dirname "$0")/workflow.txt"
            """;

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
        gitOutput(dir, args);
    }

    private static int runCliWithEnvironment(Map<String, String> environment, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(MutationLoopCli.class.getName());
        command.addAll(List.of(args));
        var processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        processBuilder.environment().putAll(environment);
        var process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("CLI failed with exit " + exitCode + "\n" + output);
        }
        return exitCode;
    }

    private static String gitOutput(Path dir, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        Files.createDirectories(dir);
        var process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", args) + "\n" + output);
        }
        return output;
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("could not locate repository root from " + Path.of("").toAbsolutePath());
    }
    /**
     * CHG-016 S5. A misconfigured budget is silently absorbed by scoring: budgetScore skips any
     * benchmark without a matching budget, so an unpaired or misspelled name leaves
     * cost_latency_budget at 1.0 and the candidate promotes as though it had been measured. Each
     * case is rejected before the loop starts, because it would otherwise change a recorded
     * promotion decision without saying so.
     */
    @Test
    void rejectsBenchmarkConfigurationThatWouldBeSilentlyIgnored(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        initRepo(repo);

        for (String[] flagsAndExpectation : new String[][] {
                {"without --benchmark", "--benchmark-budget", "publish=1.0"},
                {"without a matching --benchmark-budget", "--benchmark", "publish=.*Workflow.*"},
                {"not requested with --benchmark",
                        "--benchmark", "publish=.*Workflow.*", "--benchmark-budget", "typo=1.0"},
                {"must be a positive finite number",
                        "--benchmark", "publish=.*Workflow.*", "--benchmark-budget", "publish=0"},
                {"must be a positive finite number",
                        "--benchmark", "publish=.*Workflow.*", "--benchmark-budget", "publish=-1"},
                {"must be a positive finite number",
                        "--benchmark", "publish=.*Workflow.*", "--benchmark-budget", "publish=NaN"},
                {"must be a positive finite number",
                        "--benchmark", "publish=.*Workflow.*", "--benchmark-budget", "publish=Infinity"}}) {
            var err = new java.io.StringWriter();
            var command = new CommandLine(new MutationLoopCli());
            command.setErr(new java.io.PrintWriter(err, true));
            var arguments = new java.util.ArrayList<String>(java.util.List.of(
                    "saaa-evolve", target.toString(), "--behaviour-case", "workflow-check"));
            arguments.addAll(java.util.List.of(flagsAndExpectation).subList(1, flagsAndExpectation.length));

            int exitCode = command.execute(arguments.toArray(String[]::new));

            assertThat(exitCode).as("%s", (Object) arguments).isNotZero();
            assertThat(err.toString()).contains(flagsAndExpectation[0]);
            assertThat(Files.exists(target.resolve("journal.md")))
                    .as("no verdict was journalled")
                    .isFalse();
            assertThat(Files.exists(target.resolve(".saaa/candidates")))
                    .as("rejection happens before any candidate is created, so no run began")
                    .isFalse();
        }
    }

}
