# Context Store

The template treats the repository itself as the context store: a versioned set
of files and checks that explain what the project is, why it is shaped that way,
how it should behave and whether it still conforms.

Do not add an external vector store, database or SaaS memory layer by default.
Use one only when project evidence justifies it and `PROJECT_PROFILE.toon`
records the decision, risks and validation path.

## Layers

| Layer | Repository sources | What it answers |
|---|---|---|
| Structure | `AGENTS.md`, `README.md`, `PROJECT_PROFILE.toon.architecture`, `docs/wiki/architecture.md` | What exists, where boundaries are, and how commands are shaped |
| Lineage | `PROJECT_PROFILE.toon.decisions`, `PROJECT_PROFILE.toon.rejected_options`, `HANDOFF.toon`, `docs/decisions/`, `.agents/knowledge/` | Why choices were made, what changed, and what remains unresolved |
| Behavior | `specs/capabilities/`, `specs/changes/`, tests selected from `docs/validation.md` | What the system promises and how those promises are verified |
| Conformance | `.agentic-template/bin/project check`, `project ready`, CI, specialised architecture fitness functions | Whether current code still respects important constraints |

## Startup Query

For non-trivial work:

1. Run `.agentic-template/bin/project startup`.
2. Read `HANDOFF.toon`, `PROJECT_PROFILE.toon`, this file and
   `.agents/knowledge/index.md`.
3. Use `.agentic-template/bin/project docs` if the next artifact is unclear.
4. Read relevant specs and knowledge entries before planning or implementation.

## Change Handoff

Every non-trivial change should leave enough context for the next human or
agent to continue without reconstructing intent from code alone:

- spec reference, or a clear no-spec rationale for trivial/mechanical work;
- tests added or changed, plus validation results;
- fitness-function delta, or a no-change rationale;
- decisions, unknowns, risks and rejected options updated where material;
- knowledge proposal, learning, question, risk or no-record rationale.

## Fitness Functions

Architecture fitness functions are cheap, deterministic checks that protect
project-specific characteristics. Generated projects should identify the top
1-3 architecture risks and encode checks when practical.

Good candidates include:

- dependency direction and forbidden imports;
- public API, event or file schema drift;
- provider-specific code leaking across clean boundaries;
- migration drift for persistence-backed systems;
- performance, accessibility, security or deployability budgets;
- required container health checks and health-aware Compose dependencies.

Wire fitness functions into `.agentic-template/bin/project check` or
`.agentic-template/bin/project ready`. If the best check is still manual,
record the command or inspection path in `docs/validation.md` and the current
result in `HANDOFF.toon.tests_run`.

## Further Reading

- InfoQ, "Comprehension at AI Speed: Building a Context Store for Evolutionary
  Architecture" (2026-07-14): https://www.infoq.com/articles/ai-speed-context-store-architecture/

The useful takeaway for this template is the repo-native operating model:
specs feed intent forward, tests and fitness functions feed conformance back,
and handoff/knowledge artifacts preserve lineage. The template does not adopt a
new storage system by default.
