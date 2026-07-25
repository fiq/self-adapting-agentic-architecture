# Structured Data Policy Schema

`PROJECT_PROFILE.toon.structured_data` records semantic format choices.

Required fields:

- `control_files.format`: `toon`
- `state_and_contracts.format`: `toon` or `s_expression`
- `rules_and_compute.format`: `toon`, `s_expression` or `none`

Defaults:

- TOON for state and contracts: readable, diff-friendly, docs-adjacent.
- S-expressions for rules and compute: compact symbolic predicates and
  transformations.
