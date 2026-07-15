---
name: container-build
description: Every project makes an explicit container decision; deployable services default to a tested application image.
---

# Container Build

## Policy

Every project must make an explicit container decision. Deployable services
and web applications default to a tested application image unless evidence
supports a documented exception.

Libraries, mobile apps, desktop apps and Godot projects may record
containerisation as `not_applicable` with a reason in `PROJECT_PROFILE.toon`.

## Required artifacts (when applicable)

- `Dockerfile` or `Containerfile`;
- multi-stage build where useful;
- non-root runtime user;
- meaningful `.dockerignore`;
- pinned base image or a documented update policy;
- health check where practical;
- `.agentic-template/bin/project image`;
- `.agentic-template/bin/project image-test`.

## `image-test`

`image-test` must:

1. build the image;
2. start a container from it;
3. perform a meaningful smoke or health request;
4. stop the container;
5. clean up the container and any dangling resources.

It must fail loudly on build errors, unhealthy startup, or cleanup failure.

## Profile state

Record in `PROJECT_PROFILE.toon`:

```toon
containers:
  application_image: built | not_applicable
  image_test: .agentic-template/bin/project image-test
  base_image: ...
  runtime_user: non-root
  reason: ...
```

## Do not

- Run containers as root when a non-root user is practical.
- Leave `application_image: unknown` after `/specialise`.
- Commit brittle or unpinned base images without an update policy.
- Run `image-test` without meaningful cleanup.
