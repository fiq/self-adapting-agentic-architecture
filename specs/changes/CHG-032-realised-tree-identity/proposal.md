# CHG-032: A generation ranks one candidate per distinct realised tree

## Why

Live run `nw4` (2026-11-07) produced three candidates with different ids that all
realised the same file. The generation ranked them as three, with spread 0.00.
The `(id, patch)` rule from CHG-029 compares what the proposer claimed, so it
cannot see this. Q-013 named the realised tree as the destination; this builds it.

## Intent

- Identity is the candidate commit's Git tree.
- The first candidate with a tree ranks. Later ones are duplicates: recorded,
  shown, not ranked.
- A repeated `(id, patch)` becomes a duplicate instead of aborting the run.

## Non-goals

- Skipping evaluation of a duplicate.
- Cross-run or semantic sameness.

## Related knowledge

`Q-013`, `ADR-0002`, `CHG-026`, `CHG-029`, `PAT-004`.
