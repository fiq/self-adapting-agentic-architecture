package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CHG-025. The model's construction rules, asserted directly.
 *
 * <p>The conformance suite exercises these through a frontend, which is the right level for a
 * contributor and the wrong one for the rules themselves: a rule reached only through a toy
 * frontend is only tested for the shapes that frontend happens to produce.
 */
final class SourceStructureTest {

    @Test
    @DisplayName("a result claiming the source was read must have filled a layer")
    void readingSomethingAndFillingNothingIsRejected() {
        assertThatThrownBy(() -> new SourceStructure(
                "java", "toy", Set.of(), StructureCompleteness.RECOVERED_WITH_ERRORS,
                Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recovering nothing is UNPARSEABLE");
    }

    @Test
    @DisplayName("the unreadable factory refuses a completeness that claims the source was read")
    void theUnreadableFactoryRefusesAReadableCompleteness() {
        assertThatThrownBy(() -> SourceStructure.unreadable(
                "java", "toy", StructureCompleteness.COMPLETE))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> SourceStructure.unreadable(
                "java", "toy", StructureCompleteness.UNPARSEABLE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FLOW cannot be declared filled while no field could corroborate it")
    void declaringFlowFilledIsRejected() {
        assertThatThrownBy(() -> new SourceStructure(
                "java", "toy", Set.of(StructureLayer.SYNTAX, StructureLayer.FLOW),
                StructureCompleteness.COMPLETE, Optional.of("digest"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no flow evidence");
    }

    @Test
    @DisplayName("a nested declaration wins over the one enclosing it, whatever order they arrive in")
    void theInnermostDeclarationContainingALineIsTheOneReturned() {
        var enclosing = new SourceSymbol("Outer", 1, 20);
        var nested = new SourceSymbol("inner", 5, 9);

        assertThat(structureWith(List.of(enclosing, nested)).symbolContaining(7))
                .contains(nested);
        assertThat(structureWith(List.of(nested, enclosing)).symbolContaining(7))
                .as("input order must not decide the answer, or two frontends for one language "
                        + "could disagree about the same edit")
                .contains(nested);
        assertThat(structureWith(List.of(enclosing, nested)).symbolContaining(15))
                .as("a line the nested declaration does not cover still resolves to the enclosing one")
                .contains(enclosing);
        assertThat(structureWith(List.of(enclosing, nested)).symbolContaining(30)).isEmpty();
    }

    private static SourceStructure structureWith(List<SourceSymbol> symbols) {
        return new SourceStructure(
                "java", "toy", Set.of(StructureLayer.SYMBOL), StructureCompleteness.COMPLETE,
                Optional.empty(), symbols);
    }
}
