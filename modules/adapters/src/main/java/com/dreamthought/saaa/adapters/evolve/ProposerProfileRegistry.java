package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.adapters.fixture.FixtureMutationProposer;
import com.dreamthought.saaa.adapters.langchain4j.OpenAiCompatibleMutationProposerFactory;
import com.dreamthought.saaa.deterministic.MutationProposer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves a profile name to a proposer. Selecting a proposer is adapter composition, not
 * deterministic policy, and it is shared by CLI and MCP entrypoints.
 */
public final class ProposerProfileRegistry {
    private final Map<String, Function<Path, MutationProposer>> factories = new LinkedHashMap<>();

    public ProposerProfileRegistry() {
        this(folder -> new OpenAiCompatibleMutationProposerFactory().fromApplicationConfig());
    }

    public ProposerProfileRegistry(Function<Path, MutationProposer> openAiCompatibleFactory) {
        Objects.requireNonNull(openAiCompatibleFactory, "openAiCompatibleFactory");
        factories.put("fixture", folder ->
                new FixtureMutationProposer(folder.resolve(".saaa/fixture-mutation.txt")));
        factories.put("openai-compatible", openAiCompatibleFactory);
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
