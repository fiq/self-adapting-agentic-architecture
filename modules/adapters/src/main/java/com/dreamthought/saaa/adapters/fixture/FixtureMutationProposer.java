package com.dreamthought.saaa.adapters.fixture;

import com.dreamthought.saaa.deterministic.MutationProposer;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
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
 * <p>Being deterministic, this proposer cannot supply the variance a population needs. That is a
 * dependency of the population slice, not a gap here.
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
        evidence = ProposerEvidence.of("fixture", Map.of("fixture_path", fixtureFile.toString()));
        return new Mutation("MUT-" + baseline.id() + "-fixture", summary, MutationScope.WORKFLOW_DEFINITION, patch);
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
