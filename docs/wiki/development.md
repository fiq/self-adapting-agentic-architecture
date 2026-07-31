# Development

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

`component-test` runs the outside-in acceptance tests for the mutation and
fitness loop (`CHG-001`) and the `evolve` CLI command (`CHG-003`). Keep
LangChain4j behind the `adapters/langchain4j` package and keep deterministic
decisions out of model authority.

The `evolve` command runs one mutation evaluation end to end with no model
credentials using the `fixture` proposer profile against a target folder inside
a Git repository; see the README "Evolve a workflow" section.

Each `--behaviour-case <name>` is verified by `<name>.sh` in the target folder,
and every declared case must pass before promotion. Declaring a case without
wiring its check would let the required-behaviour hard gate pass without the
evidence it names, so the mapping is total by construction.

A check command that names a program by path must resolve inside the candidate
worktree, symlinks followed. A committed symlink out of the tree is recreated
faithfully by `git worktree add`, so without that guard a script that is not in
the candidate could satisfy a required behaviour. An escaping command aborts the
run rather than being recorded as a failed behaviour case, because it is a broken
setup and not an observation about the mutation.

## Documentation Graph

New Markdown should fit the existing graph instead of creating a parallel
narrative. Use specs for intended behavior, knowledge entries for durable
facts, decisions, risks and questions, ADRs for accepted architectural choices,
and wiki pages for short explanations that link back to those nodes.

The current taxonomy is in `.agents/knowledge/TAXONOMY.md`. `Q-008` tracks
whether the project needs a stronger ontology or DBpedia-style linked-data
shape as the graph grows. Until then, prefer clear local terms in the glossary,
resolved knowledge IDs, canonical edge names such as `relates_to`, and short
wiki pages over broad external vocabulary.

## Tool-unavailable Integration Fallback

Default integration is branch plus PR. If PR or GitHub tooling is unavailable,
the lead agent may skip the PR only when the user explicitly authorizes it.

PR creation works as of 2026-07-31, so the fallback does not currently apply;
`CHG-003` went through PR #1. Pushing over SSH can still fail with
`agent refused operation` when the SSH agent will not sign. Either unlock the key
with `ssh-add`, or run `gh auth setup-git` once and push over HTTPS with the `gh`
token; the stored remote can stay on SSH.

Required fallback steps:

- keep the work on a bounded branch;
- run `.agentic-template/bin/project check`, `.agentic-template/bin/project ready`
  and relevant specialized tests;
- request at least one risk-appropriate reviewer actor or subagent when agent
  tooling is available, then address findings before merge;
- if a reviewer actor times out while actor tooling is still available, retry
  with another actor or get explicit human review, then address any findings
  from that substituted review before merge;
- self-review the staged branch in code-review style after actor or human
  review findings are addressed;
- if actor review tooling is unavailable, disclose the missing actor-review
  gate, get explicit user authorization for the degraded single-lead path, and
  record the degraded fallback level and compensating validation or human review
  needed;
- record the fallback reason, validation, branch, commit, actor-review or
  substituted human-review result and lost independent review challenge in
  `HANDOFF.toon`;
- merge to `main` only after explicit user authorization;
- push `main` without force-pushing.
