# CHG-030: Live proposals carry the full replacement file

## Why

The first live generation against a real provider (neuralwatt
`glm-5.3-flash-flex`, runs `nw1` and `nw2`, 2026-11-07) showed that a live
candidate's workflow file becomes the model's diff text. The prompt asks for
"patch text"; the realiser writes that text as the whole file. In `nw1` two such
candidates were promoted, because the toy check only rejects an exact
`draft-check: skip` line, and diff text has none. The measurement ADR-0002 and
ADR-0006 slice 0 were waiting for is therefore invalid until this is fixed.

Two of six asks also chose a scope the prompt offered but the run rejects.

## Intent

Ask for the complete new file in plain words, reject diff-shaped answers
deterministically before a candidate exists, and offer the model only the scopes
the run will realise.

## Non-goals

- applying unified diffs;
- realising PROMPT or TOOL_CONFIGURATION scopes;
- usage for rejected attempts (CHG-031).

## Related knowledge

`ADR-0002`, `ADR-0006`, `PAT-004`, and the CHG-029 spec.
