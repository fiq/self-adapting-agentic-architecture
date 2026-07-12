---
name: ci
description: Specialise CI so workflows call repository command primitives without duplicating build logic.
---

# CI

CI must call repository primitives. Do not duplicate build logic in YAML.

A suitable workflow shape is:

```text
project check
project test
project integration-test       # when applicable
project image-test             # when applicable
project compose-config         # when applicable
project infra-check            # when applicable
project ready                  # or the non-duplicating composite
```

Avoid running the same expensive tests twice when `ready` composes earlier
commands.

## Constraints

- Generic template CI must not require interactive AI tooling, MCP servers,
  Superpowers or external model access.
- Generic template CI must not automatically apply infrastructure or require
  cloud credentials.
- CI must not rebuild images or topologies already built by `image-test` or
  `compose-test`.

## Do not

- Inline build commands that duplicate `.agentic-template/bin/project`.
- Gate CI on external model availability.
- Run destructive or apply-from-CI steps from the generic template.
