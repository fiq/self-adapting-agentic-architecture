# Capability Schema

A capability is a living requirement in `specs/capabilities/<name>.toon`. It is
the durable, current statement of what the system does, updated when a change
is archived. Validated by `.agentic-template/bin/check-changes`.

## Structure

```yaml
capability:
  id: CAP-001                 # required
  name: theming               # required
  status: living              # living | deprecated
  summary: One sentence.       # required
  relates_to:                 # optional; knowledge IDs (must resolve)
    - SYS-001

requirements:                 # required, at least one
  - id: R1                    # required, unique within the capability
    requirement: The app supports a dark theme.   # required
    scenarios:                # required, at least one
      - id: S1
        when: user clicks the theme toggle
        then: the app switches to dark mode
        and: []
```

## Validation rules

- `capability.id`, `capability.name`, `capability.summary`, `capability.status`
  are present; `status` is `living` or `deprecated`.
- At least one requirement; each has a unique `id` and a `requirement`.
- Each scenario has `id`, `when` and `then`.
- Every `relates_to` value that looks like a knowledge ID resolves to an
  existing `.agents/knowledge/` entry.
