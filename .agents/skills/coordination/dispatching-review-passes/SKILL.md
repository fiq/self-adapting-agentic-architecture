---
name: dispatching-review-passes
description: Run several independent review passes at once without them interfering, and get a usable verdict back from each.
---

# Dispatching Review Passes

## Outcome

Turn a decision to review into several passes that actually ran, actually stayed
independent, and each actually concluded. This is the execution half of review;
it assumes the decision has already been made.

## What this does not cover

- **How many passes, and aimed at what** — `review-routing`. Decide that first.
- **What to put in each brief** — `context-packet`. A reviewer gets a packet, not
  a repository.
- **What to do when a pass returns no verdict** — `review-routing`, which carries
  the recovery ladder.

## When to trigger

You have decided a change needs more than one independent pass, and you are about
to launch them.

## The sequence

Each step has a trap under it. The traps are the reason this is a procedure
rather than a paragraph.

### 1. Quiesce the tree first

Commit or stash everything. Reviewers read the working tree, so a tree you are
still editing produces findings about your edits rather than about the change.

*If you need to verify something while passes are running*, use a detached
worktree at the reviewed commit rather than the tree they are reading.

### 2. Detect what is installed

Do not name a harness from memory; this repository is a template and runs on
machines that are not the author's.

    .agentic-template/bin/project check     # reports tooling status
    command -v codex opencode claude gemini # what is on PATH
    opencode models                         # what a harness has configured

Prefer two providers over two runs of one. Different providers disagree in ways
one provider twice does not, and that disagreement is the product.

### 3. Write each brief to its own file, as its own step

    cat > BRIEF-A.md <<'BRIEF'
    ...
    BRIEF
    test -s BRIEF-A.md || exit 1

**Trap.** Do not build the brief and launch the agent in one compound command. If
any part is refused, the file is never written, the agent receives an empty
prompt, reads the startup contract, answers "what would you like to work on", and
exits zero. That looks like a hijacked agent and is a shell bug in the caller.
Check the file exists and is non-empty before launching, and before blaming the
tool.

### 4. Give each brief a persona and a region

A brief without a lens produces generic review. Compose the lens from what the
repository already defines:

- `.agents/team/*.md` — persistent roles with continuing responsibility:
  architect, tech-lead, product-owner, domain-expert, knowledge-curator.
- `.agents/subagents/*.md` — bounded reviewer roles: red-team-reviewer,
  evidence-checker, test-reviewer, security-reviewer, performance-reviewer.

Name one or two in the brief, point at the file, and say what that lens owns. A
combination usually beats a single role: *architect + tech-lead* covers
boundaries and implementation coherence; *red-team + test-reviewer* covers whether
the tests prove what they claim; *evidence-checker + domain-expert* covers whether
recorded claims match what the code writes.

Then give the pass a **region**, and name the regions belonging to the others, so
the passes do not converge. End every brief with the verdict line
`review-routing` requires.

**Ask directly about anything you already suspect is wrong.** A brief that names
the doubt gets a sharper answer than one hoping the reviewer stumbles onto it.
Seeding a pass with a known-hard example of the defect shape you want hunted
works: tell it what already slipped through and why.

### 5. Launch read-only, in the background, with a sentinel

    nohup bash -c "<harness read-only invocation> \"\$(cat BRIEF-A.md)\" \
        > A.out 2>&1; echo \"exit=\$?\" >> A.out" >/dev/null 2>&1 &

**Read-only is an invariant to enforce, not to request.** A reviewer given write
tools mutates the change the others are reading, and the passes silently stop
being additive. A reviewer merely *asked* not to write can still try, be refused
mid-flight, and stop without ever reaching a verdict.

**Poll for the sentinel, not for the harness's own status.** The harness has
reported success for a process that failed.

### 6. Enforce independence while they run

Watch the working tree rather than trusting that read-only held:

    while true; do
      d=$(git status --porcelain); [ -n "$d" ] && { echo "TREE DIRTY: $d"; exit 1; }
      ...check sentinels, exit 0 when all present...
    done

If it stays clean for the whole review, the passes were genuinely additive and
you can say so rather than assume it.

### 7. Collect, and count verdicts before consolidating

Confirm each pass produced a verdict line. Exiting zero is not a verdict. Do not
begin consolidation one pass short — recover it first via `review-routing`.

## Then consolidate

Consolidating is the work, not the reviewing. See `review-routing` and `PAT-003`:
deduplicate, adjudicate contradictions rather than averaging them, verify every
finding against the code including the ones you expect to be right, reproduce at
least one mutation proof before repeating any as evidence, and apply fixes
serially in one place.

**Passes disagreeing on the merge verdict is not a problem to resolve by
averaging.** Regions differ; a blocker in one region stands regardless of two
clean verdicts elsewhere.

## Cost

Reviews are read-only, so they compose and can run at once. That makes the wall
clock cheap and the tokens real. Spend them where independent challenge pays: aim
each brief at ground no earlier pass covered. A pass that re-derives context you
already hold is the expensive way to get an answer you could have written
yourself.

## Evidence this works

Three passes over one change in this repository, three regions, two providers:
**every finding came from exactly one pass, and no two passes overlapped on any
finding.** One returned a blocker the other two did not see, on ground only its
brief covered. That is the case for different briefs over more reviewers sharing
one, stated as measurement rather than principle.
