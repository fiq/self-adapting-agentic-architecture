package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.evolve.EvolveRunRequest;
import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import com.dreamthought.saaa.adapters.evolve.ProposerProfileRegistry;
import com.dreamthought.saaa.deterministic.HarnessSessionStateMachine;
import com.dreamthought.saaa.domain.EvolutionTargetKind;
import com.dreamthought.saaa.domain.HarnessSessionSnapshot;
import com.dreamthought.saaa.domain.HarnessSessionStatus;
import com.dreamthought.saaa.domain.HarnessSessionTarget;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** Line-oriented interactive client for the SAAA-owned harness control plane. */
@Command(
        name = "sa",
        description = "Start an interactive SAAA harness session."
)
public final class SaCommand implements Callable<Integer> {
    private static final int DEFAULT_MAX_LINES = 80;
    private static final String DEFAULT_TASK = "Improve the target while preserving all declared behaviour cases";

    private final BufferedReader input;
    private final PrintWriter output;
    private final EvolveRunner evolveRunner;
    private final List<String> routes;

    public SaCommand() {
        this(new BufferedReader(new InputStreamReader(System.in)), new PrintWriter(System.out, true),
                new EvolveRunner(), new ProposerProfileRegistry().knownNames());
    }

    SaCommand(BufferedReader input, PrintWriter output, EvolveRunner evolveRunner) {
        this(input, output, evolveRunner, new ProposerProfileRegistry().knownNames());
    }

    SaCommand(BufferedReader input, PrintWriter output, EvolveRunner evolveRunner, List<String> routes) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.evolveRunner = Objects.requireNonNull(evolveRunner, "evolveRunner");
        this.routes = List.copyOf(Objects.requireNonNull(routes, "routes"));
        if (this.routes.isEmpty()) {
            throw new IllegalArgumentException("at least one route is required");
        }
    }

    @Override
    public Integer call() {
        var session = new HarnessSessionStateMachine(routes.get(0));
        output.println("sa session ACTIVE; type help for commands");
        try {
            while (session.snapshot().status() == HarnessSessionStatus.ACTIVE) {
                String line = input.readLine();
                if (line == null) {
                    session.close();
                    renderStatus(session.snapshot());
                    break;
                }
                handle(session, line);
            }
            return 0;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read interactive session input", exception);
        }
    }

    private void handle(HarnessSessionStateMachine session, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String[] parts = trimmed.split("\\s+", 3);
        try {
            switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "help" -> renderHelp();
                case "status" -> renderStatus(session.snapshot());
                case "capabilities" -> renderCapabilities();
                case "skills" -> renderSkills();
                case "target" -> selectTarget(session, parts);
                case "route" -> selectRoute(session, parts);
                case "evolve" -> evolve(session, parts);
                case "quit", "close" -> renderStatus(session.close());
                default -> output.println("error unknown command: " + parts[0]);
            }
        } catch (RuntimeException exception) {
            // A failed command reports and returns to the prompt; only EOF or an explicit close
            // ends the session.
            output.println("error " + describe(exception));
        }
    }

    private void selectTarget(HarnessSessionStateMachine session, String[] parts) {
        if (parts.length != 3) {
            throw new IllegalArgumentException("usage: target <HARNESS_WORKFLOW|CODE> <folder>");
        }
        EvolutionTargetKind kind;
        try {
            kind = EvolutionTargetKind.valueOf(parts[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown target kind: " + parts[1]);
        }
        var snapshot = session.selectTarget(new HarnessSessionTarget(kind, Path.of(parts[2])));
        output.printf("target %s %s%n", snapshot.target().orElseThrow().kind(),
                snapshot.target().orElseThrow().folder());
    }

    private void selectRoute(HarnessSessionStateMachine session, String[] parts) {
        if (parts.length != 2) {
            throw new IllegalArgumentException("usage: route <profile>");
        }
        if (!routes.contains(parts[1])) {
            throw new IllegalArgumentException("unknown route: " + parts[1] + "; known routes: "
                    + String.join(", ", routes));
        }
        output.println("route " + session.selectRoute(parts[1]).route());
    }

    private void evolve(HarnessSessionStateMachine session, String[] parts) {
        if (parts.length != 3) {
            throw new IllegalArgumentException("usage: evolve <workflow-file> <behaviour-case>...");
        }
        var target = session.snapshot().target()
                .orElseThrow(() -> new IllegalStateException("select a target before evolve"));
        List<String> behaviourCases = List.of(parts[2].split("\\s+"));
        var result = evolveRunner.run(new EvolveRunRequest(
                target.folder(), session.snapshot().route(), parts[1], behaviourCases,
                DEFAULT_MAX_LINES, RetrievalMode.NONE, DEFAULT_TASK), new ConsoleReporter(output));
        output.println("session decision " + result.fitnessResult().decision());
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void renderStatus(HarnessSessionSnapshot snapshot) {
        output.println("state " + snapshot.status());
        output.println("route " + snapshot.route());
        snapshot.target().ifPresentOrElse(
                target -> output.printf("target %s %s%n", target.kind(), target.folder()),
                () -> output.println("target NONE"));
    }

    private void renderCapabilities() {
        output.println("capability evolve-harness-workflow target=HARNESS_WORKFLOW executable=true");
        output.println("capability evolve-code target=CODE executable=true realization=whole-file");
        output.println("routes " + String.join(", ", routes));
    }

    private void renderSkills() {
        output.println("skill select-target choose an explicit evolution target");
        output.println("skill choose-route select an explicit registered proposer profile");
        output.println("skill evolve-governed run the existing deterministic evolution loop");
    }

    private void renderHelp() {
        output.println("commands: status, capabilities, skills, target, route, evolve, quit");
        output.println("target <HARNESS_WORKFLOW|CODE> <folder>");
        output.println("route <profile>");
        output.println("evolve <workflow-file> <behaviour-case>...");
    }
}
