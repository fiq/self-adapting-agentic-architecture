package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.SourceStructure;
import com.dreamthought.saaa.domain.SourceSymbol;
import com.dreamthought.saaa.domain.StructureCompleteness;
import com.dreamthought.saaa.domain.StructureLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * CHG-025. Proves the conformance suite is both satisfiable and strict.
 *
 * <p>Both halves are necessary and neither is sufficient. A suite nobody can satisfy would not be
 * discovered until the first real frontend, by which point the contract looks like the frontend's
 * bug. A suite anybody can satisfy proves nothing at all, which is the PAT-004 trap: an assertion
 * that cannot fail is not evidence, however carefully it reads.
 *
 * <p>The reference frontend below is a toy, deliberately. Using a real parser here would test that
 * parser; the question is whether the <em>contract</em> is well formed.
 */
final class SourceStructureConformanceTest {
    private static final String TOY = "toy";

    @Test
    @DisplayName("a frontend that honours the contract passes, so the suite is satisfiable")
    void aCorrectFrontendPassesTheSuite() {
        assertThatCode(() -> SourceStructureConformance.verify(new ToyFrontend(), fixtures()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a frontend that calls every source identical fails: it cannot see a real change")
    void formattingOnlyDifferencesAreIdenticalAndRealOnesAreNot() {
        var constantDigest = new ToyFrontend(structure ->
                withDigest(structure, "always-the-same"));

        assertThatThrownBy(() -> SourceStructureConformance.verify(constantDigest, fixtures()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("changed statement");
    }

    @Test
    @DisplayName("a frontend whose digest is unstable fails: nothing downstream could rank on it")
    void anUnstableDigestFailsEvenThoughEverySourceLooksDifferent() {
        var counter = new int[] {0};
        var unstable = new ToyFrontend(structure ->
                withDigest(structure, "call-" + counter[0]++));

        assertThatThrownBy(() -> SourceStructureConformance.verify(unstable, fixtures()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("a frontend declaring SYMBOL but locating nothing fails")
    void aFrontendMustFillTheLayersItDeclares() {
        var noSymbols = new ToyFrontend(structure -> new SourceStructure(
                structure.languageId(), structure.frontendId(), structure.filledLayers(),
                structure.completeness(), structure.normalizedDigest(), List.of()));

        assertThatThrownBy(() -> SourceStructureConformance.verify(noSymbols, fixtures()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("declarations must be located");
    }

    @Test
    @DisplayName("a frontend that cannot tell inside a declaration from outside it fails")
    void aDeclaredSymbolIsLocatedAndEditsOutsideItAreDistinguishable() {
        var swallowsEverything = new ToyFrontend(structure -> structure.symbols().isEmpty()
                ? structure
                : new SourceStructure(
                        structure.languageId(), structure.frontendId(), structure.filledLayers(),
                        structure.completeness(), structure.normalizedDigest(),
                        List.of(new SourceSymbol(
                                structure.symbols().get(0).identifier(), 1, 9_999))));

        assertThatThrownBy(() -> SourceStructureConformance.verify(swallowsEverything, fixtures()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("left its locus");
    }

    @Test
    @DisplayName("a frontend reporting partial recovery over an empty tree fails")
    void unusableStructureIsReportedUnparseableRatherThanPartial() {
        var overclaims = new ToyFrontend(UnaryOperator.identity()) {
            @Override
            public SourceStructure inspect(String languageId, String source) {
                if (!readable(source)) {
                    // The JavaParser shape: a result exists, but there is nothing in it.
                    return SourceStructure.unreadable(
                            TOY, frontendId(), StructureCompleteness.RECOVERED_WITH_ERRORS);
                }
                return super.inspect(languageId, source);
            }
        };

        assertThatThrownBy(() -> SourceStructureConformance.verify(overclaims, fixtures()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UNPARSEABLE");
    }

    @Test
    @DisplayName("a capability needing an unfilled layer is unsupported, not silently degraded")
    void aCapabilityNeedingAnUnfilledLayerIsUnsupported() {
        var syntaxOnly = new ToyFrontend() {
            @Override
            public Set<StructureLayer> declaredLayers() {
                return Set.of(StructureLayer.SYNTAX);
            }
        };

        // A syntax-only frontend is a first-class outcome, so it must pass the suite - and the
        // symbol assertions must simply not run against it.
        assertThatCode(() -> SourceStructureConformance.verify(syntaxOnly, fixtures()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a fixture set that reuses one source for two roles is rejected")
    void fixturesThatCannotDistinguishTheCasesAreRefused() {
        assertThatThrownBy(() -> new SourceStructureFixtures(
                TOY, "sym a {\n x\n}\n", "sym a {\n x\n}\n", "sym a {\n y\n}\n", "a", 2, 1, "sym"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ textually");
    }

    private static SourceStructureFixtures fixtures() {
        return new SourceStructureFixtures(
                TOY,
                "# header\nsym greet {\n  say hello\n}\n",
                "# a different comment\nsym greet {\n\n     say    hello\n}\n",
                "# header\nsym greet {\n  say goodbye\n}\n",
                "greet",
                3,
                1,
                "sym greet {\n  say hello\n");
    }

    private static SourceStructure withDigest(SourceStructure structure, String digest) {
        return new SourceStructure(
                structure.languageId(), structure.frontendId(), structure.filledLayers(),
                structure.completeness(), Optional.of(digest), structure.symbols());
    }

    /**
     * A toy frontend for a toy language: {@code sym <name> { ... }}, with {@code #} comments.
     * Enough to exercise every assertion without adopting a parser.
     */
    private static class ToyFrontend implements SourceStructureInspector {
        private final UnaryOperator<SourceStructure> distortion;

        ToyFrontend() {
            this(UnaryOperator.identity());
        }

        ToyFrontend(UnaryOperator<SourceStructure> distortion) {
            this.distortion = distortion;
        }

        @Override public String frontendId() { return "toy-reference"; }

        @Override public String languageId() { return TOY; }

        @Override public Set<StructureLayer> declaredLayers() {
            return Set.of(StructureLayer.SYNTAX, StructureLayer.SYMBOL);
        }

        @Override
        public SourceStructure inspect(String languageId, String source) {
            if (!readable(source)) {
                return SourceStructure.unreadable(TOY, frontendId(), StructureCompleteness.UNPARSEABLE);
            }
            // A frontend produces only what it declares. Building symbols it did not declare is
            // rejected by the model itself, which is the check working rather than an obstacle.
            var layers = declaredLayers();
            return distortion.apply(new SourceStructure(
                    TOY, frontendId(), layers, StructureCompleteness.COMPLETE,
                    layers.contains(StructureLayer.SYNTAX)
                            ? Optional.of(digest(source)) : Optional.empty(),
                    layers.contains(StructureLayer.SYMBOL) ? symbols(source) : List.of()));
        }

        static boolean readable(String source) {
            long open = source.chars().filter(c -> c == '{').count();
            long close = source.chars().filter(c -> c == '}').count();
            return open > 0 && open == close;
        }

        /** Erases comments and whitespace; preserves identifiers, literals and statement order. */
        private static String digest(String source) {
            return source.lines()
                    .map(line -> line.replaceAll("#.*$", ""))
                    .map(line -> line.replaceAll("\\s+", " ").trim())
                    .filter(line -> !line.isEmpty())
                    .reduce("", (a, b) -> a + "|" + b);
        }

        private static List<SourceSymbol> symbols(String source) {
            var found = new ArrayList<SourceSymbol>();
            var lines = source.lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                var matcher = java.util.regex.Pattern
                        .compile("^\\s*sym\\s+(\\w+)\\s*\\{").matcher(lines.get(i));
                if (matcher.find()) {
                    int close = i;
                    while (close < lines.size() && !lines.get(close).contains("}")) {
                        close++;
                    }
                    found.add(new SourceSymbol(matcher.group(1), i + 1,
                            Math.min(close + 1, lines.size())));
                }
            }
            return found;
        }
    }
}
