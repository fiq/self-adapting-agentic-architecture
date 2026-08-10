package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.MutationOperatorType.TARGETED_BEHAVIOR_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.FitnessObjective;
import com.dreamthought.saaa.domain.FitnessSignalId;
import com.dreamthought.saaa.domain.MutationBounds;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.MutationTarget;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

final class MutationContractCanonicalizerTest {
    private final MutationContractCanonicalizer canonicalizer = new MutationContractCanonicalizer();

    @Test
    void emitsStableSExpressionMutationIr() {
        var contract = targetedContract(
                "MUT-001",
                List.of("method_body", "adjacent_unit_tests"),
                List.of("unit_tests_pass", "property_tests_pass", "benchmark_not_worse_than_baseline")
        );

        assertThat(canonicalizer.canonicalize(contract)).isEqualTo(
                "(mutation"
                        + " (id MUT-001)"
                        + " (operator targeted-behavior-change)"
                        + " (target"
                        + " (kind method)"
                        + " (file \"src/main/java/example/billing/InterestCalculator.java\")"
                        + " (symbol calculateInterest))"
                        + " (loci method-body adjacent-unit-tests)"
                        + " (bounds"
                        + " (max-files-changed 2)"
                        + " (max-lines-changed 80)"
                        + " (public-api-change false)"
                        + " (persistence-change false)"
                        + " (production-config-change false))"
                        + " (evidence unit-tests-pass property-tests-pass benchmark-not-worse-than-baseline)"
                        + " (fitness"
                        + " (gate (scope subject) (name deterministic-checks-pass))"
                        + " (gate (scope subject) (name required-evidence-present))"
                        + " (objective (scope subject) (name task-success) 0.40)"
                        + " (objective (scope subject) (name reliability) 0.20)"
                        + " (objective (scope subject) (name cost-latency-budget) 0.20)"
                        + " (objective (scope subject) (name behavioral-safety) 0.10)"
                        + " (objective (scope subject) (name parsimony) 0.10)))"
        );
    }

    @Property
    void canonicalizesSemanticTokensToLowerHyphenatedAtoms(@ForAll("locusStems") String locusStem) {
        var rawLocus = locusStem.toUpperCase(Locale.ROOT) + "_case";
        var contract = targetedContract("MUT-" + locusStem, List.of(rawLocus), List.of("unit_tests_pass"));

        assertThat(canonicalizer.canonicalize(contract))
                .contains("(loci " + locusStem.toLowerCase(Locale.ROOT) + "-case)");
    }

    @Test
    void refusesTokensThatWouldForgeCanonicalIrNodes() {
        var contract = targetedContract(
                "MUT-001",
                List.of("method_body"),
                List.of("unit_tests_pass", ") (gate model_approved_this")
        );

        assertThatThrownBy(() -> canonicalizer.canonicalize(contract))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("token does not canonicalize to a safe atom: ) (gate model_approved_this");
    }

    /**
     * A gate is a gate because of where it sits, not because of what it is called. Under the old
     * prefix convention an objective could be named to look like a gate; here the head node decides.
     */
    @Test
    void renderingPlacesRoleInThePositionNotTheName() {
        String rendered = new MutationContractCanonicalizer().canonicalize(contractWithObjectiveNamed("invariant"));

        assertThat(rendered).contains("(objective (scope subject) (name invariant)");
        assertThat(rendered).doesNotContain("(gate (scope subject) (name invariant)");
    }

    @Provide
    Arbitrary<String> locusStems() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
    }

    private static MutationContract targetedContract(String id, List<String> loci, List<String> requiredEvidence) {
        return new MutationContract(
                id,
                TARGETED_BEHAVIOR_CHANGE,
                "change interest rounding only at money boundaries",
                new MutationTarget(
                        "method",
                        "src/main/java/example/billing/InterestCalculator.java",
                        "calculateInterest"
                ),
                loci,
                new MutationBounds(2, 80, false, false, false),
                requiredEvidence,
                List.of(
                        FitnessSignalId.invariant("deterministic_checks_pass").canonical(),
                        FitnessSignalId.invariant("required_evidence_present").canonical()
                ),
                List.of(
                        new FitnessObjective(FitnessSignalId.objective("task_success").canonical(), 0.40),
                        new FitnessObjective(FitnessSignalId.objective("reliability").canonical(), 0.20),
                        new FitnessObjective(FitnessSignalId.objective("cost_latency_budget").canonical(), 0.20),
                        new FitnessObjective(FitnessSignalId.objective("behavioral_safety").canonical(), 0.10),
                        new FitnessObjective(FitnessSignalId.objective("parsimony").canonical(), 0.10)
                ),
                Optional.empty(),
                List.of()
        );
    }

    private static MutationContract contractWithObjectiveNamed(String name) {
        MutationContract base = targetedContract("MUT-role", List.of("locus"), List.of("unit_tests_pass"));
        return new MutationContract(
                base.id(), base.operator(), base.hypothesis(), base.target(), base.loci(), base.bounds(),
                base.requiredEvidence(), base.hardGates(),
                List.of(new FitnessObjective(FitnessSignalId.objective(name).canonical(), 1.0)),
                base.searchPosture(), base.parentTraits());
    }
}
