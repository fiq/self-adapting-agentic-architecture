package com.dreamthought.saaa.cli;

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
