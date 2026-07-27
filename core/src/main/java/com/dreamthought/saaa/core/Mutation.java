package com.dreamthought.saaa.core;

import java.util.Objects;

public record Mutation(String id, String summary, MutationScope scope, String patch) {
    public Mutation {
        id = Require.nonBlank(id, "id");
        summary = Require.nonBlank(summary, "summary");
        scope = Objects.requireNonNull(scope, "scope");
        patch = Require.nonBlank(patch, "patch");
    }
}
