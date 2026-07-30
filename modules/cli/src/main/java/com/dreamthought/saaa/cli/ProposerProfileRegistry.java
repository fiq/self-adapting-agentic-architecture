package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.fixture.FixtureMutationProposer;
import com.dreamthought.saaa.deterministic.MutationProposer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves a profile name to a proposer. Choosing a proposer is composition, not policy, so this
 * lives in the CLI rather than the deterministic layer.
 *
 * <p>Adding a live provider means adding an entry here plus provider-neutral {@code ChatModel}
 * construction. The proposal adapter already accepts any {@code ChatModel}, so no adapter changes
 * are needed for any provider.
 */
public final class ProposerProfileRegistry {
    private final Map<String, Function<Path, MutationProposer>> factories = new LinkedHashMap<>();

    public ProposerProfileRegistry() {
        factories.put("fixture", folder ->
                new FixtureMutationProposer(folder.resolve(".saaa/fixture-mutation.txt")));
    }

    public List<String> knownNames() {
        return List.copyOf(factories.keySet());
    }

    public MutationProposer resolve(String profileName, Path targetFolder) {
        Objects.requireNonNull(profileName, "profileName");
        Objects.requireNonNull(targetFolder, "targetFolder");
        Function<Path, MutationProposer> factory = factories.get(profileName);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "unknown proposer profile: " + profileName
                            + "; known profiles: " + String.join(", ", knownNames()));
        }
        return factory.apply(targetFolder);
    }
}
