package com.dreamthought.saaa.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a frontend read from one source, in the one model every capability consumes.
 *
 * <p>No capability may ask which frontend produced this. That is the point of there being one type:
 * the variation between a grammar-only frontend and a language tool shows up in {@link #filledLayers()},
 * which is data, rather than in which type arrived, which would be a branch in every consumer.
 *
 * @param languageId       the language this was read as, so a mixed-language target stays legible
 * @param frontendId       which frontend read it, for provenance only — never for behaviour
 * @param filledLayers     the layers actually populated; a capability needing an unfilled layer is
 *                         unsupported for this source rather than silently degraded
 * @param completeness     how much of the source was readable
 * @param normalizedDigest the syntax layer reduced to a comparison key, absent unless SYNTAX is
 *                         filled. Formatting and comments are erased; identifiers, literals and
 *                         statement order are not. This is what duplicate detection compares.
 * @param symbols          declarations located, empty unless SYMBOL is filled
 */
public record SourceStructure(
        String languageId,
        String frontendId,
        Set<StructureLayer> filledLayers,
        StructureCompleteness completeness,
        Optional<String> normalizedDigest,
        List<SourceSymbol> symbols
) {
    public SourceStructure {
        languageId = Require.nonBlank(languageId, "languageId");
        frontendId = Require.nonBlank(frontendId, "frontendId");
        filledLayers = Set.copyOf(Objects.requireNonNull(filledLayers, "filledLayers"));
        completeness = Objects.requireNonNull(completeness, "completeness");
        normalizedDigest = Objects.requireNonNull(normalizedDigest, "normalizedDigest");
        symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));

        // A declared layer with nothing in it is the failure this model exists to prevent: a
        // capability would read the declaration, trust it, and find nothing. Absence must be
        // declared as absence.
        if (filledLayers.contains(StructureLayer.SYNTAX) && normalizedDigest.isEmpty()) {
            throw new IllegalArgumentException("SYNTAX is declared filled but no digest was produced");
        }
        if (!filledLayers.contains(StructureLayer.SYNTAX) && normalizedDigest.isPresent()) {
            throw new IllegalArgumentException("a digest was produced but SYNTAX is not declared filled");
        }
        if (!filledLayers.contains(StructureLayer.SYMBOL) && !symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols were produced but SYMBOL is not declared filled");
        }
        // Nothing was read, so nothing may be claimed. Without this a frontend could report
        // UNPARSEABLE while handing over structure a capability would then use.
        boolean readable = completeness == StructureCompleteness.COMPLETE
                || completeness == StructureCompleteness.RECOVERED_WITH_ERRORS;
        if (!readable && !filledLayers.isEmpty()) {
            throw new IllegalArgumentException(
                    completeness + " cannot fill any layer, but declared " + filledLayers);
        }
    }

    /** Nothing was readable. The layer set is necessarily empty. */
    public static SourceStructure unreadable(
            String languageId, String frontendId, StructureCompleteness completeness) {
        return new SourceStructure(
                languageId, frontendId, Set.of(), completeness, Optional.empty(), List.of());
    }

    /** Whether a capability requiring these layers can read this source at all. */
    public boolean supports(Set<StructureLayer> required) {
        return filledLayers.containsAll(required);
    }

    /** The declaration containing a changed line, if the symbol layer was filled and one matches. */
    public Optional<SourceSymbol> symbolContaining(int line) {
        return symbols.stream().filter(symbol -> symbol.contains(line)).findFirst();
    }
}
