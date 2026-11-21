# Measurements

Runs that were actually made, and what they showed. Hand-written.

> Authority stays with `ADR-0002` and `ADR-0006`, where each run is recorded.
> [Experiment memory](experiments.md) is a separate, generated page built from
> `experiments/ledger/*.toon`; this repository commits no ledger envelopes, so
> its tables are empty here.

## Live proposer on the toy fixture, 2026-11-07

Four generations of three candidates each, against neuralwatt
`glm-5.3-flash-flex`, runs `nw1` to `nw4` (`ADR-0002`, "First measurement").

| Run | Outcome |
|---|---|
| `nw1`, `nw2` | Invalid. The prompt asked for "patch text" and the realiser wrote the model's diff as the whole file. Fixed by `CHG-030`. |
| `nw3` | Three whole-file candidates, all failing the same two checks. Spread 0.00. |
| `nw4` | Three candidates, all PROMOTE at 0.99, all the identical file under three ids. Spread 0.00. |

- **What it showed:** the variant ask did not produce variation, and the old
  sameness rule ranked one answer as three (`Q-013`).
- **Fix:** `CHG-032` identifies candidates by the files their commits hold, so
  `nw4` would now rank one candidate and record two duplicates.
- **Cost seen (`ADR-0006`, slice 0):** about 380-400 input and 400-1500 output
  tokens and 10-65 s of wall clock per candidate. This is cost per candidate,
  not per successful task.
- **Rejected attempts:** in the first two live generations one ask in three was
  answered, then rejected by validation. Since `CHG-031` its usage is recorded.

## Reference answers on the multi-answer fixture

`fixtures/multi-answer-workflow` (`CHG-033`) has three hand-written answers.
Each passes every required case. Held-out cases separate them.

| Answer | Score |
|---|---:|
| best | 0.989 |
| middle | 0.911 |
| lowest | 0.833 |

- **What it shows:** the scorer can separate candidates on this target.
- **What it does not show:** that a live model will produce different answers.
  No live run on this fixture is recorded yet.
- **Margin:** the lowest clears the 0.80 threshold by about 0.033. One held-out
  failure still promotes. `RISK-010` stays open on the wider question.

## Not yet measured

- **GraphRAG benefit:** no run compares `NONE`, `VECTOR`, `GRAPH` and `HYBRID`
  retrieval with a real proposer and embedding provider (`HANDOFF.toon`,
  `ADR-0004`).
