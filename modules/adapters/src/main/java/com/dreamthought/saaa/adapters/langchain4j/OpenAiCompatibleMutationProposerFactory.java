package com.dreamthought.saaa.adapters.langchain4j;

import com.dreamthought.saaa.deterministic.MutationProposer;
import java.util.Map;
import java.util.Objects;

public final class OpenAiCompatibleMutationProposerFactory {
    private final OpenAiCompatibleChatModelFactory chatModelFactory;

    public OpenAiCompatibleMutationProposerFactory() {
        this(new OpenAiCompatibleChatModelFactory());
    }

    OpenAiCompatibleMutationProposerFactory(OpenAiCompatibleChatModelFactory chatModelFactory) {
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory, "chatModelFactory");
    }

    public MutationProposer fromProcessEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public MutationProposer fromEnvironment(Map<String, String> environment) {
        return LangChain4jMutationProposalAdapter.from(chatModelFactory.fromEnvironment(environment));
    }
}
