---
id: PAT-004
type: pattern
title: Prove an assertion can fail before trusting it
status: proposed
summary: A passing test is not evidence until it has been seen to fail for the reason intended. Breaking the guarded behaviour and observing the failure catches assertions that cannot fail; reading them does not.
owners:
  - lead
relates_to:
  - PAT-003
  - LRN-001
  - CON-002
review_after: 2026-11-30
---

# Prove an Assertion Can Fail Before Trusting It

Break the behaviour a new test guards, confirm the test fails, restore the file
unchanged, and record what was broken. Until then the test tells you nothing: a
green result is equally consistent with a correct implementation and with an
assertion that cannot fail.

## Why this is not a matter of care

On 2026-08-22 five assertions in this repository could not fail. Four were
written by the lead, and one of those was written the same day, after several
hours spent finding exactly that defect in other people's work. Every one was
caught by mutation. None was caught by reading, including by reviewers
specifically briefed to look for weak tests.

| Assertion | Why it could not fail |
|---|---|
| CHG-011 `S4` route selection | selected `fixture`, already the session default, so an implementation ignoring route selection passed |
| CHG-011 `S1` catalogue inspection | asserted transcript text with no proposer spy |
| CHG-014 fail-wins on duplicate ids | listed the failing result last, so last-write-wins returned the same answer |
| CHG-016 CLI benchmark test | budget of `1e-7` drove the ratio to zero whichever direction the quantity pointed, proving a discard happened and nothing about why |
| CHG-019 inverted `S9` | asserted `containsKey` when the scorer writes that key whatever the outcome |

Two more were found the same way in a subagent's work and in a reviewer's
suggestion, and one reviewer suggestion would have codified the opposite of the
promotion boundary had it been applied without running it.

## The shapes that recur

- checking a key is present when the key is written regardless of outcome;
- pinning a decision without pinning the reason for it;
- a fixture that already satisfies the assertion before the code runs;
- ordering that lets a last-write-wins bug produce the expected answer;
- a magnitude so extreme it passes for a direction-agnostic reason.

## What this does not mean

It does not mean mutating every test. It means a *new or changed* assertion that
will be cited as evidence, especially one guarding a gate, a decision or an audit
record. Nor does it replace review: reviewers found defects mutation missed, and
mutation found defects three independent reviewers missed. They fail differently,
which is why both are worth the cost.
