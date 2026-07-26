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
