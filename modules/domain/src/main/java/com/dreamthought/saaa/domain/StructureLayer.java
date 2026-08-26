package com.dreamthought.saaa.domain;

/**
 * A depth to which a frontend can fill the source-structure model.
 *
 * <p>ADR-0005 keeps one model rather than one type per fidelity. The variation between languages
 * lives here, in which layers were populated, because that is data a capability can branch on
 * deliberately. The alternative — a different type per fidelity — puts the same branch in every
 * consumer and grows with languages times capabilities.
 *
 * <p>Ordered from cheapest and most widely available to most demanding, which is also the order in
 * which frontends tend to be able to fill them.
 */
public enum StructureLayer {
    /**
     * Nodes, their kinds and their nesting. Every frontend fills this; a grammar is enough. It is
     * what duplicate detection, structural distance and complexity read.
     */
    SYNTAX,

    /**
     * Declarations and the references that resolve to them. Needs a language tool rather than a
     * grammar, so it is available for fewer languages. It is what the declared-locus gate reads,
     * and a frontend that cannot fill it is still useful for everything else.
     */
    SYMBOL,

    /**
     * Control flow and data dependence. No planned capability needs it yet; it is named so that a
     * frontend able to fill it has somewhere to declare that, rather than the enum being widened
     * later under pressure.
     */
    FLOW
}
