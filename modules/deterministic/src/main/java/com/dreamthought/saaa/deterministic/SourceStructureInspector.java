package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.SourceStructure;
import com.dreamthought.saaa.domain.StructureLayer;
import java.util.Set;

/**
 * Reads source into the one structural model every capability consumes.
 *
 * <p>Implementations live in {@code adapters}, because a parser is a provider-shaped dependency and
 * this layer must stay free of them — the architecture fitness function enforces that, and was
 * extended to name parser packages before any parser was adopted.
 *
 * <p>An implementation is <em>supported</em> exactly when it passes the shared conformance suite for
 * the layers it declares. That is not a documentation convention: the suite is the contract, because
 * "find a parser and wrap it" is only safe if wrapped correctly is decidable by a machine. A human
 * reviewing an adapter for a language they do not read is not a control.
 */
public interface SourceStructureInspector {
    /** Identifies this frontend in provenance. Never used to vary behaviour. */
    String frontendId();

    /** The language this frontend reads, matched against a target's language id. */
    String languageId();

    /**
     * The layers this frontend fills when a source is readable.
     *
     * <p>The suite holds a frontend to exactly this declaration. Claiming a layer it cannot fill is
     * a lie the suite catches; omitting one it can fill costs it capabilities and nothing else.
     */
    Set<StructureLayer> declaredLayers();

    /**
     * Reads one source.
     *
     * <p>Never throws for source it cannot read: unreadable source is a result, not an exception,
     * because a capability has to record why a candidate could not be measured. A frontend that
     * recovered no declarations reports {@code UNPARSEABLE} rather than partial recovery.
     */
    SourceStructure inspect(String languageId, String source);
}
