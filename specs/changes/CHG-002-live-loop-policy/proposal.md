# Live Mutation Loop Policy

## Why

`CHG-001` proves the orchestration shape with deterministic ports and adapter
slices. The next thin slice should not add broad implementation until the live
loop policy is explicit.

The central correction for this proposal is that a mutation is not a patch. In
the genetic-algorithm sense, a mutation is a bounded variation of an individual
in the search space. For this project, the individual is a workflow genotype and
the phenotype is the behavior observed when that workflow runs against
deterministic evaluation cases. A patch may be one implementation-level
realization of the mutation, but the fitness function evaluates behavior, not
the patch.

Five questions still decide the shape of that work: the first LangChain4j
provider, the mutation contract and internal representation, the realization
boundary, the promotion target and the initial fitness objectives.

## External Evidence Consulted

- LangChain4j AI Services are Java interfaces backed by model calls and can
  return structured values, which matches the existing typed mutation proposer.
  Source: https://docs.langchain4j.dev/tutorials/ai-services/
- LangChain4j provides an OpenAI official integration whose
  `OpenAiOfficialChatModel` implements the LangChain4j `ChatModel` interface,
  so provider construction can stay adapter-local.
  Source: https://docs.langchain4j.dev/integrations/language-models/open-ai-official/
- LangChain4j and OpenAI both support structured output patterns, but schema
  conformance is still only model-output shaping. This project still needs
  deterministic Java validation as the authority boundary.
  Sources: https://docs.langchain4j.dev/tutorials/structured-outputs/ and
  https://platform.openai.com/docs/guides/structured-outputs
- Evolutionary algorithm literature separates candidate representations,
  mutation operators and fitness evaluation. A mutation changes the candidate
  representation; fitness is assessed from the resulting candidate behavior.
  Source: https://pmc.ncbi.nlm.nih.gov/articles/PMC3813526/
- Genetic improvement research frames software evolution as automated search
  over improved versions of existing software, including performance and
  functionality changes.
  Source: https://discovery.ucl.ac.uk/id/eprint/10038273/
- Genetic programming established program evolution through operators such as
  mutation and crossover, but this project starts with targeted mutations
  because LLM implementation introduces extra nondeterminism.
  Source: https://www.genetic-programming.org/gpbook1toc.html
- Hill climbing is useful local search when the fitness landscape supports
  exploitation near a promising candidate.
  Source: https://doi.org/10.1162/evco_a_00312
- Novelty-search literature is useful background for bounded exploratory
  variants when objective pressure risks local traps.
  Source: https://doi.org/10.1162/EVCO_a_00025
- Empirical standards for optimization studies in software engineering call
  out stochasticity, repeated runs and explicit fitness functions.
  Source: https://www2.sigsoft.org/EmpiricalStandards/docs/standards
- LLM program repair literature supports using LLMs as code-producing
  collaborators, but not as the deterministic evaluation authority.
  Source: https://www.nist.gov/publications/can-ai-fix-buggy-code-exploring-use-large-language-models-automated-program-repair
- Git worktrees support multiple working trees attached to one repository,
  matching the already implemented candidate isolation adapter.
  Source: https://git-scm.com/docs/git-worktree

## Proposal

Adopt a proposal-only live-loop policy for the next implementation slice:

- First live model provider is OpenAI through LangChain4j's official OpenAI
  integration, constructed only inside `adapters/langchain4j`.
- CLI configuration selects the provider and model explicitly. No live model
  call is attempted unless required environment variables are present.
- The first mutation contract uses two layers: a TOON audit envelope and a
  canonical S-expression internal mutation IR.
- TOON captures rationale, evidence, sources and review state. S-expressions
  capture operator, target, loci, bounds and fitness gates for deterministic
  parsing, validation, mutation and later crossover.
- The internal S-expression IR uses a closed initial mutation operator enum.
  Unknown operators are rejected until their bounds and evidence requirements
  are specified.
- The enum includes `hill-climb` for local fitness-aware exploitation and
  `exploratory-leap` for bounded moonshot variants that remain fitness-aware.
- A model-proposed mutation must declare a behavioral hypothesis, target
  surface, mutation operator, loci, bounds, required evidence and expected
  measurable effect.
- The concrete realization is a Git diff committed in an isolated candidate
  worktree. It may include code, tests, configuration or workflow definitions,
  but the diff is not the mutation.
- The validator checks the contract and realized candidate against deterministic
  bounds: allowed files and symbols, maximum change size, forbidden public API
  or persistence changes, required tests and authority boundaries.
- Conceptual crossover is accepted as a later recombination policy: combine
  traits, techniques or lessons from evaluated parents into a new targeted
  mutation contract using the closed operator enum. Raw diff crossover is
  deferred.
- The evolutionary process is treated as loop engineering: each mutation
  targets a bounded part of an agentic loop, such as model routing, prompt
  policy, tool strategy, validation or scoring behavior.
- Promotion creates deterministic local promotion metadata and a local Git ref
  under `refs/heads/promoted/<candidate-id>`. It does not merge to `main`, does
  not deploy and does not delete candidate evidence.
- Fitness scoring uses hard deterministic gates plus weighted objectives over
  evaluation evidence from the candidate phenotype. The model can supply the
  proposed behavioral mutation and realization, but not score, approval,
  rollback or promotion fields.
- Reviewer actors, when available, are evidence producers only. If actor tools
  are unavailable, the loop must record that fact and apply the configured
  deterministic policy instead of silently treating review as passed.

## Non-goals

- No OpenSearch, vector storage or external memory layer.
- No AST mutation or LSP-backed repair loop.
- No distributed candidate workers.
- No automatic production deployment.
- No direct promotion to `main`.
- No model-authored fitness score or model approval channel.

## Approval Questions

1. Accept OpenAI via LangChain4j's official OpenAI adapter as the first live
   provider, with provider and model selected by configuration.
2. Accept TOON mutation-contract envelopes with canonical S-expression internal
   mutation IR.
3. Accept local `refs/heads/promoted/<candidate-id>` plus SQLite metadata as
   the first promotion target.
4. Accept phenotype-based fitness objectives and initial weights defined in
   `design.md`.
5. Accept conceptual crossover as deferred trait recombination, not raw code
   diff merging.
