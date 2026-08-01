package com.dreamthought.saaa.adapters.mcp;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class EvolveMcpResponseScrubber {
    private static final Pattern BEARER_HEADER =
            Pattern.compile("Authorization:\\s*Bearer\\s+[^\\s\\r\\n,}]+", Pattern.CASE_INSENSITIVE);

    private final Supplier<Optional<String>> apiKeySupplier;

    public EvolveMcpResponseScrubber() {
        this(() -> Optional.ofNullable(System.getenv("SAAA_MODEL_API_KEY")).filter(value -> !value.isBlank()));
    }

    public EvolveMcpResponseScrubber(Supplier<Optional<String>> apiKeySupplier) {
        this.apiKeySupplier = Objects.requireNonNull(apiKeySupplier, "apiKeySupplier");
    }

    public String scrub(String text) {
        Objects.requireNonNull(text, "text");
        String scrubbed = BEARER_HEADER.matcher(text).replaceAll("Authorization: Bearer <redacted>");
        Optional<String> apiKey = apiKeySupplier.get();
        if (apiKey.isPresent()) {
            scrubbed = scrubbed.replace(apiKey.get(), "<redacted>");
        }
        return scrubbed;
    }
}
