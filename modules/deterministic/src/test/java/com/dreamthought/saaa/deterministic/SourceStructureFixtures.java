package com.dreamthought.saaa.deterministic;

import java.util.Objects;

/**
 * The sources a frontend supplies so the shared conformance assertions can run against its language.
 *
 * <p>The assertions are written once and shared; only these differ per language. That split is what
 * makes the suite a contract rather than a set of Java tests: a contributor writing a frontend for a
 * language nobody here reads supplies six strings and inherits every assertion.
 *
 * @param languageId          the language these are written in
 * @param original            any readable source declaring at least one symbol
 * @param formattingOnlyEdit  the same source with whitespace, line breaks or comments changed and
 *                            nothing else — the syntax layer must call this identical
 * @param statementEdit       the same source with a statement genuinely changed — must not be
 *                            identical, or a frontend returning a constant would pass
 * @param declaredSymbolName  a symbol declared in {@code original}, as its frontend identifies it
 * @param lineInsideSymbol    a one-based line falling inside that declaration
 * @param lineOutsideSymbol   a one-based line falling outside every declaration, such as an import
 * @param unreadable          source this frontend cannot read into usable structure
 */
public record SourceStructureFixtures(
        String languageId,
        String original,
        String formattingOnlyEdit,
        String statementEdit,
        String declaredSymbolName,
        int lineInsideSymbol,
        int lineOutsideSymbol,
        String unreadable
) {
    public SourceStructureFixtures {
        languageId = Require.nonBlankFixture(languageId, "languageId");
        original = Require.nonBlankFixture(original, "original");
        formattingOnlyEdit = Require.nonBlankFixture(formattingOnlyEdit, "formattingOnlyEdit");
        statementEdit = Require.nonBlankFixture(statementEdit, "statementEdit");
        declaredSymbolName = Require.nonBlankFixture(declaredSymbolName, "declaredSymbolName");
        unreadable = Require.nonBlankFixture(unreadable, "unreadable");
        // A fixture set that reuses one source for two roles would make the suite pass without
        // proving anything, which is the failure mode PAT-004 describes for a fixture that already
        // satisfies its assertion.
        if (original.equals(formattingOnlyEdit)) {
            throw new IllegalArgumentException("formattingOnlyEdit must differ textually from original");
        }
        if (original.equals(statementEdit)) {
            throw new IllegalArgumentException("statementEdit must differ from original");
        }
        if (formattingOnlyEdit.equals(statementEdit)) {
            throw new IllegalArgumentException(
                    "formattingOnlyEdit and statementEdit must be different edits, or the suite "
                            + "cannot tell a formatting change from a real one");
        }
        if (lineInsideSymbol < 1 || lineOutsideSymbol < 1) {
            throw new IllegalArgumentException("fixture lines are one-based");
        }
        if (lineInsideSymbol == lineOutsideSymbol) {
            throw new IllegalArgumentException("inside and outside lines must differ");
        }
    }

    private static final class Require {
        static String nonBlankFixture(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("fixture " + name + " must not be blank");
            }
            return value;
        }
    }
}
