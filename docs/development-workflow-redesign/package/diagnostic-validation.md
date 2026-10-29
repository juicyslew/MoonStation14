# Additional automated validation

Request relevant automated checks whenever useful during the task, including before feature completion or without a preceding failure. Give a concrete rationale, select focused checks, batch related captures where useful, and reuse still-valid evidence rather than repeating unchanged runs. There is no diagnostic run budget or routine owner-approval step.

State the concrete question each selected check answers, using its existing rationale field. Start with the cheapest check capable of answering it. `DIAGNOSTIC` is not permission to run native, packaging, or a full gate automatically, nor does a failing check itself justify escalation. Unit-only diagnostics need no GameTest, readiness, or package check; select only readiness categories actually implicated. Select all categories only when the question truly affects all of them. Keep trusted selectors and prototype/runtime coverage intact. Use a full suite only for a genuinely broad integration question; preserve existing full-suite requirements for broad changes and MAIN merges. Read existing bounded failure evidence before rerunning. Diagnostic purpose does not enable debug logging: normal GameTest logging remains INFO; use only the explicit `-Pms14ValidationDebug=true` flag for an active investigation that needs it, and describe the specific confirmed log field it investigates.

## Author actions

1. Obtain your authenticated context with the existing author `status` tool. Prepare and `register_scope` the normal bound policy and fresh evidence for the intended submission key. Keep scratch request files in ignored build output.
2. Prepare the normal strict submission envelope with these diagnostic differences:
   - `purpose: "DIAGNOSTIC"`, `implementation_complete: false`.
   - Nonempty `diagnostic_rationale` and a typed `diagnostic_plan` with `kind: "diagnostic"`, nonempty checks, and `manual_steps: []`.
   - Request `manual_steps: []`, `check_packs: []`; keep all trusted policy criterion IDs/evidence bindings, but use criterion status `"DIAGNOSTIC"`, not `"COMPLETE"`.
   - Plan criteria may be a relevant subset of trusted policy criteria; checks must cover those plan criteria. Use the existing gate-plan fields (`title`, `acceptance_criteria`, `fixture`, `references`, `checks`). Fixture/reference paths must exist and are frozen normally.
   - Compute `plan_digest` from `workflow_check_packs.resolve_diagnostic_plan(request, repo=repo, policy=policy)` using `workflow_store.digest`. Source/policy digests, author identity, provenance and evidence requirements remain unchanged.
3. For typed GameTest requests, use exactly `-Pms14GameTestDir=build/workflow-fixtures/<input_digest first12>` and capture the corresponding `build/workflow-fixtures/<input_digest first12>/logs/latest.log`. The generic `build/gametest-tags-fixture` path is manual-check guidance, not a valid managed request path. `submit` through the existing tool. Admission returns `WAIT_FOR_RESULT`; inspect this diagnostic by its returned `run_id`, since it is not the feature head. Never supply `replaces_run_id` for a diagnostic.
4. Review the selected check results, logs and captured artifacts. Acknowledge the delivered result with the existing exact seven-field `acknowledge_result` contract. Diagnostic PASS uses `REVIEW_DIAGNOSTIC_RESULTS`, not an owner-testing action.

All heavy execution uses the coordinator's existing serialized lane and immutable artifact archive. Do not launch independent server/client validation while waiting. Changed input needs fresh scope/submission keys; exact uncertain retries keep their existing keys. Diagnostic results do not replace a feature verdict, trigger AI feature reviews, or authorize owner acceptance.

A diagnostic `ERROR` is immutable and does not imply author fault; inspect status and logs. Once cleanup is confirmed and resources/scopes are settled, correct request/configuration and register a fresh diagnostic with fresh scope/submission keys without owner approval. This does not replace a feature run: never use `replaces_run_id`, retire a feature head, or seek manual owner retirement. A historical diagnostic ERROR may retain `WAIT_FOR_RECOVERY` in its immutable event; current status/ACK guidance is authoritative after settlement. `PREPARE_FRESH_DIAGNOSTIC` is guidance only; it does not execute checks. If cleanup is unresolved or unknown, wait for recovery while retaining the original identifiers.

## Agent-02 population capture example

For the existing nonhostile-animal launcher, one typed command check can use this argv (choose `Mixed`, `Parrots`, or `Kobolds`):

```json
["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "tooling/validation/connected_join.ps1", "-PopulationScenario", "Mixed", "-WallTimeoutSeconds", "900"]
```

Set check `kind: "command"`, a relevant `rationale` and `covers`. Declare diagnostic-only `artifact_globs` for fresh outputs, for example:

```json
[
  "build/reports/nonhostile-animals/population-profiles/mixed-*/*.json",
  "build/reports/nonhostile-animals/population-profiles/mixed-*/*.jfr",
  "build/reports/nonhostile-animals/population-profiles/mixed-*/*.log"
]
```

Each glob must produce fresh files; old unchanged matches do not establish success. Use the matching lowercase scenario prefix for other scenarios. Collected bytes are frozen through the existing CHECK receipt/artifact owner.

Do **not** add `-PopulationSustained` to claim unattended movement/jump coverage: its analyzer explicitly requires operator-driven horizontal travel and grounded jumps. Non-sustained mode supplies stationary automated population evidence, not that additional coverage. A visible client window alone does not make a check manual. Tests requiring active owner actions or judgment remain manual, after validation, directly with the coding agent; the website is a status view, not owner acceptance.
