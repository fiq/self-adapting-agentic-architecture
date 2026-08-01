package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.adapters.checks.CommandCheckRunner;
import com.dreamthought.saaa.adapters.checks.CommandCheckRunner.CommandCheck;
import com.dreamthought.saaa.adapters.files.TextMutationRealizer;
import com.dreamthought.saaa.adapters.git.GitCandidateWorkspace;
import com.dreamthought.saaa.adapters.git.GitRealizationInspector;
import com.dreamthought.saaa.adapters.journal.JournalDecisionSink;
import com.dreamthought.saaa.adapters.journal.JournalReporter;
import com.dreamthought.saaa.deterministic.BoundedMutationValidator;
import com.dreamthought.saaa.deterministic.CompositeMutationValidator;
import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.deterministic.ExperimentMetadataStore;
import com.dreamthought.saaa.deterministic.MutationProposer;
import com.dreamthought.saaa.deterministic.MutationEvaluationLoop;
import com.dreamthought.saaa.deterministic.MutationScopeValidator;
import com.dreamthought.saaa.deterministic.DiffLineBudgetMutationValidator;
import com.dreamthought.saaa.deterministic.PhenotypeBridgeScorer;
import com.dreamthought.saaa.deterministic.ScoringConfig;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EvolveRunner {
    private final ProposerProfileRegistry profileRegistry;
    private final Clock clock;

    public EvolveRunner() {
        this(new ProposerProfileRegistry(), Clock.systemUTC());
    }

    public EvolveRunner(ProposerProfileRegistry profileRegistry, Clock clock) {
        this.profileRegistry = Objects.requireNonNull(profileRegistry, "profileRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public EvolveRunResult run(EvolveRunRequest request, EvolutionReporter reporter) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reporter, "reporter");

        Path folder = request.targetFolder().toAbsolutePath().normalize();
        Path gitRoot = findGitRoot(folder);
        Path workflowPath = folder.resolve(request.workflowFile());
        if (!Files.isRegularFile(workflowPath)) {
            throw new IllegalArgumentException("workflow file not found: " + workflowPath);
        }
        var checks = BehaviourCaseChecks.forCases(request.behaviourCases(), gitRoot.relativize(folder));
        requireRunnableCheckScripts(gitRoot, checks);

        String relativeWorkflow = gitRoot.relativize(workflowPath).toString();
        var baseline = new WorkflowGraph(folder.getFileName().toString(), "baseline", readString(workflowPath));
        MutationProposer proposer = profileRegistry.resolve(request.profile(), folder);
        Path journalPath = folder.resolve("journal.md");
        var loop = new MutationEvaluationLoop(
                proposer,
                new CompositeMutationValidator(List.of(
                        new BoundedMutationValidator(),
                        new MutationScopeValidator(Set.of(MutationScope.WORKFLOW_DEFINITION)),
                        new DiffLineBudgetMutationValidator(request.maxLines()))),
                new GitCandidateWorkspace(
                        gitRoot,
                        gitRoot.resolve(".worktrees"),
                        new TextMutationRealizer(relativeWorkflow),
                        proposer::proposerEvidence),
                new CommandCheckRunner(checks),
                candidate -> List.of(),
                new PhenotypeBridgeScorer(
                        new GitRealizationInspector(),
                        new ScoringConfig(Set.copyOf(request.behaviourCases()), request.maxLines(), Map.of())),
                new NoOpMetadataStore(),
                new JournalDecisionSink(),
                new CompositeReporter(List.of(reporter, new JournalReporter(journalPath, clock))),
                clock);
        return new EvolveRunResult(loop.evaluate(baseline), journalPath);
    }

    private static void requireRunnableCheckScripts(Path gitRoot, List<CommandCheck> checks) {
        for (CommandCheck check : checks) {
            Path script = gitRoot.resolve(check.command().get(0)).normalize();
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

    private static final class NoOpMetadataStore implements ExperimentMetadataStore {
        @Override
        public void recordCandidate(Candidate candidate) {
            // persistent experiment metadata arrives with CHG-002 task T5
        }

        @Override
        public void recordFitness(FitnessResult result) {
            // persistent experiment metadata arrives with CHG-002 task T5
        }
    }
}
