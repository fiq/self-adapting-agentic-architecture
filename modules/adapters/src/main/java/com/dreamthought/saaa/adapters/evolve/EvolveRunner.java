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
import com.dreamthought.saaa.deterministic.BenchmarkRunner;
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
import java.util.ArrayList;
import java.util.Collection;
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
    private final BenchmarkRunner benchmarkRunner;

    public EvolveRunner() {
        this(new ProposerProfileRegistry(), Clock.systemUTC(), LocalRetrievalFactory::forMode,
                LocalEvolutionaryMemoryFactory::forMode, candidate -> List.of());
    }

    /**
     * Composes the default proposer, clock and retrieval wiring with an injected
     * {@link BenchmarkRunner}, so a caller can supply a real evidence source, such as
     * {@code JmhBenchmarkRunner} from {@code :benchmarks}, without also having to know or
     * reconstruct the other defaults. See C3 / README "cost_latency_budget cannot be measured".
     */
    public EvolveRunner(BenchmarkRunner benchmarkRunner) {
        this(new ProposerProfileRegistry(), Clock.systemUTC(), LocalRetrievalFactory::forMode,
                LocalEvolutionaryMemoryFactory::forMode, benchmarkRunner);
    }

    public EvolveRunner(ProposerProfileRegistry profileRegistry, Clock clock) {
        this(profileRegistry, clock, LocalRetrievalFactory::forMode, LocalEvolutionaryMemoryFactory::forMode,
                candidate -> List.of());
    }

    public EvolveRunner(
            ProposerProfileRegistry profileRegistry,
            Clock clock,
            BiFunction<RetrievalMode, Path, EvidenceRetriever> retrievalResolver
    ) {
        this(profileRegistry, clock, retrievalResolver, LocalEvolutionaryMemoryFactory::forMode,
                candidate -> List.of());
    }

    public EvolveRunner(
            ProposerProfileRegistry profileRegistry,
            Clock clock,
            BiFunction<RetrievalMode, Path, EvidenceRetriever> retrievalResolver,
            BiFunction<RetrievalMode, Path, EvolutionaryMemoryStore> memoryResolver
    ) {
        this(profileRegistry, clock, retrievalResolver, memoryResolver, candidate -> List.of());
    }

    public EvolveRunner(
            ProposerProfileRegistry profileRegistry,
            Clock clock,
            BiFunction<RetrievalMode, Path, EvidenceRetriever> retrievalResolver,
            BiFunction<RetrievalMode, Path, EvolutionaryMemoryStore> memoryResolver,
            BenchmarkRunner benchmarkRunner
    ) {
        this.profileRegistry = Objects.requireNonNull(profileRegistry, "profileRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retrievalResolver = Objects.requireNonNull(retrievalResolver, "retrievalResolver");
        this.memoryResolver = Objects.requireNonNull(memoryResolver, "memoryResolver");
        this.benchmarkRunner = Objects.requireNonNull(benchmarkRunner, "benchmarkRunner");
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
        // Probes are executed alongside behaviour cases so the objective has evidence to score. They
        // are separated again at the scorer, which withholds them from the deterministic-checks gate;
        // without running them here every probe would count as absent and the objective would always
        // read 0.0 for a caller who declared any.
        var caseAndProbeNames = new ArrayList<>(request.behaviourCases());
        request.safetyProbes().stream()
                .filter(name -> !caseAndProbeNames.contains(name))
                .forEach(caseAndProbeNames::add);
        // Held-out cases must execute for the same reason probes must: the scorer records a declared
        // case that produced no evidence as failed, so a held-out case that never ran would lower
        // `task_success` from absence rather than from measurement, and the run would look like it
        // had measured something it never executed. Adding them here also brings them under
        // `requireRunnableCheckScripts` and `requireWorkflowIsNotCheckScript` below.
        request.heldOutCases().stream()
                .filter(name -> !caseAndProbeNames.contains(name))
                .forEach(caseAndProbeNames::add);
        // Repeats are added after the scripts are resolved, so a repeat cannot introduce a new script
        // path: it re-runs one that already had to exist and be executable.
        var checks = BehaviourCaseChecks.withRepeatedRuns(
                BehaviourCaseChecks.forCases(caseAndProbeNames, gitRoot.relativize(folder)),
                request.behaviourCases(), request.reliabilityRuns());
        requireWorkflowIsNotCheckScript(gitRoot, workflowPath, checks);
        requireRunnableCheckScripts(gitRoot, checks, request.safetyProbes());

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
                benchmarkRunner,
                new PhenotypeBridgeScorer(
                        new GitRealizationInspector(),
                        new ScoringConfig(
                                Set.copyOf(request.behaviourCases()), request.maxLines(),
                                request.benchmarkBudgets(), Set.copyOf(request.safetyProbes()),
                                request.reliabilityRuns(), Set.copyOf(request.heldOutCases()))),
                new SqliteExperimentMetadataStore(gitRoot.resolve(".saaa/experiments.sqlite")),
                new JournalDecisionSink(),
                new CompositeReporter(List.of(reporter, new JournalReporter(journalPath, clock), retrievalCapture)),
                clock,
                timedRetriever,
                new EvolutionaryMemoryProjector(
                        memoryResolver.apply(request.retrievalMode(), gitRoot),
                        LocalEvolutionaryMemoryFactory.policy().id(),
                        evolutionContext,
                        new JGitChangedPathInspector()),
                request.contract());
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

    private static void requireRunnableCheckScripts(
            Path gitRoot, List<CommandCheck> checks, Collection<String> probeNames) {
        for (CommandCheck check : checks) {
            // Name the option the caller actually passed. Probes travel with the behaviour cases so
            // they get executed, and calling a bad probe a behaviour case sends the reader to the
            // wrong flag.
            String kind = probeNames.contains(check.name()) ? "safety probe" : "behaviour case";
            Path script = gitRoot.resolve(check.command().get(0)).normalize();
            if (!Files.isRegularFile(script, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(
                        kind + " " + check.name() + " needs a regular script file, which "
                                + script + " is not; a symlinked check script is refused because it can "
                                + "point outside the candidate");
            }
            if (!Files.isExecutable(script)) {
                throw new IllegalArgumentException(
                        kind + " " + check.name() + " has a script that is not executable: " + script);
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
