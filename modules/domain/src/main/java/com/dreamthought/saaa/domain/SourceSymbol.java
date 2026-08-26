package com.dreamthought.saaa.domain;

import java.util.Objects;

/**
 * A declaration a frontend located, and the span of source it occupies.
 *
 * <p>The identifier is derived by the frontend and must be stable across edits that do not change
 * what the symbol is. ADR-0005 records that stable symbol identity is unsolved design work rather
 * than something a parser hands over: a tree gives nodes, not identities that survive an edit.
 * Qualified name plus arity is the intended derivation, and a rename is deliberately a different
 * symbol — under the declared-locus gate, renaming is leaving the locus.
 *
 * <p>Line bounds are inclusive and one-based, matching how a diff reports a changed line, because
 * the gate's whole job is deciding whether a changed line fell inside a declared symbol.
 */
public record SourceSymbol(String identifier, int firstLine, int lastLine) {
    public SourceSymbol {
        identifier = Require.nonBlank(identifier, "identifier");
        if (firstLine < 1) {
            throw new IllegalArgumentException("firstLine must be one-based, got " + firstLine);
        }
        if (lastLine < firstLine) {
            throw new IllegalArgumentException(
                    "lastLine " + lastLine + " precedes firstLine " + firstLine);
        }
    }

    /** Whether a changed line falls inside this declaration. */
    public boolean contains(int line) {
        return line >= firstLine && line <= lastLine;
    }
}
