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
- self-review the staged branch in code-review style;
- record the fallback reason, validation, branch, commit and lost independent
  review challenge in `HANDOFF.toon`;
- merge to `main` only after explicit user authorization;
- push `main` without force-pushing.
