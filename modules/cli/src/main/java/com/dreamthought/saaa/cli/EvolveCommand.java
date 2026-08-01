package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.checks.CommandCheckRunner;
import com.dreamthought.saaa.adapters.checks.CommandCheckRunner.CommandCheck;
import com.dreamthought.saaa.adapters.git.GitCandidateWorkspace;
import com.dreamthought.saaa.adapters.git.GitRealizationInspector;
import com.dreamthought.saaa.adapters.files.TextMutationRealizer;
import com.dreamthought.saaa.adapters.journal.JournalDecisionSink;
import com.dreamthought.saaa.adapters.journal.JournalReporter;
import com.dreamthought.saaa.deterministic.BoundedMutationValidator;
import com.dreamthought.saaa.deterministic.CompositeMutationValidator;
import com.dreamthought.saaa.deterministic.DiffLineBudgetMutationValidator;
import com.dreamthought.saaa.deterministic.ExperimentMetadataStore;
import com.dreamthought.saaa.deterministic.MutationEvaluationLoop;
import com.dreamthought.saaa.deterministic.MutationProposer;
import com.dreamthought.saaa.deterministic.MutationScopeValidator;
import com.dreamthought.saaa.deterministic.PhenotypeBridgeScorer;
import com.dreamthought.saaa.deterministic.ScoringConfig;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "evolve",
        description = "Run one mutation evaluation against a target folder."
)
public final class EvolveCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Folder containing the workflow being evolved.")
    private Path targetFolder;

    @Option(names = "--profile", defaultValue = "fixture", description = "Proposer profile name.")
    private String profile;

    @Option(names = "--workflow-file", defaultValue = "workflow.txt",
            description = "File inside the target folder that is being evolved.")
    private String workflowFile;

    @Option(names = "--behaviour-case", required = true,
            description = "Name of a check that is required behaviour and hard-gates promotion.")
    private List<String> behaviourCases;

    @Option(names = "--max-lines", defaultValue = "80",
            description = "Change budget that parsimony is scored against.")
    private int maxLines;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        Path folder = targetFolder.toAbsolutePath().normalize();
        Path gitRoot = findGitRoot(folder);
        Path workflowPath = folder.resolve(workflowFile);
        if (!Files.isRegularFile(workflowPath)) {
            throw new IllegalArgumentException("workflow file not found: " + workflowPath);
        }
        var checks = BehaviourCaseChecks.forCases(behaviourCases, gitRoot.relativize(folder));
        requireRunnableCheckScripts(gitRoot, checks);

        String relativeWorkflow = gitRoot.relativize(workflowPath).toString();
        var baseline = new WorkflowGraph(folder.getFileName().toString(), "baseline", readString(workflowPath));

        // JournalReporter accumulates mutation/candidate/evidence across its callbacks and never
        // resets, so it must be constructed fresh for this run rather than reused or hoisted.
        var reporter = new CompositeReporter(List.of(
                new ConsoleReporter(out),
                new JournalReporter(folder.resolve("journal.md"), Clock.systemUTC())));
        MutationProposer proposer = new ProposerProfileRegistry().resolve(profile, folder);

        var loop = new MutationEvaluationLoop(
                proposer,
                new CompositeMutationValidator(List.of(
                        new BoundedMutationValidator(),
                        new MutationScopeValidator(Set.of(MutationScope.WORKFLOW_DEFINITION)),
                        new DiffLineBudgetMutationValidator(maxLines))),
                new GitCandidateWorkspace(
                        gitRoot,
                        gitRoot.resolve(".worktrees"),
                        new TextMutationRealizer(relativeWorkflow),
                        proposer::proposerEvidence),
                new CommandCheckRunner(checks),
                candidate -> List.of(),
                new PhenotypeBridgeScorer(
                        new GitRealizationInspector(),
                        new ScoringConfig(Set.copyOf(behaviourCases), maxLines, Map.of())),
                new NoOpMetadataStore(),
                new JournalDecisionSink(),
                reporter,
                Clock.systemUTC());

        loop.evaluate(baseline);
        out.printf("  journal    %s%n", folder.resolve("journal.md"));
        out.flush();
        return 0;
    }

    /**
     * Catches the common setup mistakes early, because a check that cannot start is reported as a
     * failed check and reads identically to a candidate that genuinely broke the behaviour.
     *
     * <p>This inspects the coordination checkout, not the candidate worktree, which does not exist
     * yet. The two can disagree: the candidate is created from {@code HEAD}, so a script that is
     * present here but uncommitted, or executable here but mode 100644 in {@code HEAD}, still passes
     * this check and then fails to start inside the candidate. It is a fast typo catcher rather than
     * a guarantee. Containment of the program that finally runs is enforced by
     * {@code CommandCheckRunner} against the candidate itself.
     */
    private static void requireRunnableCheckScripts(Path gitRoot, List<CommandCheck> checks) {
        for (CommandCheck check : checks) {
            Path script = gitRoot.resolve(check.command().get(0)).normalize();
            // NOFOLLOW_LINKS so a symlinked script is named here rather than surfacing later as a
            // containment failure from inside the candidate.
            if (!Files.isRegularFile(script, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(
                        "behaviour case " + check.name() + " needs a regular script file, which "
                                + script + " is not; a symlinked check script is refused because it can "
                                + "point outside the candidate");
            }
            if (!Files.isExecutable(script)) {
                throw new IllegalArgumentException(
                        "behaviour case " + check.name() + " has a script that is not executable: " + script);
            }
        }
    }

    private static Path findGitRoot(Path folder) {
        for (Path current = folder; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
        }
        throw new IllegalArgumentException("target folder is not inside a Git repository: " + folder);
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read " + path, exception);
        }
    }

    /**
     * Persistent experiment metadata arrives with CHG-002 task T5. Keeping this a no-op avoids
     * pulling SQLite into the CLI before promotion semantics exist.
     */
    private static final class NoOpMetadataStore implements ExperimentMetadataStore {
        @Override
        public void recordCandidate(Candidate candidate) {
            // no persistent store in this slice
        }

        @Override
        public void recordFitness(FitnessResult result) {
            // no persistent store in this slice
        }
    }
}
