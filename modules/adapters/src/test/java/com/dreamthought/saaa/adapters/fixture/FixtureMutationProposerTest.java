package com.dreamthought.saaa.adapters.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.PreparedMutationProposalRequest;
import com.dreamthought.saaa.domain.RetrievalBundle;
import com.dreamthought.saaa.domain.RetrievalDiagnostics;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FixtureMutationProposerTest {
    private final WorkflowGraph baseline = new WorkflowGraph("toy", "v1", "old content");

    @Test
    void readsSummaryFromTheFirstLineAndPatchFromTheRest(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nline one\nline two\n");

        var mutation = new FixtureMutationProposer(fixture).proposeFor(baseline);

        assertThat(mutation.summary()).isEqualTo("tighten the publish guard");
        assertThat(mutation.patch()).isEqualTo("line one\nline two\n");
        assertThat(mutation.scope()).isEqualTo(MutationScope.WORKFLOW_DEFINITION);
        assertThat(mutation.id()).isNotBlank();
    }

    @Test
    void producesTheSameMutationEveryTimeForTheSameFixture(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nnew content\n");
        var proposer = new FixtureMutationProposer(fixture);

        assertThat(proposer.proposeFor(baseline)).isEqualTo(proposer.proposeFor(baseline));
    }

    @Test
    void recordsOnlyTheFixtureFileNameAsEvidence(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nnew content\n");
        var proposer = new FixtureMutationProposer(fixture);

        proposer.proposeFor(baseline);

        assertThat(proposer.proposerEvidence().orElseThrow().attributes())
                .containsEntry("fixture_path", "fixture-mutation.txt");
    }

    @Test
    void failsClearlyWhenTheFixtureIsMissing(@TempDir Path dir) {
        assertThatThrownBy(() -> new FixtureMutationProposer(dir.resolve("absent.txt")).proposeFor(baseline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absent.txt");
    }
    /**
     * S8. Asked to fill a generation, the fixture proposer must yield distinct mutations. Before it
     * varied it returned {@code MUT-<baseline>-fixture} on every call, so a generation of N was one
     * candidate evaluated N times: distinct candidate ids, distinct worktrees, an identical ranking
     * tie and a spread of zero that said nothing about the candidates.
     *
     * <p>The patches are asserted distinct as well as the ids, because ids that differ over
     * identical content would still rank as a tie. The lengths are asserted to grow because
     * parsimony reads the size of the realised diff, which is the axis that makes the difference
     * visible to the scorer rather than only to a reader.
     */
    @Test
    void aGenerationRequestYieldsDistinctMutations(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nnew content\n");
        var proposer = new FixtureMutationProposer(fixture);
        var request = generationRequest();

        var variants = List.of(
                proposer.proposeFor(request, 1),
                proposer.proposeFor(request, 2),
                proposer.proposeFor(request, 3));

        assertThat(variants).extracting(Mutation::id).doesNotHaveDuplicates();
        assertThat(variants).extracting(Mutation::patch).doesNotHaveDuplicates();
        assertThat(variants.get(1).patch().length())
                .as("a later variant is larger, which is what parsimony can see")
                .isGreaterThan(variants.get(0).patch().length());
        assertThat(variants.get(2).patch().length())
                .isGreaterThan(variants.get(1).patch().length());
    }

    /**
     * The single-candidate path must not change. Variant one is the fixture exactly as written, so
     * every run that asks for one candidate proposes the mutation it always proposed and the padding
     * exists only for candidates a generation asked for.
     */
    @Test
    void theFirstVariantIsTheFixtureExactlyAsWritten(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nnew content\n");
        var proposer = new FixtureMutationProposer(fixture);
        var request = generationRequest();

        assertThat(proposer.proposeFor(request, 1)).isEqualTo(proposer.proposeFor(baseline));
    }

    @Test
    void refusesAVariantBelowOne(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nnew content\n");
        var proposer = new FixtureMutationProposer(fixture);
        var request = generationRequest();

        assertThatThrownBy(() -> proposer.proposeFor(request, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one-based");
    }
    /** Any prepared request; this proposer reads only the baseline from it. */
    private PreparedMutationProposalRequest generationRequest() {
        return new PreparedMutationProposalRequest(
                baseline,
                new RetrievalQuery(RetrievalMode.NONE, "vary", baseline, "v1", List.of("workflow.txt"),
                        Optional.empty()),
                new RetrievalBundle(RetrievalMode.NONE, "test-retrieval", "v1", "none", "none", "none",
                        "none", "none", List.of(), RetrievalDiagnostics.empty(), ""));
    }
}
