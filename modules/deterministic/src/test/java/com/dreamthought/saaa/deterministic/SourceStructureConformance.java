package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.SourceStructure;
import com.dreamthought.saaa.domain.StructureCompleteness;
import com.dreamthought.saaa.domain.StructureLayer;
import java.util.Set;

/**
 * The shared conformance assertions every frontend must pass, for the layers it declares.
 *
 * <p>This is the contract. A frontend is supported exactly when it passes these with fixtures in its
 * own language — not when it exists, and not when someone has read its adapter. Reviewing an adapter
 * for a language you do not read is not a control; running the suite every other frontend passes is.
 *
 * <p>Written before any frontend, deliberately. A suite written alongside its first implementation
 * describes what that implementation happens to do, and the second language then discovers the real
 * contract the hard way.
 *
 * <p>Not a test class itself: a frontend's own test invokes {@link #verify} so the failure is
 * attributed to that frontend.
 */
final class SourceStructureConformance {
    private SourceStructureConformance() {
    }

    static void verify(SourceStructureInspector frontend, SourceStructureFixtures fixtures) {
        assertThat(frontend.languageId())
                .as("a frontend must be tested with fixtures in the language it reads")
                .isEqualTo(fixtures.languageId());

        var declared = frontend.declaredLayers();
        assertThat(declared)
                .as("a frontend filling no layers cannot be used by any capability")
                .isNotEmpty();

        var original = read(frontend, fixtures, fixtures.original());
        assertThat(original.filledLayers())
                .as("readable source must fill exactly the layers the frontend declares")
                .isEqualTo(declared);
        assertThat(original.frontendId()).isEqualTo(frontend.frontendId());

        if (declared.contains(StructureLayer.SYNTAX)) {
            verifySyntaxLayer(frontend, fixtures, original);
        }
        if (declared.contains(StructureLayer.SYMBOL)) {
            verifySymbolLayer(frontend, fixtures, original);
        }
        verifyUnreadableSource(frontend, fixtures);
        verifyLayerDeclarationIsHonest(original, declared);
    }

    /**
     * Asserted in both directions on purpose. A frontend that called every source identical would
     * satisfy the formatting half alone, and one that called every source different would satisfy
     * the statement half alone. Only together do they pin a digest that tracks meaning.
     */
    private static void verifySyntaxLayer(
            SourceStructureInspector frontend, SourceStructureFixtures fixtures, SourceStructure original) {
        var formatting = read(frontend, fixtures, fixtures.formattingOnlyEdit());
        var statement = read(frontend, fixtures, fixtures.statementEdit());

        assertThat(formatting.normalizedDigest())
                .as("formatting and comments are erased, so this must equal the original")
                .isEqualTo(original.normalizedDigest());
        assertThat(statement.normalizedDigest())
                .as("a changed statement is a changed candidate, so this must not equal the original")
                .isNotEqualTo(original.normalizedDigest());

        assertThat(read(frontend, fixtures, fixtures.original()).normalizedDigest())
                .as("reading the same source twice must give the same answer, or nothing downstream is stable")
                .isEqualTo(original.normalizedDigest());
    }

    /** What the declared-locus gate consumes: where a declaration is, and what falls outside it. */
    private static void verifySymbolLayer(
            SourceStructureInspector frontend, SourceStructureFixtures fixtures, SourceStructure original) {
        assertThat(original.symbols())
                .as("the symbol layer was declared filled, so declarations must be located")
                .isNotEmpty()
                .extracting(com.dreamthought.saaa.domain.SourceSymbol::identifier)
                .contains(fixtures.declaredSymbolName());

        assertThat(original.symbolContaining(fixtures.lineInsideSymbol()))
                .as("a line inside a declaration must resolve to it")
                .isPresent();
        assertThat(original.symbolContaining(fixtures.lineOutsideSymbol()))
                .as("a line outside every declaration must resolve to none, or the gate cannot "
                        + "distinguish an edit that left its locus")
                .isEmpty();
    }

    /**
     * The distinction the JavaParser spike forced. A partial tree carrying no declarations is
     * absence, and reporting it as recovery puts a confident-looking artefact in front of a gate
     * with nothing behind it.
     */
    private static void verifyUnreadableSource(
            SourceStructureInspector frontend, SourceStructureFixtures fixtures) {
        var result = frontend.inspect(fixtures.languageId(), fixtures.unreadable());

        assertThat(result.completeness())
                .as("source that yields no usable structure is UNPARSEABLE, never partial recovery")
                .isEqualTo(StructureCompleteness.UNPARSEABLE);
        assertThat(result.filledLayers())
                .as("nothing was read, so nothing may be claimed")
                .isEmpty();
    }

    /** A capability needing an unfilled layer must be told, not quietly given less than it asked for. */
    private static void verifyLayerDeclarationIsHonest(
            SourceStructure original, Set<StructureLayer> declared) {
        assertThat(original.supports(declared))
                .as("a frontend must support every layer it declares")
                .isTrue();
        for (StructureLayer layer : StructureLayer.values()) {
            if (!declared.contains(layer)) {
                assertThat(original.supports(Set.of(layer)))
                        .as("an undeclared layer must not be reported as supported: %s", layer)
                        .isFalse();
            }
        }
    }

    private static SourceStructure read(
            SourceStructureInspector frontend, SourceStructureFixtures fixtures, String source) {
        var structure = frontend.inspect(fixtures.languageId(), source);
        assertThat(structure.completeness())
                .as("this fixture is meant to be readable")
                .isIn(StructureCompleteness.COMPLETE, StructureCompleteness.RECOVERED_WITH_ERRORS);
        return structure;
    }
}
