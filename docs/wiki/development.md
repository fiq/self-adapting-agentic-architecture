# Development

How to build, test and integrate changes to SAAA.

Start with:

```sh
.agentic-template/bin/project startup
nix develop
```

Common commands:

```sh
.agentic-template/bin/project check
.agentic-template/bin/project test
.agentic-template/bin/project lint
.agentic-template/bin/project component-test
```

- `component-test` runs the outside-in acceptance tests for the mutation and
  fitness loop (`CHG-001`) and the `evolve` CLI command (`CHG-003`).

Standing boundary rules:

- Keep LangChain4j behind the `adapters/langchain4j` package.
- Keep deterministic decisions out of model authority.

The `evolve` command:

- Runs one mutation evaluation end to end with no model credentials.
- Uses the `fixture` proposer profile against a target folder inside a Git
  repository.
- See the README "Evolve a workflow" section.

Behaviour cases:

- Each `--behaviour-case <name>` is verified by `<name>.sh` in the target
  folder, and every declared case must pass before promotion.
- Declaring a case without wiring its check would let the required-behaviour
  hard gate pass without the evidence it names, so the mapping is total by
  construction.
- A check command that names a program by path must resolve inside the
  candidate worktree, symlinks followed.
- A committed symlink out of the tree is recreated faithfully by
  `git worktree add`, so without that guard a script that is not in the
  candidate could satisfy a required behaviour.
- An escaping command aborts the run rather than being recorded as a failed
  behaviour case, because it is a broken setup and not an observation about the
  mutation.

## Interface Direction

SAAA is intended to become a smart bridge in front of existing agents and
models.

- `ADR-0003` records two northbound interfaces:
    - MCP for tool-aware agents; and
    - a narrow OpenAI-compatible API for existing clients that want to use SAAA
      as a governed base URL.
- Keep the direction separate from the current `CHG-004` adapter work.
- The OpenAI-compatible LangChain4j wiring in `CHG-004` is southbound provider
  configuration: SAAA calling a model.
- A later northbound facade is clients calling SAAA.

## Resource-aware model routing

The application does not yet select models by task complexity.

- `Q-010` records the intended strategy: remaining tokens, provider credits,
  wall-clock budget, rate limits and retry allowance should become explicit
  experiment state that can select a model tier or bounded context packet.
- Routing must be decided and audited before proposal, while deterministic
  validation, fitness and promotion remain unchanged.
- Do not add opaque model-name heuristics until usage evidence and an ablation
  show that routing improves experiment throughput or quality.

Agent operation follows the [model-routing and session-efficiency
policy](../../.agents/coordination/MODEL_ROUTING_POLICY.md):

- Keep one session for one stable objective and role.
- Use source-referenced context packets.
- Record observed budget/cache/latency evidence when available.
- Reset before an independent review or a material scope/privacy change.
- The policy is not automatic SAAA provider routing; its safety and economic
  prerequisites remain open in `Q-010` and `Q-011`.

## Documentation Graph

New Markdown should fit the existing graph instead of creating a parallel
narrative.

Where each kind of content belongs:

- specs — intended behavior;
- knowledge entries — durable facts, decisions, risks and questions;
- ADRs — accepted architectural choices;
- wiki pages — short explanations that link back to those nodes.

- The current taxonomy is in `.agents/knowledge/TAXONOMY.md`.
- `Q-008` tracks whether the project needs a stronger ontology or DBpedia-style
  linked-data shape as the graph grows.
- Until then, prefer:
    - clear local terms in the glossary;
    - resolved knowledge IDs;
    - canonical edge names such as `relates_to`;
    - short wiki pages over broad external vocabulary.

## Tool-unavailable Integration Fallback

The default integration path is a branch plus a pull request. If PR or GitHub
tooling is unavailable, the lead agent may skip the PR only when the user
explicitly authorizes it.

Current status:

- PR creation works as of 2026-07-31, so the fallback does not currently apply;
  `CHG-003` went through PR #1.
- Pushing over SSH can still fail with `agent refused operation` when the SSH
  agent will not sign. Either:
    - unlock the key with `ssh-add`; or
    - run `gh auth setup-git` once and push over HTTPS with the `gh` token.
- The stored remote can stay on SSH.

Required fallback steps:

- Keep the work on a bounded branch.
- Run `.agentic-template/bin/project check`, `.agentic-template/bin/project ready`
  and relevant specialized tests.
- Request at least one risk-appropriate reviewer actor or subagent when agent
  tooling is available, then address findings before merge.
- If a reviewer actor times out while actor tooling is still available, retry
  with another actor or get explicit human review, then address any findings
  from that substituted review before merge.
- Self-review the staged branch in code-review style after actor or human
  review findings are addressed.
- If actor review tooling is unavailable, disclose the missing actor-review
  gate, get explicit user authorization for the degraded single-lead path, and
  record the degraded fallback level and compensating validation or human
  review needed.
- Record the fallback reason, validation, branch, commit, actor-review or
  substituted human-review result and lost independent review challenge in
  `HANDOFF.toon`.
- Merge to `main` only after explicit user authorization.
- Push `main` without force-pushing.
