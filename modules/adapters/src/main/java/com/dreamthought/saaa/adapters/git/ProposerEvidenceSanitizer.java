package com.dreamthought.saaa.adapters.git;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class ProposerEvidenceSanitizer {
    public static final int VALUE_LIMIT = 8_000;

    private static final Pattern BEARER_HEADER =
            Pattern.compile("Authorization:\\s*Bearer\\s+[^\\s\\r\\n,}]+", Pattern.CASE_INSENSITIVE);

    private final Supplier<Optional<String>> apiKeySupplier;

    public ProposerEvidenceSanitizer() {
        this(() -> Optional.ofNullable(System.getenv("SAAA_MODEL_API_KEY")).filter(value -> !value.isBlank()));
    }

    public ProposerEvidenceSanitizer(Supplier<Optional<String>> apiKeySupplier) {
        this.apiKeySupplier = Objects.requireNonNull(apiKeySupplier, "apiKeySupplier");
    }

    public String sanitize(String value) {
        Objects.requireNonNull(value, "value");
        String scrubbed = BEARER_HEADER.matcher(value).replaceAll("Authorization: Bearer <redacted>");
        Optional<String> apiKey = apiKeySupplier.get();
        if (apiKey.isPresent()) {
            scrubbed = scrubbed.replace(apiKey.get(), "<redacted>");
        }
        return scrubbed.length() <= VALUE_LIMIT ? scrubbed : scrubbed.substring(0, VALUE_LIMIT);
    }
}
