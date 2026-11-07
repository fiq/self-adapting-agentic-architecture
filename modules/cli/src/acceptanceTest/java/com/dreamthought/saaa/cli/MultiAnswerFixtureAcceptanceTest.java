package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * CHG-033. {@code fixtures/multi-answer-workflow} exists so a generation has something to rank: the
 * toy fixture has one correct edit, so every passing candidate scores the same.
 *
 * <p>Each test copies the shipped fixture into a throwaway Git repository, installs one reference
 * answer as the canned mutation, and runs the real {@code saaa-evolve} command with the fixture's
 * own required and held-out cases. Nothing here restates what the fixture scripts check; the
 * assertions read the recorded decision, the recorded held-out results and the recorded score.
 */
final class MultiAnswerFixtureAcceptanceTest {
    private static final List<String> REQUIRED = List.of("has-a-gate", "keeps-every-knob", "recovery-exists");
    private static final List<String> HELD_OUT = List.of("reviewed", "tested", "staged-rollout");
    private static final List<String> ANSWERS = List.of(
            "review-tests-and-canary", "review-and-staged-rollout", "tests-only");

    /** What a run recorded: the gate decision, the raw score and each held-out case's outcome. */
    private record Outcome(String decision, BigDecimal score, Map<String, String> heldOut) {}

    @Test
    void theBaselineFailsARequiredCase(@TempDir Path tempDir) throws Exception {
        Path target = copyFixture(tempDir.resolve("direct"));

        assertThat(exitCodeOf(target, "has-a-gate"))
                .as("the baseline must fail at least one required case, or there is no transition")
                .isNotZero();
        assertThat(exitCodeOf(target, "keeps-every-knob")).isZero();
        assertThat(exitCodeOf(target, "recovery-exists")).isZero();

        // The same fact through the command: a mutation that leaves the gate failing is discarded,
        // and the canned mutation that clears it is promoted.
        Outcome ungated = evolve(tempDir.resolve("ungated"), List.of(
                "notify and keep everything else", "review: skip", "tests: skip",
                "rollout: all-at-once", "notify: slack", "recovery: manual"));
        assertThat(ungated.decision())
                .as("an edit that leaves the baseline's failing required case failing is discarded")
                .isEqualTo("DISCARD");
        Outcome canned = evolve(tempDir.resolve("canned"), cannedMutation());
        assertThat(canned.decision()).as("the shipped canned mutation clears it").isEqualTo("PROMOTE");
    }

    @Test
    void everyReferenceAnswerPassesTheGatesAndTheyScoreDifferently(@TempDir Path tempDir) throws Exception {
        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        for (String answer : ANSWERS) {
            outcomes.put(answer, evolve(tempDir.resolve(answer), answerLines(answer)));
        }

        outcomes.forEach((answer, outcome) -> assertThat(outcome.decision())
                .as("%s passes every required case and clears the threshold", answer)
                .isEqualTo("PROMOTE"));
        assertThat(outcomes.values().stream().map(Outcome::heldOut).distinct().count())
                .as("each answer earns a different held-out result: %s", outcomes)
                .isEqualTo(ANSWERS.size());
        assertThat(outcomes.values().stream().map(Outcome::score).distinct().count())
                .as("scores differ pairwise, so a generation of these has a spread: %s", outcomes)
                .isEqualTo(ANSWERS.size());
        assertThat(outcomes.get("review-tests-and-canary").score())
                .as("passing more held-out cases ranks higher")
                .isGreaterThan(outcomes.get("review-and-staged-rollout").score());
        assertThat(outcomes.get("review-and-staged-rollout").score())
                .isGreaterThan(outcomes.get("tests-only").score());
    }

    @Test
    void oneHeldOutFailureStillPromotes(@TempDir Path tempDir) throws Exception {
        Outcome outcome = evolve(tempDir.resolve("one-failure"), answerLines("review-and-staged-rollout"));

        assertThat(outcome.heldOut().values().stream().filter("FAILED"::equals).count())
                .as("this answer fails exactly one held-out case: %s", outcome.heldOut())
                .isEqualTo(1);
        assertThat(outcome.decision())
                .as("with six cases a held-out failure costs 0.40/6, so it must not trip RISK-010")
                .isEqualTo("PROMOTE");
        assertThat(outcome.score()).isGreaterThanOrEqualTo(new BigDecimal("0.80"));
    }

    private static Outcome evolve(Path root, List<String> mutationLines) throws Exception {
        Path repo = root.resolve("repo");
        Path target = copyFixture(repo.resolve("fixture"));
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"), String.join("\n", mutationLines) + "\n");
        initRepo(repo);

        var arguments = new ArrayList<>(List.of(
                "saaa-evolve", target.toString(), "--profile", "fixture", "--run-id", "multi-answer"));
        REQUIRED.forEach(name -> arguments.addAll(List.of("--behaviour-case", name)));
        HELD_OUT.forEach(name -> arguments.addAll(List.of("--held-out-case", name)));

        int exitCode = new CommandLine(new MutationLoopCli()).execute(arguments.toArray(String[]::new));
        assertThat(exitCode).as("a discarded candidate is still a successful run").isZero();

        String journal = Files.readString(target.resolve("journal.md"));
        Map<String, String> heldOut = new LinkedHashMap<>();
        for (String name : HELD_OUT) {
            Matcher matcher = Pattern.compile("(?<![\\w-])" + Pattern.quote(name) + " (PASSED|FAILED)")
                    .matcher(journal);
            assertThat(matcher.find())
                    .as("held-out case %s must appear as executed evidence, not be inferred:%n%s", name, journal)
                    .isTrue();
            heldOut.put(name, matcher.group(1));
        }
        return new Outcome(rowOf(journal, "decision"), new BigDecimal(rowOf(journal, "score")), heldOut);
    }

    private static String rowOf(String journal, String key) {
        return journal.lines()
                .filter(line -> line.startsWith("| " + key + " |"))
                .map(line -> line.split("\\|")[2].trim())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + key + " row in journal:\n" + journal));
    }

    private static List<String> answerLines(String answer) throws IOException {
        return Files.readAllLines(fixtureDir().resolve("answers/" + answer + ".txt"));
    }

    private static List<String> cannedMutation() throws IOException {
        return Files.readAllLines(fixtureDir().resolve(".saaa/fixture-mutation.txt"));
    }

    private static int exitCodeOf(Path target, String caseName) throws Exception {
        var process = new ProcessBuilder(target.resolve(caseName + ".sh").toString())
                .redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    /** The fixture is shipped at the repository root; the test runs from a module directory. */
    private static Path fixtureDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("fixtures/multi-answer-workflow");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("fixtures/multi-answer-workflow not found above " + System.getProperty("user.dir"));
    }

    /** Copies the shipped fixture, minus anything a run writes, preserving script permissions. */
    private static Path copyFixture(Path destination) throws IOException {
        Path source = fixtureDir();
        try (var paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path relative = source.relativize(path);
                if (relative.toString().equals("journal.md")) {
                    continue;
                }
                Path copy = destination.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copy);
                } else {
                    Files.createDirectories(copy.getParent());
                    Files.copy(path, copy);
                    copy.toFile().setExecutable(path.toFile().canExecute());
                }
            }
        }
        return destination;
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
