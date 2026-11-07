package com.dreamthought.saaa.cli;

import static com.dreamthought.saaa.cli.OpenAiStubSupport.clearOpenAiCompatibleProfileConfig;
import static com.dreamthought.saaa.cli.OpenAiStubSupport.configureOpenAiCompatibleProfile;
import static com.dreamthought.saaa.cli.OpenAiStubSupport.openAiMutationResponse;
import static com.dreamthought.saaa.cli.OpenAiStubSupport.startOpenAiStub;
import static com.dreamthought.saaa.cli.OpenAiStubSupport.stubOpenAiMutationResponses;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * CHG-026 S7. A generation, run from the command line, has to leave a record of how it selected.
 *
 * <p>End to end on purpose, and asserted on both durable surfaces. The journal is what a person
 * reads; the experiment ledger is the audit record. A generation that reported a winner to a
 * terminal and left neither would make its selection unreproducible, which is the thing a total
 * order exists to prevent: the point of ranking deterministically is that a later reader can check
 * the selection rather than trust it.
 */
final class EvolveGenerationAcceptanceTest {
    @Test
    void candidatesThatRealiseTheSameFileRankOnce(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target);
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);
        var server = startOpenAiStub();
        try {
            stubOpenAiMutationResponses(server,
                    openAiMutationResponse("mut-one", "draft-check: enforce\n"),
                    openAiMutationResponse("mut-two", "draft-check: enforce\n"),
                    openAiMutationResponse("mut-three", "draft-check: enforce\n"));
            configureOpenAiCompatibleProfile(server);
            var output = new java.io.StringWriter();
            var command = new CommandLine(new MutationLoopCli());
            command.setOut(new java.io.PrintWriter(output, true));
            assertThat(command.execute("saaa-evolve", target.toString(),
                    "--profile", "openai-compatible", "--behaviour-case", "workflow-check",
                    "--run-id", "same-tree", "--candidates", "3")).isZero();

            try (var connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + repo.resolve(".saaa/experiments.sqlite").toAbsolutePath())) {
                assertThat(rankedCandidateIds(connection, "same-tree"))
                        .hasSize(1)
                        .containsExactly("candidate-same-tree-c1-mut-one");
                assertThat(singleText(connection,
                        "select requested_count from generations where run_id = ?", "same-tree"))
                        .isEqualTo("3");
                org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                    try {
                        softly.assertThat(singleText(connection, """
                                select group_concat(candidate_id || ':' || input_tokens || ':' || output_tokens, ',')
                                from (select * from candidate_usage
                                      where candidate_id like 'candidate-same-tree-c%' order by candidate_id)
                                """))
                                .isEqualTo("candidate-same-tree-c1-mut-one:1:1,"
                                        + "candidate-same-tree-c2-mut-two:1:1,candidate-same-tree-c3-mut-three:1:1");
                        softly.assertThat(singleText(connection, """
                                select sum(i) || ':' || sum(o) from (
                                  select input_tokens i, output_tokens o from candidate_usage
                                    where candidate_id like 'candidate-same-tree-c%'
                                  union all
                                  select input_tokens, output_tokens from generation_unevaluated
                                    where run_id = 'same-tree')
                                """))
                                .as("CHG-031 S3: duplicate evaluations still count in the whole spend")
                                .isEqualTo("3:3");
                    } catch (SQLException failure) {
                        throw new AssertionError(failure);
                    }
                });
                try (var rows = connection.createStatement().executeQuery("""
                        select candidate_id, same_as_candidate_id from generation_duplicates
                        where run_id = 'same-tree' order by candidate_id
                        """)) {
                    for (int index = 2; index <= 3; index++) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("candidate-same-tree-c" + index
                                + (index == 2 ? "-mut-two" : "-mut-three"));
                        assertThat(rows.getString(2)).isEqualTo("candidate-same-tree-c1-mut-one");
                    }
                    assertThat(rows.next()).isFalse();
                }
            }
            String journal = Files.readString(target.resolve("journal.md"));
            assertThat(journal).contains("| evidence | 3 candidates: 1 ranked, 2 duplicates, 0 without evidence |");
            assertThat(output.toString()).contains(
                    "generation 3 candidates: 1 ranked, 2 duplicates, 0 without evidence");
            assertThat(journal.lines().filter(line -> line.contains("duplicate  ")).count()).isEqualTo(2);
            assertThat(output.toString().lines().filter(line -> line.contains("duplicate  ")).count()).isEqualTo(2);
            for (int index = 2; index <= 3; index++) {
                String line = "duplicate  candidate-same-tree-c" + index
                        + (index == 2 ? "-mut-two" : "-mut-three")
                        + "  realised the same files as candidate-same-tree-c1-mut-one; not ranked";
                assertThat(journal).contains(line);
                assertThat(output.toString()).contains(line.replace("duplicate  ", "duplicate   "));
            }
        } finally {
            clearOpenAiCompatibleProfileConfig();
            server.stop();
        }
    }

    @Test
    void aFixtureGenerationRecordsEveryCandidatesUsageAsUnavailable(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
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
                "--run-id", "fixture-usage-run",
                "--candidates", "3");

        assertThat(exitCode).isZero();
        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + repo.resolve(".saaa/experiments.sqlite").toAbsolutePath());
                var statement = connection.prepareStatement("""
                        select candidate_id, provider, model, input_tokens, output_tokens, credits_consumed,
                               wall_clock_millis, retries
                        from candidate_usage
                        where candidate_id like 'candidate-fixture-usage-run-c%'
                        order by candidate_id
                        """)) {
            try (var rows = statement.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    count++;
                    for (int column = 2; column <= 8; column++) {
                        assertThat(rows.getObject(column))
                                .as("candidate %s column %s is unavailable", rows.getString(1), column)
                                .isNull();
                    }
                }
                assertThat(count).isEqualTo(3);
            }
        }
    }

    @Test
    void aGenerationRecordsItsRankingWinnerFingerprintAndSpread(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "saaa-evolve", target.toString(),
                "--behaviour-case", "workflow-check",
                "--run-id", "recorded-run",
                "--candidates", "3");

        assertThat(exitCode).isZero();

        String journal = Files.readString(target.resolve("journal.md"));
        assertThat(journal)
                .as("the journal carries the generation, not only the three candidates it evaluated")
                .contains("| evidence | 3 of 3 candidates |");

        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + repo.resolve(".saaa/experiments.sqlite").toAbsolutePath())) {
            assertThat(rankedCandidateIds(connection, "recorded-run"))
                    .as("the ledger holds the order, so the selection can be re-derived from it")
                    .hasSize(3)
                    .allSatisfy(id -> assertThat(id).startsWith("candidate-recorded-run-c"));
            assertThat(singleText(connection,
                    "select evaluated_count || ' of ' || requested_count from generations where run_id = ?",
                    "recorded-run"))
                    .isEqualTo("3 of 3");
            String winner = singleText(connection,
                    "select winner_candidate_id from generations where run_id = ?", "recorded-run");
            assertThat(singleText(connection,
                    "select decision from fitness_results where candidate_id = ?", winner))
                    .as("the recorded winner is a candidate its own gates promoted")
                    .isEqualTo("PROMOTE");
            assertThat(journal)
                    .as("the journal names the same winner the ledger does, rather than merely "
                            + "having a winner row")
                    .contains("| winner | " + winner + " |")
                    .contains("1. " + rankedCandidateIds(connection, "recorded-run").get(0) + "  ");
            assertThat(singleText(connection,
                    "select scoring_fingerprint from generations where run_id = ?", "recorded-run"))
                    .as("every candidate was measured under one scoring context")
                    .isNotBlank();
            assertThat(Double.parseDouble(singleText(connection,
                    "select cast(spread as text) from generations where run_id = ?", "recorded-run")))
                    .as("ranking discriminated; an equal-score generation is ADR-0002's revisit trigger")
                    .isGreaterThan(0.0);
        }
    }

    /**
     * S4 on the real command-line path, and the case that separates "the winner" from "the top of
     * the ranking". Whenever anything promoted they are the same candidate, because FitnessScore
     * orders decision-first — so every fixture with a promotion in it is blind to a store that
     * records rank one regardless of the gate decision. Independent review found that the
     * acceptance test above could not fail for exactly that defect; this one can.
     *
     * <p>Every candidate is discarded by making the declared behaviour case fail for all of them.
     * The run still exits zero, because a discarded candidate is a successful run.
     */
    @Test
    void aGenerationWhereNoCandidatePromotesRecordsNoWinner(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
        // Fails whatever the candidate did, so no candidate can clear its gates.
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                exit 1
                """);
        initRepo(repo);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "saaa-evolve", target.toString(),
                "--behaviour-case", "workflow-check",
                "--run-id", "no-winner-run",
                "--candidates", "2");

        assertThat(exitCode).as("a discarded candidate is a successful run").isZero();

        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + repo.resolve(".saaa/experiments.sqlite").toAbsolutePath())) {
            assertThat(rankedCandidateIds(connection, "no-winner-run"))
                    .as("both candidates were still evaluated and still ranked")
                    .hasSize(2);
            assertThat(singleText(connection,
                    "select coalesce(winner_candidate_id, 'none') from generations where run_id = ?",
                    "no-winner-run"))
                    .as("ranking selects among promotions; promoting the best of a bad generation "
                            + "would move the deciding step out of fixed code")
                    .isEqualTo("none");
        }
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("| winner | none promoted |");
    }

    /**
     * A generation that got as far as evaluating every candidate but collected no evidence did run;
     * it is not a successful empty result. Reusing its run id makes every candidate collide with the
     * worktrees from the first invocation, which exercises that distinction through the public CLI.
     */
    @Test
    void aGenerationWhereNoCandidateProducesEvidenceExitsOneAndNamesTheFirstFailure(@TempDir Path tempDir)
            throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);

        String[] arguments = {
                "saaa-evolve", target.toString(),
                "--behaviour-case", "workflow-check",
                "--run-id", "reused-generation",
                "--candidates", "2"
        };
        assertThat(new CommandLine(new MutationLoopCli()).execute(arguments)).isZero();

        var out = new java.io.StringWriter();
        var command = new CommandLine(new MutationLoopCli());
        command.setOut(new java.io.PrintWriter(out, true));

        int exitCode = command.execute(arguments);

        assertThat(exitCode).isEqualTo(1);
        assertThat(out.toString())
                .contains("no candidate produced evidence; first failure:")
                .contains("worktree")
                .contains("  journal    " + target.resolve("journal.md").toAbsolutePath());
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("| evidence | 0 of 2 candidates |");
    }

    private static List<String> rankedCandidateIds(java.sql.Connection connection, String runId)
            throws SQLException {
        var ids = new ArrayList<String>();
        try (var statement = connection.prepareStatement(
                "select candidate_id from generation_rankings where run_id = ? order by position")) {
            statement.setString(1, runId);
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getString(1));
                }
            }
        }
        return ids;
    }

    private static String singleText(java.sql.Connection connection, String sql, String... values)
            throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as(sql).isTrue();
                return resultSet.getString(1);
            }
        }
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
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed\n" + output);
        }
    }
}
