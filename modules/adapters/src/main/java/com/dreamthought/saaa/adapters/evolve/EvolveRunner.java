package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.adapters.checks.CommandCheckRunner;
import com.dreamthought.saaa.adapters.checks.CommandCheckRunner.CommandCheck;
import com.dreamthought.saaa.adapters.files.TextMutationRealizer;
import com.dreamthought.saaa.adapters.git.GitCandidateWorkspace;
import com.dreamthought.saaa.adapters.git.GitRealizationInspector;
import com.dreamthought.saaa.adapters.git.GitRepositoryRevision;
import com.dreamthought.saaa.adapters.git.JGitChangedPathInspector;
import com.dreamthought.saaa.adapters.journal.JournalDecisionSink;
import com.dreamthought.saaa.adapters.journal.JournalReporter;
import com.dreamthought.saaa.adapters.retrieval.LocalRetrievalFactory;
import com.dreamthought.saaa.adapters.retrieval.LocalEvolutionaryMemoryFactory;
import com.dreamthought.saaa.adapters.sqlite.SqliteExperimentMetadataStore;
import com.dreamthought.saaa.deterministic.EvolutionaryMemoryProjector;
import com.dreamthought.saaa.deterministic.EvolutionaryMemoryStore;
import com.dreamthought.saaa.deterministic.BoundedMutationValidator;
import com.dreamthought.saaa.deterministic.CompositeMutationValidator;
import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.deterministic.EvidenceRetriever;
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
import com.dreamthought.saaa.domain.MutationProposalRequest;
import com.dreamthought.saaa.domain.RetrievalBundle;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
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
import java.util.Optional;
import java.util.function.BiFunction;

public final class EvolveRunner {
    private final ProposerProfileRegistry profileRegistry;
    private final Clock clock;
    private final BiFunction<RetrievalMode, Path, EvidenceRetriever> retrievalResolver;
    private final BiFunction<RetrievalMode, Path, EvolutionaryMemoryStore> memoryResolver;

    public EvolveRunner() {
        this(new ProposerProfileRegistry(), Clock.systemUTC(), LocalRetrievalFactory::forMode,
                LocalEvolutionaryMemoryFactory::forMode);
    }

    public EvolveRunner(ProposerProfileRegistry profileRegistry, Clock clock) {
        this(profileRegistry, clock, LocalRetrievalFactory::forMode, LocalEvolutionaryMemoryFactory::forMode);
    }

    public EvolveRunner(
            ProposerProfileRegistry profileRegistry,
            Clock clock,
            BiFunction<RetrievalMode, Path, EvidenceRetriever> retrievalResolver
    ) {
        this(profileRegistry, clock, retrievalResolver, LocalEvolutionaryMemoryFactory::forMode);
    }

    public EvolveRunner(
            ProposerProfileRegistry profileRegistry,
            Clock clock,
            BiFunction<RetrievalMode, Path, EvidenceRetriever> retrievalResolver,
            BiFunction<RetrievalMode, Path, EvolutionaryMemoryStore> memoryResolver
    ) {
        this.profileRegistry = Objects.requireNonNull(profileRegistry, "profileRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retrievalResolver = Objects.requireNonNull(retrievalResolver, "retrievalResolver");
        this.memoryResolver = Objects.requireNonNull(memoryResolver, "memoryResolver");
    }

    public EvolveRunResult run(EvolveRunRequest request, EvolutionReporter reporter) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reporter, "reporter");
        long runStarted = System.nanoTime();

        Path folder = request.targetFolder().toAbsolutePath().normalize();
        Path gitRoot = findGitRoot(folder);
        Path workflowPath = folder.resolve(request.workflowFile()).normalize();
        if (!workflowPath.startsWith(folder)) {
            throw new IllegalArgumentException("workflowFile must stay inside targetFolder");
        }
        requireNoSymlinkSegments(folder, workflowPath);
        if (!Files.isRegularFile(workflowPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("workflow file not found: " + workflowPath);
        }
        var checks = BehaviourCaseChecks.forCases(request.behaviourCases(), gitRoot.relativize(folder));
        requireWorkflowIsNotCheckScript(gitRoot, workflowPath, checks);
        requireRunnableCheckScripts(gitRoot, checks);

        String relativeWorkflow = gitRoot.relativize(workflowPath).toString();
        String repositoryRevision = GitRepositoryRevision.workingTree(gitRoot);
        var evolutionContext = LocalEvolutionContext.resolve(gitRoot, repositoryRevision);
        var baseline = new WorkflowGraph(folder.getFileName().toString(), repositoryRevision, readString(workflowPath));
        MutationProposer proposer = profileRegistry.resolve(request.profile(), folder);
        var timedRetriever = new TimedRetriever(retrievalResolver.apply(request.retrievalMode(), gitRoot));
        var retrievalCapture = new RetrievalCapture();
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
                        proposer::proposerEvidence,
                        request.runId()),
                new CommandCheckRunner(checks),
                candidate -> List.of(),
                new PhenotypeBridgeScorer(
                        new GitRealizationInspector(),
                        new ScoringConfig(Set.copyOf(request.behaviourCases()), request.maxLines(), Map.of())),
                new SqliteExperimentMetadataStore(gitRoot.resolve(".saaa/experiments.sqlite")),
                new JournalDecisionSink(),
                new CompositeReporter(List.of(reporter, new JournalReporter(journalPath, clock), retrievalCapture)),
                clock,
                timedRetriever,
                new EvolutionaryMemoryProjector(
                        memoryResolver.apply(request.retrievalMode(), gitRoot),
                        LocalEvolutionaryMemoryFactory.policy().id(),
                        evolutionContext,
                        new JGitChangedPathInspector()));
        var query = new RetrievalQuery(
                request.retrievalMode(),
                request.task(),
                baseline,
                repositoryRevision,
                List.of(relativeWorkflow, baseline.id()),
                Optional.empty());
        var result = loop.evaluate(new MutationProposalRequest(baseline, query));
        long wallMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - runStarted);
        return new EvolveRunResult(result, journalPath, retrievalCapture.required(), proposer.proposerEvidence(),
                wallMillis, timedRetriever.elapsedMillis());
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

    private static void requireWorkflowIsNotCheckScript(
            Path gitRoot, Path workflowPath, List<CommandCheck> checks) {
        Path normalizedWorkflow = workflowPath.normalize();
        for (CommandCheck check : checks) {
            Path script = gitRoot.resolve(check.command().get(0)).normalize();
            if (normalizedWorkflow.equals(script)) {
                throw new IllegalArgumentException(
                        "workflowFile must not target the behaviour-case check script "
                                + script + "; a candidate cannot rewrite the file that grades it");
            }
        }
    }

    private static void requireNoSymlinkSegments(Path folder, Path workflowPath) {
        Path current = folder;
        for (Path segment : folder.relativize(workflowPath)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("workflowFile must not contain symlink path segments");
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

    private static final class RetrievalCapture implements EvolutionReporter {
        private RetrievalBundle retrieval;

        @Override
        public void retrievalPrepared(RetrievalBundle retrieval) {
            this.retrieval = retrieval;
        }

        private RetrievalBundle required() {
            return Objects.requireNonNull(retrieval, "retrieval was not prepared");
        }
    }

    private static final class TimedRetriever implements EvidenceRetriever {
        private final EvidenceRetriever delegate;
        private long elapsedNanos;

        private TimedRetriever(EvidenceRetriever delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public RetrievalBundle retrieve(RetrievalQuery query) {
            long started = System.nanoTime();
            try {
                return delegate.retrieve(query);
            } finally {
                elapsedNanos += System.nanoTime() - started;
            }
        }

        private long elapsedMillis() {
            return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        }
    }
}
