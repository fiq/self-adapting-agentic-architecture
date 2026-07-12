---
name: infra-local-compose
description: Specialise local Compose topology with health checks, pinned images and deterministic validation.
---

# Infrastructure: Local Compose

## When to use Compose

Use Compose when:

- the runnable demonstration contains multiple services;
- local execution requires external dependencies;
- a production-like integration risk is cheap and meaningful to exercise.

When none of these apply, record `compose_required: false` with a reason in
`PROJECT_PROFILE.toon`.

## Required when applicable

- pinned images (explicit tags, not `latest`);
- health checks;
- health-aware dependency ordering (`depends_on` with `condition: service_healthy`);
- named services;
- documented ports;
- no committed secrets;
- `docker compose config` validation;
- a bounded startup smoke test;
- deterministic cleanup.

## Commands

Expose:

- `.agentic-template/bin/project compose-config`
- `.agentic-template/bin/project compose-test`

`compose-config` must run `docker compose config` and fail on invalid
topology.

`compose-test` must:

1. validate configuration with `compose-config`;
2. bring up the topology with `--wait` or an equivalent health-aware start;
3. perform a bounded startup smoke request against a meaningful service;
4. tear down with `docker compose down --volumes`;
5. fail loudly on unclean exit.

## Testcontainers

Prefer Testcontainers (or an equivalent lifecycle-managed approach) for
integration-test dependencies where that produces a faster and more reliable
test loop than standing a full Compose topology for every test run.

## Do not

- Use `latest` tags for pinned services.
- Commit secrets, tokens or `.env` files with real values.
- Leave services without health checks when a health probe is practical.
- Skip cleanup in `compose-test`.
