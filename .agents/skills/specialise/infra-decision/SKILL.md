---
name: infra-decision
description: Record an explicit infrastructure-as-code decision and validation command for every project.
---

# Infrastructure Decision

## Outcome

Every project explicitly records its local topology, deployment target and
IaC status. No project is left with an implicit or unrecorded infrastructure
decision after `/specialise`.

## Required state

Record the following in `PROJECT_PROFILE.toon`:

```toon
infrastructure:
  local_topology: ...
  deployment_target: ...
  iac:
    status: required | deferred | not_applicable
    tool: ...
    validation_command: ...
    revisit_trigger: ...
```

## When a deployment target is known

- Create only the smallest useful IaC skeleton.
- Expose `project infra-check`.
- Run formatting and static validation.
- Validate in CI.
- Never automatically apply from generic template CI.
- Do not require cloud credentials for ordinary validation where avoidable.

## When the target is unknown

Record `deferred` and a concrete revisit trigger, for example:

```toon
infrastructure:
  local_topology: nix
  deployment_target: unknown
  iac:
    status: deferred
    tool: null
    validation_command: null
    revisit_trigger: deployment target selected
```

## Applicability

Libraries, mobile apps, desktop apps and Godot projects may record
`not_applicable` with a reason but must not leave the decision implicit.

## Do not

- Add cloud resources, credentials or modules without a deployment target.
- Require cloud credentials for static validation.
- Provision infrastructure from generic template CI.
