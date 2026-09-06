package com.dreamthought.saaa.adapters.fixture;

import com.dreamthought.saaa.deterministic.MutationProposer;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.PreparedMutationProposalRequest;
import com.dreamthought.saaa.domain.ProposerEvidence;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads a canned mutation from a file so the pipe can be exercised with no model and no network.
 *
 * <p>The format is deliberately trivial — first line is the summary, the remainder is the proposed
 * new file content — because there is no TOON reader in Java yet. This is a recorded deviation from
 * the structured-data rule, scoped to this proposer, and it retires when the TOON envelope reader
 * lands.
 *
 * <p>It varies for a generation, and the variation is mechanical rather than clever: variant one is
 * the fixture exactly as written, and each later variant appends one more padding line. That is
 * enough to make the candidates differ in a way the scorer can actually see, because parsimony reads
 * the size of the realised diff, so a generation from this proposer produces a real spread rather
 * than a tie.
 *
 * <p>What that does not show is worth stating plainly. A fixture proposer produces exactly the
 * variety it was written to produce. It can demonstrate that ranking discriminates between
 * candidates that differ; it cannot show that a live model's candidates differ in ways the scorer
 * can see. Only the live proposer answers that, and it is deliberately not part of this slice.
 */
public final class FixtureMutationProposer implements MutationProposer {
    private final Path fixtureFile;
    private ProposerEvidence evidence;

    public FixtureMutationProposer(Path fixtureFile) {
        this.fixtureFile = Objects.requireNonNull(fixtureFile, "fixtureFile");
    }

    @Override
    public Mutation proposeFor(WorkflowGraph baseline) {
        Objects.requireNonNull(baseline, "baseline");
        if (!Files.isRegularFile(fixtureFile)) {
            throw new IllegalStateException("fixture mutation file not found: " + fixtureFile);
        }
        String content = read();
        int firstBreak = content.indexOf('\n');
        if (firstBreak < 0) {
            throw new IllegalStateException(
                    "fixture mutation must have a summary line and a body: " + fixtureFile);
        }
        String summary = content.substring(0, firstBreak).trim();
        String patch = content.substring(firstBreak + 1);
        if (summary.isBlank() || patch.isBlank()) {
            throw new IllegalStateException(
                    "fixture mutation must have a summary line and a body: " + fixtureFile);
        }
        evidence = ProposerEvidence.of("fixture", Map.of("fixture_path", fixtureFile.getFileName().toString()));
        return new Mutation("MUT-" + baseline.id() + "-fixture", summary, MutationScope.WORKFLOW_DEFINITION, patch);
    }

    /**
     * Variant one is byte-identical to a single-candidate proposal, so asking for one candidate
     * behaves exactly as it always did and the padding is confined to the candidates that only a
     * generation asks for.
     */
    @Override
    public Mutation proposeFor(PreparedMutationProposalRequest request, int variant) {
        Objects.requireNonNull(request, "request");
        if (variant < 1) {
            throw new IllegalArgumentException("variant is one-based, got " + variant);
        }
        Mutation first = proposeFor(request.baseline());
        if (variant == 1) {
            return first;
        }
        var patch = new StringBuilder(first.patch());
        if (!first.patch().endsWith("\n")) {
            patch.append('\n');
        }
        // One extra line per variant, appended rather than woven in, because a fixture body is
        // arbitrary file content and this proposer cannot know where a safe edit would be. Appending
        // keeps every line the behaviour cases grade untouched and changes only the size, which is
        // the axis parsimony reads.
        for (int line = 1; line < variant; line++) {
            patch.append("# fixture variant ").append(variant).append(" line ").append(line).append('\n');
        }
        return new Mutation(
                first.id() + "-v" + variant,
                first.summary() + " (variant " + variant + ")",
                first.scope(),
                patch.toString());
    }

    @Override
    public Optional<ProposerEvidence> proposerEvidence() {
        return Optional.ofNullable(evidence);
    }

    private String read() {
        try {
            return Files.readString(fixtureFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read fixture mutation: " + fixtureFile, exception);
        }
    }
}
