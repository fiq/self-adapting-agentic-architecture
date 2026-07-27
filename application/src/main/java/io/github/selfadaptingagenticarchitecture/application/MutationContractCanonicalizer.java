package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.FitnessObjective;
import io.github.selfadaptingagenticarchitecture.core.MutationBounds;
import io.github.selfadaptingagenticarchitecture.core.MutationContract;
import io.github.selfadaptingagenticarchitecture.core.MutationTarget;
import io.github.selfadaptingagenticarchitecture.core.ParentTrait;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Renders a reviewable TOON mutation contract as the canonical S-expression mutation IR.
 *
 * <p>Semantic tokens (operator, target kind, loci, evidence, gates, objectives, search posture)
 * become lower-hyphenated atoms, so tokens differing only in case or word separator collapse to one
 * form. Identifiers (contract id, code symbol, candidate id, evidence id) are emitted verbatim
 * because case is meaningful there, and prose or paths are quoted.
 *
 * <p>Unquoted positions are checked against a safe shape and rejected otherwise, so model-authored
 * text cannot smuggle whitespace or parentheses into the IR and forge a node such as an extra gate.
 *
 * <p>Declaration order of loci, evidence and gates is significant: two contracts listing the same
 * evidence in a different order are different canonical forms. Order-insensitive equivalence is a
 * separate policy question and is not claimed here.
 *
 * <p>Generation only for now; a parser is warranted once something needs to read the IR back.
 */
public final class MutationContractCanonicalizer {
    private static final Pattern SAFE_ATOM = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9]+([-._][A-Za-z0-9]+)*");

    public String canonicalize(MutationContract contract) {
        Objects.requireNonNull(contract, "contract");

        StringBuilder out = new StringBuilder("(mutation");
        out.append(" (id ").append(identifier(contract.id())).append(')');
        out.append(" (operator ").append(contract.operator().wireName()).append(')');
        appendTarget(out, contract.target());
        appendTokenList(out, "loci", contract.loci());
        appendBounds(out, contract.bounds());
        appendTokenList(out, "evidence", contract.requiredEvidence());
        contract.searchPosture().ifPresent(posture -> out
                .append(" (search-posture (parent ").append(identifier(posture.parentCandidateId()))
                .append(") (objective ").append(atom(posture.objectiveFocus()))
                .append(") (expected-delta ").append(atom(posture.expectedDelta()))
                .append(") (risk-budget ").append(atom(posture.riskBudget()))
                .append("))"));
        appendParents(out, contract);
        appendFitness(out, contract);
        return out.append(')').toString();
    }

    private static void appendTarget(StringBuilder out, MutationTarget target) {
        out.append(" (target (kind ").append(atom(target.kind()))
                .append(") (file ").append(quoted(target.file()))
                .append(") (symbol ").append(identifier(target.symbol()))
                .append("))");
    }

    private static void appendBounds(StringBuilder out, MutationBounds bounds) {
        out.append(" (bounds (max-files-changed ").append(bounds.maxFilesChanged())
                .append(") (max-lines-changed ").append(bounds.maxLinesChanged())
                .append(") (public-api-change ").append(bounds.publicApiChange())
                .append(") (persistence-change ").append(bounds.persistenceChange())
                .append(") (production-config-change ").append(bounds.productionConfigChange())
                .append("))");
    }

    private static void appendParents(StringBuilder out, MutationContract contract) {
        if (contract.parentTraits().isEmpty()) {
            return;
        }
        out.append(" (parents");
        for (ParentTrait parentTrait : contract.parentTraits()) {
            out.append(" (parent (candidate ").append(identifier(parentTrait.parentCandidateId()))
                    .append(") (trait ").append(quoted(parentTrait.trait()))
                    .append(") (evidence ").append(identifier(parentTrait.evidenceId()))
                    .append("))");
        }
        out.append(')');
    }

    private static void appendFitness(StringBuilder out, MutationContract contract) {
        out.append(" (fitness");
        for (String gate : contract.hardGates()) {
            out.append(" (gate ").append(atom(gate)).append(')');
        }
        for (FitnessObjective objective : contract.objectives()) {
            out.append(" (objective ").append(atom(objective.id()))
                    .append(' ').append(String.format(Locale.ROOT, "%.2f", objective.weight()))
                    .append(')');
        }
        out.append(')');
    }

    private static void appendTokenList(StringBuilder out, String head, Iterable<String> tokens) {
        out.append(" (").append(head);
        for (String token : tokens) {
            out.append(' ').append(atom(token));
        }
        out.append(')');
    }

    private static String atom(String token) {
        String canonical = token.toLowerCase(Locale.ROOT).replace('_', '-');
        if (!SAFE_ATOM.matcher(canonical).matches()) {
            throw new IllegalArgumentException("token does not canonicalize to a safe atom: " + token);
        }
        return canonical;
    }

    private static String identifier(String value) {
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("identifier is not safe for the canonical IR: " + value);
        }
        return value;
    }

    private static String quoted(String prose) {
        return '"' + prose.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
