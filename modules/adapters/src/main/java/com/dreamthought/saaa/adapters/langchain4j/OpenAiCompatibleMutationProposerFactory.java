package com.dreamthought.saaa.adapters.langchain4j;

import com.dreamthought.saaa.deterministic.MutationProposer;
import java.util.Objects;

public final class OpenAiCompatibleMutationProposerFactory {
    private final OpenAiCompatibleChatModelFactory chatModelFactory;

    public OpenAiCompatibleMutationProposerFactory() {
        this(new OpenAiCompatibleChatModelFactory());
    }

    OpenAiCompatibleMutationProposerFactory(OpenAiCompatibleChatModelFactory chatModelFactory) {
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory, "chatModelFactory");
    }

    public MutationProposer fromApplicationConfig() {
        return LangChain4jMutationProposalAdapter.from(chatModelFactory.fromApplicationConfig());
    }

    public MutationProposer fromConfig(ModelEndpointConfig config) {
        return LangChain4jMutationProposalAdapter.from(chatModelFactory.fromConfig(config));
    }
}
