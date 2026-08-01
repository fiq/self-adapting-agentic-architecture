package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CandidateBranchRefTest {
    @Test
    void derivesFullCandidateRefFromCandidateBranchName() {
        var candidate = new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", Path.of("/tmp/wt"), "abc1234");

        assertThat(CandidateBranchRef.fromCandidate(candidate).value())
                .isEqualTo("refs/heads/candidate/toy-MUT-1");
    }

    @Test
    void rejectsMainAsAPromotionTarget() {
        assertThatThrownBy(() -> new CandidateBranchRef("refs/heads/main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidate branch ref must start with refs/heads/candidate/");
    }

    @Test
    void rejectsTraversalShapedCandidateRefs() {
        assertThatThrownBy(() -> new CandidateBranchRef("refs/heads/candidate/../../heads/main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single safe branch segment");
    }
}
