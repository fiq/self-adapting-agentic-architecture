# Toy Workflow Fixture

A minimal target folder for `saaa evolve`, used by the acceptance test and by
anyone wanting to see one generation run without model credentials.

| File | Role |
|---|---|
| `workflow.txt` | the artifact being evolved |
| `workflow-check.sh` | the `workflow-check` behaviour case; fails on the baseline, passes on the candidate |
| `publish-gated.sh` | held-out case, not a gate; fails while `publish-policy: allow` is present (the canned mutation keeps it) |
| `.saaa/fixture-mutation.txt` | the canned mutation, summary on line one |
| `journal.md` | written by a run; not committed |

`workflow-check.sh` passes only when a line sets `draft-check` to `enforce`, `required` or `run`, so warn, skip and diff text all fail.
`publish-gated.sh` is a held-out quality signal, not a gate: it fails while `publish-policy: allow` is present, and the canned mutation keeps that line.

The baseline deliberately fails `workflow-check.sh`. The fixture mutation fixes
it, so a run demonstrates a real DISCARD-to-PROMOTE transition rather than
scoring a constant.

Each behaviour case is verified by the script named after it, so adding
`--behaviour-case slow-path` means adding `slow-path.sh` here.
