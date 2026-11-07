package com.dreamthought.saaa.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * CHG-031. A live attempt the model answered but validation rejected still spent tokens, and the
 * generation's cost is only complete if that spend is recorded against the attempt.
 */
final class RejectedAttemptUsageAcceptanceTest {
    private static final String BASE_URL_PROPERTY = "saaa.model.base-url";
    private static final String API_KEY_PROPERTY = "saaa.model.api-key";
    private static final String MODEL_NAME_PROPERTY = "saaa.model.name";

    /**
     * S1. Variant two answers with scope PROMPT, which scope validation rejects. Its row in
     * generation_unevaluated carries the provider, the model the provider declared and the token
     * counts of that very response, distinct from variant one's so a copied or constant value fails.
     */
    @Test
    void aRejectedLiveAttemptRecordsWhatItCost(@TempDir Path tempDir) throws Exception {
        var server = startStub();
        try {
            stubVariants(server,
                    response("mut-ok", "WORKFLOW_DEFINITION", "draft-check: enforce\n",
                            "declared-model-one", 101, 11),
                    response("mut-bad", "PROMPT", "draft-check: enforce\n# two\n",
                            "declared-model-two", 202, 22));
            configure(server);
            Path repo = initLiveRepo(tempDir);
            Path target = repo.resolve("toy");

            int exitCode = new CommandLine(new MutationLoopCli()).execute(
                    "saaa-evolve", target.toString(),
                    "--profile", "openai-compatible",
                    "--behaviour-case", "workflow-check",
                    "--run-id", "r1-run",
                    "--candidates", "2",
                    "--max-lines", "8");

            assertThat(exitCode).isZero();
            try (var connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + repo.resolve(".saaa/experiments.sqlite").toAbsolutePath());
                 var statement = connection.prepareStatement("""
                         select provider, model, input_tokens, output_tokens
                         from generation_unevaluated where run_id = ?
                         """)) {
                statement.setString(1, "r1-run");
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).as("the rejected attempt has a row").isTrue();
                    assertThat(rows.getString("provider")).isEqualTo("openai-compatible");
                    assertThat(rows.getString("model")).isEqualTo("declared-model-two");
                    assertThat(rows.getLong("input_tokens")).isEqualTo(202);
                    assertThat(rows.getLong("output_tokens")).isEqualTo(22);
                    assertThat(rows.next()).as("only the rejected attempt is unevaluated").isFalse();
                }
            }
            String journal = Files.readString(target.resolve("journal.md"));
            assertThat(lineContaining(journal, "no evidence"))
                    .contains("declared-model-two")
                    .contains("202")
                    .contains("22");
        } finally {
            clear();
            server.stop();
        }
    }

    /**
     * S3. Summing candidate_usage and generation_unevaluated for the run gives the whole spend:
     * 101 + 202 input and 11 + 22 output. Either table alone gives less.
     */
    @Test
    void aGenerationsWholeSpendIsTheSumOfBothTables(@TempDir Path tempDir) throws Exception {
        var server = startStub();
        try {
            stubVariants(server,
                    response("mut-ok", "WORKFLOW_DEFINITION", "draft-check: enforce\n",
                            "declared-model-one", 101, 11),
                    response("mut-bad", "PROMPT", "draft-check: enforce\n# two\n",
                            "declared-model-two", 202, 22));
            configure(server);
            Path repo = initLiveRepo(tempDir);
            Path target = repo.resolve("toy");

            int exitCode = new CommandLine(new MutationLoopCli()).execute(
                    "saaa-evolve", target.toString(),
                    "--profile", "openai-compatible",
                    "--behaviour-case", "workflow-check",
                    "--run-id", "r3-run",
                    "--candidates", "2",
                    "--max-lines", "8");

            assertThat(exitCode).isZero();
            try (var connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + repo.resolve(".saaa/experiments.sqlite").toAbsolutePath());
                 var statement = connection.prepareStatement("""
                         select sum(i), sum(o) from (
                           select input_tokens i, output_tokens o from candidate_usage
                             where candidate_id like 'candidate-' || ? || '-c%'
                           union all
                           select input_tokens, output_tokens from generation_unevaluated
                             where run_id = ?)
                         """)) {
                statement.setString(1, "r3-run");
                statement.setString(2, "r3-run");
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(303);
                    assertThat(rows.getLong(2)).isEqualTo(33);
                }
            }
        } finally {
            clear();
            server.stop();
        }
    }

    private static String lineContaining(String text, String needle) {
        return text.lines().filter(line -> line.contains(needle)).findFirst()
                .orElseThrow(() -> new AssertionError("no line containing '" + needle + "' in:\n" + text));
    }

    private static WireMockServer startStub() {
        var server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    private static void stubVariants(WireMockServer server, String... responses) {
        for (int index = 0; index < responses.length; index++) {
            int variant = index + 1;
            var stub = post(urlEqualTo("/v1/chat/completions"))
                    .inScenario("rejected-attempt")
                    .whenScenarioStateIs(index == 0 ? Scenario.STARTED : "answered-" + index)
                    .withRequestBody(matchingJsonPath("$.messages[1].content",
                            containing("variant " + variant + " of a generation")))
                    .withHeader("Authorization", equalTo("Bearer local-test-key"))
                    .willReturn(okJson(responses[index]));
            if (index < responses.length - 1) {
                stub.willSetStateTo("answered-" + (index + 1));
            }
            server.stubFor(stub);
        }
    }

    private static String response(
            String id, String scope, String patch, String declaredModel, long in, long out) {
        String mutation = "{\"id\":\"" + id + "\",\"summary\":\"enforce the draft check\",\"scope\":\""
                + scope + "\",\"patch\":\"" + escape(patch) + "\"}";
        return """
                {
                  "id": "chatcmpl-local",
                  "object": "chat.completion",
                  "created": 1,
                  "model": "%s",
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "%s"},
                     "finish_reason": "stop"}
                  ],
                  "usage": {"prompt_tokens": %d, "completion_tokens": %d, "total_tokens": %d}
                }
                """.formatted(declaredModel, escape(mutation), in, out, in + out);
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static void configure(WireMockServer server) {
        System.setProperty(BASE_URL_PROPERTY, server.baseUrl() + "/v1");
        System.setProperty(API_KEY_PROPERTY, "local-test-key");
        System.setProperty(MODEL_NAME_PROPERTY, "local-test-model");
    }

    private static void clear() {
        System.clearProperty(BASE_URL_PROPERTY);
        System.clearProperty(API_KEY_PROPERTY);
        System.clearProperty(MODEL_NAME_PROPERTY);
    }

    private static Path initLiveRepo(Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target);
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Path check = target.resolve("workflow-check.sh");
        Files.writeString(check, """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        check.toFile().setExecutable(true);
        git(repo, "init", "--initial-branch=main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "baseline");
        return repo;
    }

    private static void git(Path dir, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", args) + "\n" + output);
        }
    }
}
