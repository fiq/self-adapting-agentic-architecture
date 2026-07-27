package com.dreamthought.saaa.core;

public record MutationTarget(String kind, String file, String symbol) {
    public MutationTarget {
        kind = Require.nonBlank(kind, "kind");
        file = Require.nonBlank(file, "file");
        symbol = Require.nonBlank(symbol, "symbol");
    }
}
