# Toy Workflow Fixture

A minimal target folder for `saaa evolve`, used by the acceptance test and by
anyone wanting to see one generation run without model credentials.

| File | Role |
|---|---|
| `workflow.txt` | the artifact being evolved |
| `check.sh` | a behaviour case; fails on the baseline, passes on the candidate |
| `.saaa/fixture-mutation.txt` | the canned mutation, summary on line one |
| `journal.md` | written by a run; not committed |

The baseline deliberately fails `check.sh`. The fixture mutation fixes it, so a
run demonstrates a real DISCARD-to-PROMOTE transition rather than scoring a
constant.
