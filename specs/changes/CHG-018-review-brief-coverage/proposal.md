# CHG-018: Aim each review brief at unexamined ground

## Why

`PAT-003` already says to prefer different briefs over more reviewers. CHG-016
showed why, and sharpened it into something more useful than a preference.

That change took three independent passes and each found something the previous
had missed. Not diminishing returns — coverage. Pass one examined the CLI flags,
pass two the fix and its tests, pass three the deterministic layer neither had
touched, and each defect sat in exactly the region the previous brief did not
cover. Three passes over the same surface would likely have agreed with each
other and found none of them.

## What

One clause added to the existing rule in `AGENTS.md`, guarded. `PAT-003` gains
the evidence table and the decision procedure it implies: when weighing another
review, ask which region is still unexamined, and if the answer is none, stop.

## Deliberately not

- a required number of review passes. The evidence supports choosing briefs, not
  counting rounds;
- a new section anywhere. The rule already has a home in `AGENTS.md` and
  `PAT-003`, and CHG-017 was withdrawn for adding guidance beside guidance that
  already existed.

## Confidence

One change's evidence, consistent with PR #15 where two differing briefs found
disjoint problems. Two instances is not a law, and `PAT-003` says so where it
records this.

## Relates to

PAT-003, CHG-016.
