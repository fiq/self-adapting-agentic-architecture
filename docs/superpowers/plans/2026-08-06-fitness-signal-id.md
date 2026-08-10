# Fitness Signal Identifiers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `hard_gate_*` string convention with a typed fitness signal identifier, so a signal's role is decided by its type and its position in the IR rather than by a prefix on its name.

**Architecture:** A new domain record `FitnessSignalId(scope, force, name)` with two enums. The scorer, policy and bridge produce canonical string keys from it, so persisted rows and JSON keep a flat string shape. The MCP serializer partitions on `force` instead of sniffing a prefix. The S-expression canonicaliser renders scope and name as nested nodes, so being inside `(gate …)` is what makes something a gate.

**Tech Stack:** Java 21 records and enums, JUnit 5, AssertJ, jqwik, Gradle.

## Global Constraints

- The canonical string form is `<scope>.<force>.<name>`, all lower case, e.g. `subject.invariant.deterministic_checks` and `subject.objective.parsimony`. Scope and force are the enum names lower-cased. The name segment keeps `snake_case`.
- Weights and the promotion threshold do NOT change. `PROMOTION_THRESHOLD` stays `0.80`; `DEFAULT_OBJECTIVES` weights stay 0.40 / 0.20 / 0.20 / 0.10 / 0.10 in that order.
- Scoring behaviour does NOT change. The same candidate must produce the same decision and the same aggregate score before and after this branch. Only identifiers and their representation change.
- No new dependencies.
- Real commit author and committer dates. Do NOT set `GIT_AUTHOR_DATE`, `GIT_COMMITTER_DATE` or `--date`.
- Commit with `git -c user.name="Raf Gemmail" -c user.email="raf@dreamthought.com" commit`.
- After every task: `.agentic-template/bin/project test` and `.agentic-template/bin/project component-test` must pass. After the last task `.agentic-template/bin/project check` and `ready` must also pass.
- Work in the worktree `/home/raf/Code/github/self-adapting-agentic-architecture/.worktrees/feat-fitness-signal-id` on branch `feat/fitness-signal-id`.

## Name mapping

| Old | New canonical string |
|---|---|
| `hard_gate_deterministic_checks` | `subject.invariant.deterministic_checks` |
| `hard_gate_required_behavior_cases` | `subject.invariant.required_behavior_cases` |
| `hard_gate_required_objective_scores` | `subject.invariant.required_objective_scores` |
| `hard_gate_non_empty_realization` | `subject.invariant.non_empty_realization` |
| `task_success` | `subject.objective.task_success` |
| `reliability` | `subject.objective.reliability` |
| `cost_latency_budget` | `subject.objective.cost_latency_budget` |
| `behavioral_safety` | `subject.objective.behavioral_safety` |
| `parsimony` | `subject.objective.parsimony` |
| `deterministic_checks_pass` (contract-declared) | `subject.invariant.deterministic_checks_pass` |
| `required_evidence_present` (contract-declared) | `subject.invariant.required_evidence_present` |

---

### Task 1: The identifier type

**Files:**
- Create: `modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessScope.java`
- Create: `modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessForce.java`
- Create: `modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessSignalId.java`
- Test: `modules/domain/src/test/java/com/dreamthought/saaa/domain/FitnessSignalIdTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `FitnessSignalId.invariant(String name)`, `FitnessSignalId.objective(String name)`, `FitnessSignalId.processInvariant(String name)`, instance methods `scope()`, `force()`, `name()`, `canonical()`, and static `FitnessSignalId parse(String canonical)`. Later tasks use all of these.

- [ ] **Step 1: Write the failing test**

Create `modules/domain/src/test/java/com/dreamthought/saaa/domain/FitnessSignalIdTest.java`:

```java
package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class FitnessSignalIdTest {
    @Test
    void rendersScopeForceAndNameAsACanonicalString() {
        assertThat(FitnessSignalId.invariant("deterministic_checks").canonical())
                .isEqualTo("subject.invariant.deterministic_checks");
        assertThat(FitnessSignalId.objective("parsimony").canonical())
                .isEqualTo("subject.objective.parsimony");
        assertThat(FitnessSignalId.processInvariant("layer_boundaries").canonical())
                .isEqualTo("process.invariant.layer_boundaries");
    }

    @Test
    void parsesItsOwnCanonicalForm() {
        FitnessSignalId id = FitnessSignalId.parse("subject.objective.task_success");

        assertThat(id.scope()).isEqualTo(FitnessScope.SUBJECT);
        assertThat(id.force()).isEqualTo(FitnessForce.OBJECTIVE);
        assertThat(id.name()).isEqualTo("task_success");
        assertThat(id).isEqualTo(FitnessSignalId.objective("task_success"));
    }

    /**
     * The role must come from the type, never from the name. A signal named to look like a gate
     * is still whatever its force says it is, which is the property a prefix convention cannot give.
     */
    @Test
    void aNameThatLooksLikeAnotherRoleDoesNotChangeTheRole() {
        FitnessSignalId id = FitnessSignalId.objective("invariant");

        assertThat(id.force()).isEqualTo(FitnessForce.OBJECTIVE);
        assertThat(id.canonical()).isEqualTo("subject.objective.invariant");
    }

    @Test
    void rejectsAMalformedCanonicalString() {
        assertThatThrownBy(() -> FitnessSignalId.parse("hard_gate_deterministic_checks"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FitnessSignalId.parse("subject.invariant"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FitnessSignalId.parse("nowhere.invariant.x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANameThatIsNotASafeSegment() {
        assertThatThrownBy(() -> FitnessSignalId.objective("has.dot"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FitnessSignalId.objective("has space"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FitnessSignalId.objective(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.agentic-template/bin/gradle-command :domain:test --tests '*FitnessSignalIdTest*'`
Expected: compilation failure, `FitnessSignalId` does not exist.

- [ ] **Step 3: Write the implementation**

Create `modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessScope.java`:

```java
package com.dreamthought.saaa.domain;

/** Whose property a fitness signal describes: the candidate under evaluation, or SAAA itself. */
public enum FitnessScope {
    SUBJECT,
    PROCESS
}
```

Create `modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessForce.java`:

```java
package com.dreamthought.saaa.domain;

/**
 * Whether a fitness signal must hold or merely contributes.
 *
 * <p>An invariant is binary for the promote-or-discard decision and is never tradeable against an
 * objective. An objective compounds into the score and is only consulted once every invariant has
 * passed.
 */
public enum FitnessForce {
    INVARIANT,
    OBJECTIVE
}
```

Create `modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessSignalId.java`:

```java
package com.dreamthought.saaa.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies one fitness signal by whose property it is, whether it must hold, and what it is
 * called.
 *
 * <p>Role comes from {@link #force()}, never from the name. The previous convention encoded role in
 * a {@code hard_gate_} name prefix, which meant a signal could claim a role by being named for one
 * and every consumer had to sniff the string to find out.
 */
public record FitnessSignalId(FitnessScope scope, FitnessForce force, String name) {
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");
    private static final int SEGMENTS = 3;

    public FitnessSignalId {
        scope = Objects.requireNonNull(scope, "scope");
        force = Objects.requireNonNull(force, "force");
        Objects.requireNonNull(name, "name");
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "name must be lower snake_case with no separators of its own: " + name);
        }
    }

    public static FitnessSignalId invariant(String name) {
        return new FitnessSignalId(FitnessScope.SUBJECT, FitnessForce.INVARIANT, name);
    }

    public static FitnessSignalId objective(String name) {
        return new FitnessSignalId(FitnessScope.SUBJECT, FitnessForce.OBJECTIVE, name);
    }

    public static FitnessSignalId processInvariant(String name) {
        return new FitnessSignalId(FitnessScope.PROCESS, FitnessForce.INVARIANT, name);
    }

    /** The flat form used for map keys, persisted rows and JSON, where a nested shape is unavailable. */
    public String canonical() {
        return segment(scope.name()) + "." + segment(force.name()) + "." + name;
    }

    public static FitnessSignalId parse(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        String[] parts = canonical.split("\\.", -1);
        if (parts.length != SEGMENTS) {
            throw new IllegalArgumentException(
                    "fitness signal id needs exactly " + SEGMENTS + " dot-separated segments: " + canonical);
        }
        return new FitnessSignalId(scopeOf(parts[0], canonical), forceOf(parts[1], canonical), parts[2]);
    }

    private static FitnessScope scopeOf(String value, String canonical) {
        for (FitnessScope candidate : FitnessScope.values()) {
            if (segment(candidate.name()).equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown fitness scope in " + canonical + ": " + value);
    }

    private static FitnessForce forceOf(String value, String canonical) {
        for (FitnessForce candidate : FitnessForce.values()) {
            if (segment(candidate.name()).equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown fitness force in " + canonical + ": " + value);
    }

    private static String segment(String enumName) {
        return enumName.toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.agentic-template/bin/gradle-command :domain:test --tests '*FitnessSignalIdTest*'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessScope.java \
        modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessForce.java \
        modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessSignalId.java \
        modules/domain/src/test/java/com/dreamthought/saaa/domain/FitnessSignalIdTest.java
git -c user.name="Raf Gemmail" -c user.email="raf@dreamthought.com" commit -m "Add a typed fitness signal identifier

Role comes from the force, not from a name prefix. The hard_gate_
convention let a signal claim a role by being named for one, and made
every consumer sniff the string to find out which it was."
```

---

### Task 2: Adopt it in the deterministic layer

**Files:**
- Modify: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeFitnessScorer.java`
- Modify: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationOperatorPolicy.java`
- Modify: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeBridgeScorer.java`
- Modify: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/PhenotypeFitnessScorerTest.java`
- Modify: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/PhenotypeFitnessScorerPropertyTest.java`
- Modify: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/PhenotypeBridgeScorerTest.java`
- Modify: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/GoldenCorpus.java`
- Modify: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/MutationOperatorTypeTest.java`
- Modify: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/MutationContractValidatorTest.java`
- Modify: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/SearchPosturePolicyTest.java`

**Interfaces:**
- Consumes: `FitnessSignalId` from Task 1.
- Produces: `PhenotypeFitnessScorer` public constants become `FitnessSignalId` values named `DETERMINISTIC_CHECKS_GATE`, `REQUIRED_BEHAVIOR_CASES_GATE`, `REQUIRED_OBJECTIVE_SCORES_GATE`, `NON_EMPTY_REALIZATION_GATE` (same names, new type). `FitnessResult.objectives()` keys become canonical strings. Task 3 and Task 4 depend on those key shapes.

- [ ] **Step 1: Change the four gate constants and the objective ids**

In `PhenotypeFitnessScorer`, replace the four `public static final String` gate constants with:

```java
    public static final FitnessSignalId DETERMINISTIC_CHECKS_GATE =
            FitnessSignalId.invariant("deterministic_checks");
    public static final FitnessSignalId REQUIRED_BEHAVIOR_CASES_GATE =
            FitnessSignalId.invariant("required_behavior_cases");
    public static final FitnessSignalId REQUIRED_OBJECTIVE_SCORES_GATE =
            FitnessSignalId.invariant("required_objective_scores");
    public static final FitnessSignalId NON_EMPTY_REALIZATION_GATE =
            FitnessSignalId.invariant("non_empty_realization");
```

Add the import for `com.dreamthought.saaa.domain.FitnessSignalId`. In `score`, the four `objectives.put(...)` calls become `objectives.put(DETERMINISTIC_CHECKS_GATE.canonical(), gateValue(checksPassed))` and so on for the other three. Leave the comment above them explaining that gate outcomes are written after measured scores.

In `MutationOperatorPolicy`, change `DEFAULT_OBJECTIVES` to build ids through the type, keeping the weights and their order exactly:

```java
    public static final List<FitnessObjective> DEFAULT_OBJECTIVES = List.of(
            new FitnessObjective(FitnessSignalId.objective("task_success").canonical(), 0.40),
            new FitnessObjective(FitnessSignalId.objective("reliability").canonical(), 0.20),
            new FitnessObjective(FitnessSignalId.objective("cost_latency_budget").canonical(), 0.20),
            new FitnessObjective(FitnessSignalId.objective("behavioral_safety").canonical(), 0.10),
            new FitnessObjective(FitnessSignalId.objective("parsimony").canonical(), 0.10)
    );
```

And change `DEFAULT_HARD_GATES` the same way:

```java
    public static final List<String> DEFAULT_HARD_GATES = List.of(
            FitnessSignalId.invariant("deterministic_checks_pass").canonical(),
            FitnessSignalId.invariant("required_evidence_present").canonical());
```

In `PhenotypeBridgeScorer`, change the five `objectives.put("task_success", ...)` style calls to use `FitnessSignalId.objective("task_success").canonical()` and so on. Do not change any derivation logic.

- [ ] **Step 2: Update the deterministic tests to the new identifiers**

Every string literal in the listed test files that names a gate or objective becomes its new canonical form per the plan's name mapping table. Where a test references `PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE` or a sibling constant as a map key, append `.canonical()`.

Do NOT change any expected score, weight, threshold or decision. If a test's expected numeric value changes, you have broken behaviour: stop and report BLOCKED.

- [ ] **Step 3: Run the deterministic suites**

Run: `.agentic-template/bin/gradle-command :deterministic:test`
Expected: PASS. Then run `.agentic-template/bin/gradle-command :deterministic:acceptanceTest` — expected PASS.

- [ ] **Step 4: Run the whole unit suite to find callers you missed**

Run: `.agentic-template/bin/project test`
Expected: failures only in `:adapters` and `:cli` tests that still expect old identifiers. Note them; Task 4 fixes the adapters. If `:deterministic` still fails, fix it here.

- [ ] **Step 5: Commit**

```bash
git add modules/deterministic
git -c user.name="Raf Gemmail" -c user.email="raf@dreamthought.com" commit -m "Adopt fitness signal identifiers in the deterministic layer

Gate constants and objective ids are now built through FitnessSignalId.
Weights, threshold and every derivation are unchanged: the same candidate
still produces the same score and the same decision."
```

---

### Task 3: Render fitness structurally in the S-expression IR

**Files:**
- Modify: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationContractCanonicalizer.java`
- Modify: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/MutationContractCanonicalizerTest.java`

**Interfaces:**
- Consumes: `FitnessSignalId` from Task 1, and the canonical id strings now in `MutationOperatorPolicy` from Task 2.
- Produces: a changed canonical IR shape for the `(fitness …)` node. Nothing later in this plan depends on it.

- [ ] **Step 1: Change the rendering**

`appendFitness` currently renders `(gate <atom>)` and `(objective <atom> <weight>)`, passing the whole id through `atom()`. `atom()` rejects dots, so a canonical id would throw. Replace the method with a structural rendering that puts scope and name in their own nodes:

```java
    private static void appendFitness(StringBuilder out, MutationContract contract) {
        out.append(" (fitness");
        for (String gate : contract.hardGates()) {
            appendSignal(out, "gate", FitnessSignalId.parse(gate));
            out.append(')');
        }
        for (FitnessObjective objective : contract.objectives()) {
            appendSignal(out, "objective", FitnessSignalId.parse(objective.id()));
            out.append(' ').append(String.format(Locale.ROOT, "%.2f", objective.weight())).append(')');
        }
        out.append(')');
    }

    /**
     * Position decides role: a signal is a gate because it sits inside a {@code (gate …)} node, not
     * because of anything in its name. The force is therefore not rendered — the head carries it.
     */
    private static void appendSignal(StringBuilder out, String head, FitnessSignalId id) {
        out.append(" (").append(head)
                .append(" (scope ").append(atom(id.scope().name()))
                .append(") (name ").append(atom(id.name()))
                .append(')');
    }
```

Add the import for `com.dreamthought.saaa.domain.FitnessSignalId`. Leave `atom()`, `SAFE_ATOM` and every other rendering method untouched: `atom()` now only ever sees a single segment, which already satisfies the pattern.

- [ ] **Step 2: Update the canonicaliser test expectations**

In `MutationContractCanonicalizerTest`, every expected `(gate x)` becomes `(gate (scope subject) (name x))` and every expected `(objective x 0.40)` becomes `(objective (scope subject) (name x) 0.40)`, with `x` being the bare name segment, hyphenated by `atom()` as before. Update the input contracts to use canonical ids.

Add this test to the same file:

```java
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
```

The file already has a private helper at line 86:

```java
private static MutationContract targetedContract(String id, List<String> loci, List<String> requiredEvidence)
```

Add `contractWithObjectiveNamed` beside it, reusing that helper's construction and overriding only the objectives list:

```java
    private static MutationContract contractWithObjectiveNamed(String name) {
        MutationContract base = targetedContract("MUT-role", List.of("locus"), List.of("unit_tests_pass"));
        return new MutationContract(
                base.id(), base.operator(), base.hypothesis(), base.target(), base.loci(), base.bounds(),
                base.requiredEvidence(), base.hardGates(),
                List.of(new FitnessObjective(FitnessSignalId.objective(name).canonical(), 1.0)),
                base.searchPosture(), base.parentTraits());
    }
```

If `targetedContract`'s signature differs from the above, call it with whatever arguments its neighbouring tests use; only the objectives list matters here.

- [ ] **Step 3: Run the canonicaliser tests**

Run: `.agentic-template/bin/gradle-command :deterministic:test --tests '*MutationContractCanonicalizerTest*'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/deterministic
git -c user.name="Raf Gemmail" -c user.email="raf@dreamthought.com" commit -m "Render fitness signals structurally in the canonical IR

A signal is a gate because it sits inside a (gate ...) node rather than
because of a prefix on its name, so a model-authored name can no longer
imply a role. The force is not rendered: the head node carries it."
```

---

### Task 4: Partition MCP output on force, not on a prefix

**Files:**
- Modify: `modules/adapters/src/main/java/com/dreamthought/saaa/adapters/mcp/EvolveMcpResponseSerializer.java`
- Modify: `modules/adapters/src/test/java/com/dreamthought/saaa/adapters/mcp/EvolveMcpToolTest.java`
- Modify: `modules/adapters/src/test/java/com/dreamthought/saaa/adapters/mcp/EvolveMcpServerTest.java`
- Modify: `modules/adapters/src/test/java/com/dreamthought/saaa/adapters/journal/JournalReporterTest.java`
- Modify: `modules/cli/src/main/java/com/dreamthought/saaa/cli/BenchmarkCommand.java`

**Interfaces:**
- Consumes: `FitnessSignalId` from Task 1 and the canonical key shape from Task 2.
- Produces: nothing later depends on it.

- [ ] **Step 1: Replace the prefix sniff**

In `EvolveMcpResponseSerializer.appendObjectives`, the filter currently reads:

```java
                .filter(entry -> entry.getKey().startsWith("hard_gate_") == hardGate)
```

Replace it with a force comparison:

```java
                .filter(entry -> isInvariant(entry.getKey()) == hardGate)
```

and add:

```java
    /**
     * Measured objectives are serialised before invariants. The discriminator is the signal's force,
     * so an objective cannot land in the invariant group by being named like one.
     */
    private static boolean isInvariant(String key) {
        return FitnessSignalId.parse(key).force() == FitnessForce.INVARIANT;
    }
```

Add imports for `com.dreamthought.saaa.domain.FitnessForce` and `com.dreamthought.saaa.domain.FitnessSignalId`. Rename the `hardGate` parameter to `invariant` throughout that method and its two call sites so the naming matches. Do not change ordering behaviour: measured objectives still come first, each group still sorted by key.

In `BenchmarkCommand`, update the one `hard_gate_` reference to the new canonical form per the name mapping table.

- [ ] **Step 2: Update adapter and CLI test expectations**

In the three listed test files, every gate or objective string literal becomes its new canonical form per the name mapping table. Expected JSON key ordering changes as a consequence: within each group keys are still sorted, but the sort now runs over the new strings. Recompute the expected order by sorting the new canonical strings lexicographically rather than assuming the old order still holds.

Do NOT change any expected score, weight, threshold or decision.

- [ ] **Step 3: Run the full suite**

Run: `.agentic-template/bin/project test`
Expected: PASS, all modules.

Run: `.agentic-template/bin/project component-test`
Expected: PASS.

Run: `.agentic-template/bin/project integration-test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/adapters modules/cli
git -c user.name="Raf Gemmail" -c user.email="raf@dreamthought.com" commit -m "Partition MCP objectives on force rather than a name prefix

The serializer sniffed a hard_gate_ prefix to decide which group a key
belonged in, so a name could put a signal in the wrong group. It now asks
the identifier for its force."
```

---

### Task 5: Documentation and handoff

**Files:**
- Modify: `README.md`
- Modify: `.agents/knowledge/contracts/CON-002-fitness-naming-and-severity.md`
- Modify: `docs/wiki/glossary.md`
- Modify: `HANDOFF.toon`
- Modify: `specs/changes/CHG-003-first-vertical-slice/design.md`
- Modify: `specs/changes/CHG-004-live-mcp-and-l3-utility/design.md`
- Modify: `specs/changes/CHG-004-live-mcp-and-l3-utility/change.toon`

**Interfaces:**
- Consumes: everything above.
- Produces: nothing.

- [ ] **Step 1: Update every documented identifier**

In `README.md` and the three spec files, replace each `hard_gate_*` occurrence with its new canonical form per the name mapping table. The README's hard-gate table and its worked scoring example both carry them.

- [ ] **Step 2: Promote CON-002 from proposed to canonical**

In `.agents/knowledge/contracts/CON-002-fitness-naming-and-severity.md`:
- change `status: proposed` to `status: canonical`
- replace the paragraph beginning "The existing `hard_gate_*` identifiers do not yet follow this scheme" with:

```markdown
The scheme is in force. `FitnessSignalId` in `modules/domain` is the type, and
`PhenotypeFitnessScorer`, `MutationOperatorPolicy` and `PhenotypeBridgeScorer`
build every identifier through it. The S-expression IR renders scope and name as
nested nodes, so a signal is a gate because of where it sits rather than what it
is called, and `EvolveMcpResponseSerializer` partitions on force rather than on a
name prefix.

The severity classes below are not yet enforced anywhere.
```

- [ ] **Step 3: Update the glossary's "today" disclaimers**

Three glossary entries carry a sentence saying the described model is the target recorded in `CON-002`. The naming half is now real; the ranking and severity halves are not. In `## Invariant` and `## Objective`, replace "This is the target model recorded in `CON-002`" with "The naming scheme is in force; see `CON-002`", keeping the rest of each sentence describing what happens today. Leave `## Severity Class` unchanged: no severity partition exists in the code.

- [ ] **Step 4: Update the handoff**

Set `session.branch` to `feat/fitness-signal-id` and `session.status` to `fitness_signal_id_applied`. Add to `completed:` a line recording that the naming scheme is applied, that role now comes from position and type rather than a name prefix, and that scoring behaviour is unchanged. Add to `fitness_functions:` a line noting the identifier change and that no weight, threshold or derivation moved. Add the validation runs to `tests_run:`.

- [ ] **Step 5: Run every gate**

Run: `.agentic-template/bin/project check` — expected all OK including `GLOSSARY OK`.
Run: `.agentic-template/bin/project ready` — expected `READY: PASS`, 0 failed.
Run: `git diff --check` — expected no output.

- [ ] **Step 6: Commit**

```bash
git add -A
git -c user.name="Raf Gemmail" -c user.email="raf@dreamthought.com" commit -m "Document the applied fitness naming scheme

CON-002 moves to canonical: the scheme is in force in code rather than
recorded as a target. The severity classes it also describes remain
unenforced, and the glossary still says so."
```

---

## Notes for the implementer

**The behaviour-preservation rule is the important one.** This branch changes identifiers and one rendering. It must not change a single score, weight, threshold or decision. `GoldenCorpus` and `PhenotypeFitnessScorerPropertyTest` are the tripwires: if either needs a numeric expectation changed, something is wrong with the refactor, not with the test.

**Why `atom()` is left alone.** It rejects dots. Under the structural rendering it only ever receives a single name or scope segment, so it never sees a canonical id and needs no widening. That is a benefit of the structural form rather than an accident.

**What is deliberately not in scope.** Severity classes, violation magnitudes, and the `process.invariant.*` signals are recorded in `CON-002` but nothing produces or consumes them yet. Do not add them.
