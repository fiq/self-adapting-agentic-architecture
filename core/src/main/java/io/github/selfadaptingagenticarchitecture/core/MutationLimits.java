package io.github.selfadaptingagenticarchitecture.core;

public final class MutationLimits {
    public static final int MAX_ID_LENGTH = 128;
    public static final int MAX_SUMMARY_LENGTH = 500;
    public static final int MAX_SCOPE_LENGTH = 64;
    public static final int MAX_PATCH_LENGTH = 10_000;

    private MutationLimits() {
    }
}
