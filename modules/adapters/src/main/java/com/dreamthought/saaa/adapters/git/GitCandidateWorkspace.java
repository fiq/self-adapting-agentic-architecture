package com.dreamthought.saaa.adapters.git;

import com.dreamthought.saaa.deterministic.CandidateWorkspace;
import com.dreamthought.saaa.deterministic.MutationRealizer;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.ProposerEvidence;
import com.dreamthought.saaa.domain.RetrievalProvenance;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class GitCandidateWorkspace implements CandidateWorkspace {
    private static final String COMMIT_AUTHOR_NAME = "Self Adapting Agentic Architecture";
    private static final String COMMIT_AUTHOR_EMAIL = "saaa@example.invalid";

    private final Path repositoryRoot;
    private final Path worktreesRoot;
    private final MutationRealizer realizer;
    private final Supplier<Optional<ProposerEvidence>> proposerEvidence;
    private final ProposerEvidenceSanitizer proposerEvidenceSanitizer;
    private final java.util.Optional<String> candidateNamespace;

    public GitCandidateWorkspace() {
        this(Path.of(".").toAbsolutePath().normalize());
    }

    public GitCandidateWorkspace(Path repositoryRoot) {
        this(repositoryRoot, repositoryRoot.resolve(".worktrees"));
    }

    public GitCandidateWorkspace(Path repositoryRoot, Path worktreesRoot) {
        this(repositoryRoot, worktreesRoot, (worktree, baseline, mutation) -> { });
    }

    public GitCandidateWorkspace(Path repositoryRoot, Path worktreesRoot, MutationRealizer realizer) {
        this(repositoryRoot, worktreesRoot, realizer, Optional::empty);
    }

    public GitCandidateWorkspace(
            Path repositoryRoot,
            Path worktreesRoot,
            MutationRealizer realizer,
            Supplier<Optional<ProposerEvidence>> proposerEvidence
    ) {
        this(repositoryRoot, worktreesRoot, realizer, proposerEvidence,
                new ProposerEvidenceSanitizer(), java.util.Optional.empty());
    }

    public GitCandidateWorkspace(
            Path repositoryRoot,
            Path worktreesRoot,
            MutationRealizer realizer,
            Supplier<Optional<ProposerEvidence>> proposerEvidence,
            java.util.Optional<String> candidateNamespace
    ) {
        this(repositoryRoot, worktreesRoot, realizer, proposerEvidence,
                new ProposerEvidenceSanitizer(), candidateNamespace);
    }

    public GitCandidateWorkspace(
            Path repositoryRoot,
            Path worktreesRoot,
            MutationRealizer realizer,
            Supplier<Optional<ProposerEvidence>> proposerEvidence,
            ProposerEvidenceSanitizer proposerEvidenceSanitizer
    ) {
        this(repositoryRoot, worktreesRoot, realizer, proposerEvidence,
                proposerEvidenceSanitizer, java.util.Optional.empty());
    }

    private GitCandidateWorkspace(
            Path repositoryRoot,
            Path worktreesRoot,
            MutationRealizer realizer,
            Supplier<Optional<ProposerEvidence>> proposerEvidence,
            ProposerEvidenceSanitizer proposerEvidenceSanitizer,
            java.util.Optional<String> candidateNamespace
    ) {
        this.repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
        this.worktreesRoot = Objects.requireNonNull(worktreesRoot, "worktreesRoot").toAbsolutePath().normalize();
        this.realizer = Objects.requireNonNull(realizer, "realizer");
        this.proposerEvidence = Objects.requireNonNull(proposerEvidence, "proposerEvidence");
        this.proposerEvidenceSanitizer = Objects.requireNonNull(proposerEvidenceSanitizer, "proposerEvidenceSanitizer");
        this.candidateNamespace = Objects.requireNonNull(candidateNamespace, "candidateNamespace")
                .map(value -> safeSegment(value, "candidateNamespace"));
    }

    @Override
    public Candidate createCommittedCandidate(WorkflowGraph baseline, Mutation mutation) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutation, "mutation");
        requireDirectory(repositoryRoot, "repositoryRoot");
        createDirectories(worktreesRoot);

        String workflowSegment = safeSegment(baseline.id(), "baseline.id");
        String mutationSegment = safeSegment(mutation.id(), "mutation.id");
        String namespace = candidateNamespace.map(value -> value + "-").orElse("");
        String candidateId = "candidate-" + namespace + mutationSegment;
        String branchName = "candidate/" + workflowSegment + "-" + namespace + mutationSegment;
        Path worktreePath = worktreesRoot.resolve("candidate-" + workflowSegment + "-" + namespace + mutationSegment)
                .toAbsolutePath()
                .normalize();

        if (Files.exists(worktreePath)) {
            throw new IllegalStateException("candidate worktree already exists: " + worktreePath);
        }

        git(repositoryRoot, "worktree", "add", "-b", branchName, worktreePath.toString(), "HEAD")
                .requireSuccess("create candidate worktree");

        Path candidateFile = worktreePath.resolve(".saaa/candidates/" + candidateId + ".toon");
        createDirectories(candidateFile.getParent());
        writeString(candidateFile, candidateDocument(
                candidateId,
                branchName,
                baseline,
                mutation,
                proposerEvidence.get(),
                proposerEvidenceSanitizer));

        realizer.realize(worktreePath, baseline, mutation);

        git(worktreePath, "add", "-A").requireSuccess("stage candidate changes");
        git(
                worktreePath,
                "-c",
                "user.name=" + COMMIT_AUTHOR_NAME,
                "-c",
                "user.email=" + COMMIT_AUTHOR_EMAIL,
                "commit",
                "-m",
                "Create candidate " + candidateId
        ).requireSuccess("commit candidate");

        String commitSha = git(worktreePath, "rev-parse", "HEAD")
                .requireSuccess("read candidate commit")
                .trim();
        return new Candidate(candidateId, mutation.id(), branchName, worktreePath, commitSha);
    }

    private static void requireDirectory(Path path, String name) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(name + " must be an existing directory: " + path);
        }
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create directory: " + path, exception);
        }
    }

    private static void writeString(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write candidate file: " + path, exception);
        }
    }

    private static String safeSegment(String value, String name) {
        String segment = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("(^[-.]+|[-.]+$)", "");
        if (segment.isBlank()) {
            throw new IllegalArgumentException(name + " does not contain branch-safe characters");
        }
        return segment;
    }

    private static String candidateDocument(
            String candidateId,
            String branchName,
            WorkflowGraph baseline,
            Mutation mutation,
            Optional<ProposerEvidence> proposerEvidence,
            ProposerEvidenceSanitizer proposerEvidenceSanitizer
    ) {
        return """
                candidate:
                  id: %s
                  mutation_id: %s
                  branch_name: %s
                baseline:
                  workflow_id: %s
                  version: %s
                  definition: |
                %s
                mutation:
                  id: %s
                  scope: %s
                  summary: |
                %s
                  patch: |
                %s
                %s
                """.formatted(
                candidateId,
                mutation.id(),
                branchName,
                baseline.id(),
                baseline.version(),
                indentBlock(baseline.definition()),
                mutation.id(),
                mutation.scope().name(),
                indentBlock(mutation.summary()),
                indentBlock(mutation.patch()),
                proposerBlock(proposerEvidence, proposerEvidenceSanitizer)
        );
    }

    private static String proposerBlock(
            Optional<ProposerEvidence> evidence,
            ProposerEvidenceSanitizer proposerEvidenceSanitizer
    ) {
        if (evidence.isEmpty()) {
            return "";
        }
        ProposerEvidence proposer = evidence.get();
        StringBuilder builder = new StringBuilder()
                .append("proposer:\n")
                .append("  id: ")
                .append(proposer.proposerId())
                .append("\n");
        proposer.attributes().forEach((key, value) -> builder
                .append("  ")
                .append(key)
                .append(": |\n")
                .append(indentBlock(proposerEvidenceSanitizer.sanitize(value)))
                .append("\n"));
        proposer.retrieval().ifPresent(retrieval -> builder.append(retrievalBlock(retrieval, proposerEvidenceSanitizer)));
        return builder.toString();
    }

    private static String retrievalBlock(
            RetrievalProvenance retrieval,
            ProposerEvidenceSanitizer sanitizer
    ) {
        StringBuilder builder = new StringBuilder()
                .append("  retrieval:\n")
                .append("    mode: ").append(retrieval.mode()).append("\n")
                .append("    configuration_id: ").append(retrieval.configurationId()).append("\n")
                .append("    repository_revision: ").append(retrieval.repositoryRevision()).append("\n")
                .append("    graph_schema_version: ").append(retrieval.graphSchemaVersion()).append("\n")
                .append("    capsule_projection_version: ").append(retrieval.capsuleProjectionVersion()).append("\n")
                .append("    ranking_version: ").append(retrieval.rankingVersion()).append("\n")
                .append("    embedding_model_id: ").append(retrieval.embeddingModelId()).append("\n")
                .append("    memory_policy_id: ").append(retrieval.memoryPolicyId()).append("\n")
                .append("    historical_weight_cap: ").append(retrieval.diagnostics().historicalWeightCap()).append("\n")
                .append("    evidence_ids:\n");
        retrieval.evidenceIds().forEach(id -> builder.append("      - ").append(safeScalar(id)).append("\n"));
        builder.append("    flattened_context: |\n")
                .append(indentBlock(sanitizer.sanitize(retrieval.flattenedContext()), 6))
                .append("\n");
        return builder.toString();
    }

    private static String safeScalar(String value) {
        return value.replaceAll("[^a-zA-Z0-9_./:-]+", "-");
    }

    private static String indentBlock(String value) {
        return indentBlock(value, 4);
    }

    private static String indentBlock(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        return value.lines()
                .map(line -> prefix + line)
                .collect(Collectors.joining("\n"));
    }

    private static GitCommand.Result git(Path directory, String... arguments) {
        return GitCommand.run(directory, arguments);
    }
}
