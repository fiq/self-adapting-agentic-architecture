package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProposerProfileRegistryTest {
    private final ProposerProfileRegistry registry = new ProposerProfileRegistry();

    @Test
    void resolvesKnownProfileAndListsKnownNamesOnFailure() {
        assertThat(registry.knownNames()).containsExactly("fixture");
        assertThat(registry.resolve("fixture", Path.of("some/folder"))).isNotNull();

        assertThatThrownBy(() -> registry.resolve("gpt-cloud", Path.of("some/folder")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown proposer profile: gpt-cloud; known profiles: fixture");
    }
}
