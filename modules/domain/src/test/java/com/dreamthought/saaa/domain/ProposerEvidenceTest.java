package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProposerEvidenceTest {
    @Test
    void rejectsProposerIdsThatCannotBeWrittenAsPlainToonScalars() {
        assertThatThrownBy(() -> ProposerEvidence.of("OpenAI Compatible", Map.of("raw_response", "body")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("proposerId must be a TOON-safe identifier: OpenAI Compatible");
    }

    @Test
    void rejectsAttributeKeysThatCannotBeWrittenAsToonFieldNames() {
        assertThatThrownBy(() -> ProposerEvidence.of("openai-compatible", Map.of("raw-response", "body")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("attribute key must be a TOON-safe snake_case name: raw-response");
    }
}
