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

On 2026-08-22 and 23, six assertions in this repository could not fail, one could fail but
proved something other than what it claimed, and one passed because its subject
was never executed. Three of the four
were written by the lead, and one of those was written the same day, after
several hours spent finding exactly that defect in other people's work. Every one
was caught by mutation. None was caught by reading, including by reviewers
specifically briefed to look for weak tests.

An independent review corrected this entry's own framing: the CHG-016 row below
was first recorded as an assertion that could not fail, which overstated it. That
correction is itself the point — the claim survived being written, reviewed once,
and merged before anyone checked it.

| Assertion | Why it could not fail |
|---|---|
| CHG-011 `S4` route selection | selected `fixture`, already the session default, so an implementation ignoring route selection passed |
| CHG-011 `S1` catalogue inspection | asserted transcript text with no proposer spy |
| CHG-014 fail-wins on duplicate ids | listed the failing result last, so last-write-wins returned the same answer |
| CHG-016 CLI benchmark test | could fail if the wiring were absent, but its budget of `1e-7` drove the ratio to zero whichever direction the quantity pointed, so it proved a discard happened and nothing about why. Insufficient rather than incapable |
| CHG-019 inverted `S9` | asserted `containsKey` when the scorer writes that key whatever the outcome |
| CHG-025 digest stability | unreachable: an earlier assertion in the same shared suite failed first for every input that could have reached it |

Two more were found the same way in a subagent's work and in a reviewer's
suggestion, and one reviewer suggestion would have codified the opposite of the
promotion boundary had it been applied without running it.

## A test can be disconnected rather than weak

CHG-021 added a seventh case, and it is a different kind. An acceptance test drove
the CLI with a failing safety probe and asserted the candidate still promoted. It
passed. Mutating the code that withholds probes from the gate changed nothing,
which is what exposed it: the probe's script was never executed at all, so the
objective read zero from absence rather than from measurement, and the assertion
was satisfied for a reason unrelated to what it claimed.

The assertion was fine. The mechanism was missing. That is why the rule mutates
the mechanism and not only the assertion: if breaking the code under test changes
nothing, the test is not weak, it is disconnected, and the two need different
fixes.

## An assertion can be unreachable rather than weak

CHG-025 added an eighth case with a mechanism none of the others share. A shared
conformance suite asserted three things about a digest in sequence: that a
formatting-only edit is identical, that a changed statement is not, and that
reading the same source twice gives the same answer. The third could not fail.
Every frontend built to violate it — one whose digest drifted per call — violated
the *first* assertion too, and AssertJ stops at the first failure. The negative
test written for stability was passing on the formatting assertion, and it pinned
only `AssertionError` with no message, so nothing revealed the substitution.

What makes this worth its own section is that the change's own evidence strategy
could not have caught it. That change proved strictness with six deliberately
wrong implementations, each failing a named assertion. A wrong implementation
fails an unreachable assertion exactly as readily as a reachable one, because
something else fails first and the suite is red either way. Six negative tests
passing says the suite rejects those six frontends. It says nothing about which
assertion did the rejecting.

Deleting the assertion is what found it: the suite stayed green. The fix needs an
input that satisfies every earlier assertion and violates only this one — here, a
frontend that answers correctly for every distinct source and differently only on
a re-read — and the negative test must pin the assertion's own message, or it can
silently start passing on a different failure again.

Two rules follow. Pin the message, not just the exception type, in any test that
asserts a specific assertion fires. And when assertions are ordered in a shared
routine, deleting each one and observing which test goes red is the only cheap
way to learn that each is reachable.

## The shapes that recur

- checking a key is present when the key is written regardless of outcome;
- pinning a decision without pinning the reason for it;
- a fixture that already satisfies the assertion before the code runs;
- ordering that lets a last-write-wins bug produce the expected answer;
- a magnitude so extreme it passes for a direction-agnostic reason;
- a test whose subject never runs, so the assertion is satisfied by absence;
- an assertion later in a sequence that nothing can reach, because an earlier one
  in the same routine fails first for every input that would have violated it.

## What this does not mean

A passing mutation shows the assertion is sensitive to the change you made. It
does not show the oracle is correct or the semantics are the intended ones, which
is why this does not replace review.

It does not mean mutating every test. It means a *new or changed* assertion that
will be cited as evidence, especially one guarding a gate, a decision or an audit
record. Nor does it replace review: reviewers found defects mutation missed, and
mutation found defects three independent reviewers missed. They fail differently,
which is why both are worth the cost.
