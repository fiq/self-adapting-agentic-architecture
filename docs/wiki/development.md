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

`component-test` is the first failing outside-in test for `CHG-001`. Keep
LangChain4j behind `adapters/langchain4j` and keep deterministic decisions out
of model authority.

## Tool-unavailable Integration Fallback

Default integration is branch plus PR. If PR or GitHub tooling is unavailable,
the lead agent may skip the PR only when the user explicitly authorizes it.

Required fallback steps:

- keep the work on a bounded branch;
- run `.agentic-template/bin/project check`, `.agentic-template/bin/project ready`
  and relevant specialized tests;
- request at least one risk-appropriate reviewer actor or subagent when agent
  tooling is available, then address findings before merge;
- if a reviewer actor times out while actor tooling is still available, retry
  with another actor or get explicit human review before merge;
- self-review the staged branch in code-review style after actor findings are
  addressed;
- if actor review tooling is unavailable, disclose the missing actor-review
  gate, get explicit user authorization for the degraded single-lead path, and
  record the degraded fallback level and compensating validation or human review
  needed;
- record the fallback reason, validation, branch, commit, actor-review result
  and lost independent review challenge in `HANDOFF.toon`;
- merge to `main` only after explicit user authorization;
- push `main` without force-pushing.
