package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.CandidateBranchRef;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class PromotionSinkTypeTest {
    @Test
    void promotionSinkExposesNoMergeOperationOrTargetBranchParameter() {
        var methodNames = Arrays.stream(CandidateDecisionSink.class.getDeclaredMethods())
                .map(method -> method.getName())
                .toList();

        assertThat(methodNames)
                .containsExactlyInAnyOrder("recordPromotedCandidateBranch", "recordDiscardedCandidateBranch")
                .noneMatch(name -> name.toLowerCase().contains("merge"));

        Arrays.stream(CandidateDecisionSink.class.getDeclaredMethods())
                .forEach(method -> assertThat(method.getParameterTypes())
                        .contains(CandidateBranchRef.class)
                        .doesNotContain(String.class));
    }
}
