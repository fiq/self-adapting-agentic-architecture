package com.dreamthought.saaa.domain;

/**
 * How much of a source a frontend could actually read.
 *
 * <p>Distinguishing these four is what stops absence being mistaken for measurement. The JavaParser
 * spike returned a partial result whose tree contained no types and no methods: a shell. Reporting
 * that as recovered structure would put a confident-looking artefact in front of a gate with
 * nothing behind it.
 */
public enum StructureCompleteness {
    /** The source was read fully. Only this is eligible to gate a promotion. */
    COMPLETE,

    /**
     * The source did not parse cleanly but usable declarations were still recovered. May inform a
     * metric and may never gate, because a partially understood file is a partially understood
     * measurement.
     */
    RECOVERED_WITH_ERRORS,

    /**
     * The frontend understands the language but could not read this source into usable structure.
     * A partial tree carrying no declarations is reported here rather than as recovery: partial
     * success over an empty tree is absence dressed as evidence.
     */
    UNPARSEABLE,

    /** No frontend is registered for this language. Never a score; always a work item. */
    UNSUPPORTED
}
