# Runbooks

Runbooks are optional, project-specific operating procedures. Add them only
when a repeatable human or agent operation exists.

## Add runbooks for

- secrets and credential rotation;
- schema migrations and deploy ordering;
- production provisioning;
- release or rollback;
- incident response and diagnostics;
- manual validation that CI cannot prove.

## Do not add runbooks for

- one-off setup notes;
- generic cloud/provider advice with no project evidence;
- commands already covered by `.agentic-template/bin/project`;
- historical debugging narratives better suited to knowledge entries.

## Shape

Use `RUNBOOK_TEMPLATE.md`. Keep each runbook executable by a human or agent:

- prerequisites;
- exact commands;
- expected output;
- rollback or cleanup;
- validation evidence to record in `HANDOFF.toon.tests_run`;
- related knowledge IDs.
