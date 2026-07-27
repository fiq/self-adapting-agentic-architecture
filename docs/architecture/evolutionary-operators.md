# Evolutionary Operators

This project is closer to genetic improvement of software than generic patch
generation. The model proposes or implements variation. Deterministic evidence
selects.

The broader practice is loop engineering: improving the agentic loop that
turns a goal into actions, tool use, checks, feedback and selection. A candidate
is not just "better code"; it is a revised loop behavior that survives measured
pressure.

```text
baseline implementation
        |
        | mutation contract
        | "change this bounded behavior here"
        v
candidate worktree
        |
        | model realizes contract as code/config/workflow diff
        v
candidate commit
        |
        | tests, properties, benchmarks, traces, reviews
        v
fitness result
        |
        +--> promote local candidate ref
        +--> discard and keep evidence
```

Metaphors:

- mutation contract: lab card for one experiment
- candidate worktree: isolated lab bench
- Git commit: frozen specimen
- fitness function: measuring rig
- SQLite evidence: lab notebook
- promotion: shelf the best specimen for later use

## Vocabulary

| Term | Meaning here |
|---|---|
| genotype | implementation or workflow representation being evolved |
| mutation | bounded behavioral variation to try |
| locus | method, class, workflow node, policy or config surface targeted |
| realization | Git diff produced to materialize the mutation |
| phenotype | behavior observed when the candidate runs |
| fitness | deterministic score from phenotype evidence |
| selection | deterministic promote or discard decision |

The mutation is not the diff. The diff is only how one candidate realized the
mutation.

## Contract Shape

Use two layers:

```text
human or LLM input
        |
        v
TOON envelope
  rationale, evidence, source refs, audit state
        |
        v
canonical S-expression mutation IR
  operator, target, loci, bounds, fitness gates
        |
        v
candidate Git diff and commit
```

TOON is the review and audit envelope because this repo already uses TOON for
state and specs. S-expressions are the internal mutation/operator
representation because they are regular trees: easy to parse, canonicalize,
validate, compare, mutate and later recombine.

## Mutation Operator Enum

The S-expression IR uses a closed initial operator enum. The enum is
semi-declarative input to the next loop: it selects default bounds, required
evidence and fitness dimensions before any model implementation happens.

| Operator | Use when | Required evidence |
|---|---|---|
| `targeted-behavior-change` | one named locus should behave differently | focused unit or component tests, trace predicate |
| `repair` | current behavior is failing or unsafe | reproducing failure, regression test, checks |
| `simplify` | behavior should stay equivalent with less complexity | characterization tests, diff budget, checks |
| `performance-tune` | latency, throughput, allocation or cost should improve | benchmark budget, correctness checks |
| `guardrail-change` | validator, safety or authority boundary should tighten | negative tests, boundary tests |
| `tool-strategy-change` | an agent loop should choose, order or call tools differently | trace predicate, integration or component test |
| `model-routing-change` | model/provider/prompt routing should change | routing test, cost or quality evidence |
| `prompt-policy-change` | prompt, instruction or policy text should alter behavior | golden scenario, safety check |
| `hill-climb` | a known-good parent should be locally improved along one fitness dimension | parent candidate ref, baseline score, focused objective delta |
| `exploratory-leap` | a higher-variance moonshot should test a new technique while staying fitness-aware | exploration budget, expected objective impact, rollback-safe bounds |

The enum is intentionally closed at first. Unknown operators are rejected until
a proposal adds semantics, validation defaults and fitness expectations.

```toon
mutation:
  id: MUT-001
  operator: targeted-behavior-change
  hypothesis: round money only at the boundary improves interest accuracy without changing public API
  target:
    kind: method
    file: src/main/java/example/billing/InterestCalculator.java
    symbol: calculateInterest
  loci:
    - method_body
    - adjacent_unit_tests
  bounds:
    max_files_changed: 2
    max_lines_changed: 80
    public_api_change: false
    persistence_change: false
  required_evidence:
    - unit_tests_pass
    - property_tests_pass
    - benchmark_not_worse_than_baseline
```

```lisp
(mutation
  (id MUT-001)
  (operator targeted-behavior-change)
  (hypothesis "round money only at the boundary improves interest accuracy without changing public API")
  (target
    (kind method)
    (file "src/main/java/example/billing/InterestCalculator.java")
    (symbol calculateInterest))
  (loci method-body adjacent-unit-tests)
  (bounds
    (max-files-changed 2)
    (max-lines-changed 80)
    (public-api-change false)
    (persistence-change false))
  (evidence unit-tests-pass property-tests-pass benchmark-not-worse-than-baseline))
```

## Targeted Mutation

The first operator family is targeted mutation:

```text
human or model input:
  "mutate the method that calculates interest"

contract:
  target = InterestCalculator.calculateInterest
  hypothesis = improve numerical stability
  bounds = max 2 files, no public API change
  evidence = unit + property + benchmark
  internal = canonical S-expression mutation IR

candidate:
  isolated Git worktree with one committed realization
```

This keeps LLM nondeterminism inside a small box. A model may produce different
implementations for the same contract, but validation and fitness do not move.

## Hill Climb and Exploratory Leap

Use `hill-climb` when the loop has a promising parent and a clear nearby
fitness gradient:

```lisp
(mutation
  (operator hill-climb)
  (parent candidate-042)
  (objective cost-latency-budget)
  (direction improve)
  (target (kind method) (symbol renderContentList))
  (bounds
    (max-files-changed 2)
    (max-lines-changed 60)
    (public-api-change false)))
```

Use `exploratory-leap` when the current population is stuck or lacks diversity,
but keep it fitness-function aware:

```lisp
(mutation
  (operator exploratory-leap)
  (parent candidate-042)
  (hypothesis "replace keyword-only CMS search with hybrid lexical ranking")
  (objective task-success)
  (exploration-budget high)
  (bounds
    (max-files-changed 5)
    (public-api-change false)
    (production-config-change false)))
```

The difference is search posture:

```text
hill-climb        = exploit a slope near a known-good candidate
exploratory-leap  = test a bigger idea with an explicit risk budget
selection         = same deterministic fitness gate for both
```

## Loop Engineering

The loop itself is the object being engineered:

```text
observe baseline loop
        |
        v
choose operator enum + target locus
        |
        v
write mutation contract + S-expression IR
        |
        v
realize candidate in Git worktree
        |
        v
evaluate behavior and evidence
        |
        v
select, discard or feed lessons into the next loop
```

This keeps exploration practical. The model can be creative inside a bounded
operator, but the loop decides what evidence is required and how selection
works.

## Conceptual Crossover

Crossover should start as diversity of thought, not raw diff splicing.

```text
parent A evidence: BigDecimal improved correctness but slowed benchmark
parent B evidence: cached rate factor improved speed but added complexity
        |
        v
child contract:
  operator targeted-behavior-change
  target same interest method
  combine BigDecimal boundary rounding with cached monthly factor
  require no API change and benchmark regression <= 5 percent
  internal = new canonical S-expression mutation IR
```

Allowed first crossover:

- combine traits, techniques, constraints or reviewer lessons
- use evaluated parents with recorded evidence
- create a new targeted mutation contract using the closed mutation operator
  enum
- keep one primary locus

Deferred crossover:

- merging raw diffs
- multi-locus recombination
- model-selected parents without deterministic evidence filters
- treating actor consensus as approval
- `conceptual-crossover` as a mutation operator enum value

## Literature Notes

- Manning, Sleator and Walsh describe genetic algorithms in terms of genotype,
  phenotype, fitness, selection, crossover and mutation:
  https://doi.org/10.4161/bioe.23041
- Koza's genetic programming work frames programs themselves as evolvable
  individuals under operators such as reproduction and crossover:
  https://www.genetic-programming.org/gpbook1toc.html
- Petke et al. survey genetic improvement of software as automated search for
  improved versions of existing software:
  https://doi.org/10.1109/TEVC.2017.2693219
- Lobo and Bazargani show cases where hillclimbers can outperform genetic
  algorithms depending on the fitness landscape:
  https://doi.org/10.1162/evco_a_00312
- Lehman and Stanley's novelty-search work is useful background for bounded
  exploratory leaps when objective pressure alone leads to local traps:
  https://doi.org/10.1162/EVCO_a_00025
- SIGSOFT empirical standards for optimization studies call for explicit
  fitness functions, stochasticity disclosure and repeated runs where relevant:
  https://www2.sigsoft.org/EmpiricalStandards/docs/standards
- NIST's 2025 LLM program-repair review supports treating LLMs as repair/code
  collaborators while preserving external evaluation:
  https://doi.org/10.1109/MC.2025.3527407
