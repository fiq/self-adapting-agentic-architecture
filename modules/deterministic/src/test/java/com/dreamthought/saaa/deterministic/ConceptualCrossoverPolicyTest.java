package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationOperatorType.TARGETED_BEHAVIOR_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.MutationBounds;
import com.dreamthought.saaa.domain.MutationTarget;
import com.dreamthought.saaa.domain.ParentTrait;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ConceptualCrossoverPolicyTest {
    private final ConceptualCrossoverPolicy crossoverPolicy = new ConceptualCrossoverPolicy();
    private final MutationContractCanonicalizer canonicalizer = new MutationContractCanonicalizer();

    @Test
    void createsChildContractWithClosedMutationOperatorFromEvidenceBackedTraits() {
        var child = crossoverPolicy.createChildContract(new ConceptualCrossoverRequest(
                "MUT-X1",
                TARGETED_BEHAVIOR_CHANGE,
                "combine tighter retrieval bounds with cheaper tool selection",
                new MutationTarget(
                        "method",
                        "src/main/java/example/cms/PublishWorkflow.java",
                        "chooseEditorialAction"
                ),
                List.of("method_body"),
                new MutationBounds(2, 80, false, false, false),
                List.of(
                        new ParentTrait("cand-fast-tools", "cheap tool ordering", "FIT-101"),
                        new ParentTrait("cand-safe-retrieval", "bounded retrieval guard", "FIT-108")
                )
        ));

        assertThat(child.operator()).isEqualTo(TARGETED_BEHAVIOR_CHANGE);
        assertThat(child.parentTraits()).extracting(ParentTrait::parentCandidateId)
                .containsExactly("cand-fast-tools", "cand-safe-retrieval");
        assertThat(canonicalizer.canonicalize(child))
                .contains("(operator targeted-behavior-change)")
                .contains(
                        "(parents"
                                + " (parent (candidate cand-fast-tools) (trait \"cheap tool ordering\") (evidence FIT-101))"
                                + " (parent (candidate cand-safe-retrieval) (trait \"bounded retrieval guard\")"
                                + " (evidence FIT-108)))"
                );
    }

    // That `conceptual-crossover` is not an operator enum value is asserted once, in
    // MutationOperatorTypeTest. This test owns the recombination behaviour instead.
    @Test
    void refusesToSpliceMultipleLociAsAnUnboundedDiffMerge() {
        assertThatThrownBy(() -> crossoverPolicy.createChildContract(new ConceptualCrossoverRequest(
                "MUT-X2",
                TARGETED_BEHAVIOR_CHANGE,
                "splice two candidate diffs",
                new MutationTarget("method", "src/main/java/example/cms/PublishWorkflow.java", "chooseEditorialAction"),
                List.of("method_body", "tool_policy"),
                new MutationBounds(2, 80, false, false, false),
                List.of(
                        new ParentTrait("cand-a", "diff hunk A", "FIT-201"),
                        new ParentTrait("cand-b", "diff hunk B", "FIT-202")
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("conceptual crossover child must target one primary locus");
    }

    @Test
    void refusesTraitsThatAllCameFromOneParent() {
        assertThatThrownBy(() -> crossoverPolicy.createChildContract(request(
                new MutationBounds(2, 80, false, false, false),
                List.of(
                        new ParentTrait("cand-a", "cheap tool ordering", "FIT-301"),
                        new ParentTrait("cand-a", "bounded retrieval guard", "FIT-302")
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("conceptual crossover requires traits from at least 2 evaluated parents");
    }

    @Test
    void refusesToEmitAChildContractTheDeterministicGateWouldReject() {
        assertThatThrownBy(() -> crossoverPolicy.createChildContract(request(
                new MutationBounds(9, 900, true, false, false),
                List.of(
                        new ParentTrait("cand-a", "cheap tool ordering", "FIT-301"),
                        new ParentTrait("cand-b", "bounded retrieval guard", "FIT-302")
                )
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("conceptual crossover child contract is not valid:")
                .hasMessageContaining("maxFilesChanged must be at most 2 for targeted-behavior-change");
    }

    private static ConceptualCrossoverRequest request(MutationBounds bounds, List<ParentTrait> parentTraits) {
        return new ConceptualCrossoverRequest(
                "MUT-X3",
                TARGETED_BEHAVIOR_CHANGE,
                "combine tighter retrieval bounds with cheaper tool selection",
                new MutationTarget("method", "src/main/java/example/cms/PublishWorkflow.java", "chooseEditorialAction"),
                List.of("method_body"),
                bounds,
                parentTraits
        );
    }
}
