# ACP interoperability walkthrough

This is what using SAAA with a real external agent looks like today.

SAAA is the control plane around an agent. The external engine proposes a
mutation; SAAA supplies the bounded request and disposable proposal workspace,
then deterministic code owns validation, Git realization, behavior checks,
fitness scoring and promotion recording.

```text
developer command
       |
       v
  saaa-evolve --profile acp
       |
       +--> local ACP process (OpenCode, Goose, Codex, Claude, ...)
       |       returns one mutation proposal
       |
       +--> disposable proposal workspace
       +--> deterministic validation -> Git candidate -> checks
       +--> fitness score -> PROMOTE or DISCARD
```

## The concrete OpenCode experiment

On 2026-08-14, the local machine had:

| Engine | Local evidence | ACP result |
|---|---|---|
| OpenCode 1.18.13 | `opencode acp --help` exposes an ACP server | exercised; session did not complete before the SAAA deadline |
| Goose | command not installed | not exercised |
| Codex CLI 0.146.0 | installed; help exposes `mcp-server` and `app-server`, not an ACP command | not exercised as ACP |
| Claude Code 2.1.222 | installed; help exposes normal/print modes, not an ACP command | not exercised as ACP |

The exact SAAA invocation was:

```sh
nix develop --command gradle :cli:installDist --no-daemon

SAAA_ACP_COMMAND=opencode \
SAAA_ACP_ARGS='acp' \
SAAA_ACP_WALL_CLOCK_MILLIS=180000 \
./modules/cli/build/install/saaa/bin/saaa saaa-evolve fixtures/toy-workflow \
  --profile acp \
  --behaviour-case workflow-check \
  --max-lines 20 \
  --task 'Replace the skipped draft check with an enforced draft check. Return one mutation proposal only.'
```

The command reached the normal retrieval phase, selected the ACP-backed
proposal path, and ended with:

```text
retrieval NONE  config=retrieval-config-v1 evidence=0 tokens~0
java.lang.IllegalStateException: agent invocation did not complete: TIMED_OUT:
java.util.concurrent.TimeoutException: Did not observe any item or terminal
signal within 180000ms ...
```

This is a useful result, not a successful mutation run. It proves that the
configured `opencode acp` process is selected by SAAA and that the timeout is
reported before candidate creation. It does not prove that this particular
OpenCode installation, provider configuration or model can complete a mutation
proposal through ACP.

## What SAAA controls

The `acp` profile is explicit. It does not select the “best” agent and it does
not let the agent choose whether its own result is promoted.

| Control | Current behavior |
|---|---|
| Process | `SAAA_ACP_COMMAND` and whitespace-separated `SAAA_ACP_ARGS` |
| Workspace | temporary disposable proposal workspace containing the baseline text; cleaned after invocation |
| Output | ACP text is capped at 65,536 characters and parsed as one mutation envelope |
| Time | `SAAA_ACP_WALL_CLOCK_MILLIS`, default 120,000 ms |
| Request budget | token, credit and retry allowances are configurable and passed as policy evidence |
| Usage evidence | ACP currently provides wall-clock duration; token and credit consumption are not reported |
| Authority | deterministic validation, candidate realization, checks, fitness and decision remain in SAAA |

The agent is on the proposal side of the boundary:

```text
agent output: proposal -> deterministic SAAA authority
                          /       |       \
                      validate  check   score/decide
                                              |
                                       PROMOTE/DISCARD
```

## How to try another engine

If an engine exposes an ACP-compatible stdio server, point the same profile at
it:

```sh
SAAA_ACP_COMMAND=<engine-command> \
SAAA_ACP_ARGS='<stdio-server-arguments>' \
SAAA_ACP_WALL_CLOCK_MILLIS=120000 \
./modules/cli/build/install/saaa/bin/saaa saaa-evolve <target> \
  --profile acp --behaviour-case <check> --max-lines 20
```

Keep the first run small and use a target with a committed behavior check. A
successful run should show a proposal, candidate, check result, score and
`PROMOTE` or `DISCARD`. A timeout or malformed response should stop before the
candidate line appears.

Arguments are whitespace-separated; shell quoting is not interpreted by
`SAAA_ACP_ARGS`. Use a wrapper script when an engine needs arguments containing
spaces or more elaborate setup.

## What remains to do

1. Diagnose the OpenCode no-response case with a minimal standalone ACP
   handshake and explicit provider/model configuration.
2. Add an opt-in smoke fixture for a known working OpenCode ACP setup, without
   putting credentials or network dependence in normal CI.
3. Add concrete adapters or documented protocol bridges for Codex and Claude
   once their ACP/app-server interfaces are selected; Goose remains pending
   local installation.

Until then, ACP support is correctly described as partial: the transport and
SAAA control-plane integration are implemented, but real engine
interoperability is environment-dependent and must be demonstrated per engine.

Related implementation and policy: [CHG-009](../specs/changes/CHG-009-acp-agent-harness/change.toon),
[CHG-010](../specs/changes/CHG-010-live-agentic-harness-loop/change.toon), and
[the architecture wiki](wiki/architecture.md).
