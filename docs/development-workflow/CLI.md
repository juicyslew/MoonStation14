# Agent workflow CLI reference

This is the administrative/debug reference; normal agents use the role-bound workflow MCP tools after their scoped binding is enabled. Publication/runtime verification is tracked in [HANDOFF.md](HANDOFF.md). Use the deployed shared CLI `python ../MoonStation14-shared/tooling/agent_workflow/workflow.py` after publication, or MAIN development entrypoint `python tooling/agent_workflow/workflow.py` before it. Installed worktree shims preserve cwd and arguments. Ask `--help` for the current exact schema when uncertain. No credentials, global OpenCode settings, game sources, Git state, or competing queue are changed by ordinary workflow use. The default exchange is shared under the sibling Asset Building Workspace; isolated fixtures may use `--asset-workspace DIR`.

## Asset request, delivery, review, import

Requests identify scope, runtime/source pin, assets, dependencies and context. Example:

```json
{"id":"airlock-r1","title":"Airlock panel","brief":"One bounded correction","scope":"art only","target_runtime":"Java 1.21.1 / NeoForge 21.1.224","source_pin":"c9df5ef... or unverified","asset_ids":["airlock"],"dependencies":[],"context":["docs/brief.md"]}
```

`request --from-json request.json --context-root .` validates and copies contexts with hashes. `claim ID --agent NAME` allows one claim. Corrections use a new request/revision; never overwrite a published delivery.

Only the flat manifest shape below is supported; all rows are required and transferred as a complete declared group:

```json
{"files":[{"file":"export/resourcepack/assets/.../model.json","destination":"src/main/resources/assets/.../model.json"}]}
```

It does not prove semantic completeness, dependencies, or fidelity: use package read-only validators and provenance/binding/source records. Nested `items[]` wearable/equipment manifests, null geometry destinations, bone meshes, frame geometry, OBJ/MTL remaps and transforms are unsupported and blocked. Preview safely before delivery:

```powershell
python tooling/agent_workflow/workflow.py plan-manifest `
  --manifest "../Asset Building Workspace/assets/Structures/Doors/Airlocks/compact_straight_bottom_r2/export/manifest.json" `
  --source-root "../Asset Building Workspace/assets/Structures/Doors/Airlocks/compact_straight_bottom_r2" `
  --target .
```

Delivery requires a claim, full manifest, explicit revision, provenance, editable project(s), and real gallery image paths:

```powershell
python tooling/agent_workflow/workflow.py deliver airlock-r1 `
  --manifest package/export/manifest.json --source-root package --revision r1 `
  --provenance README.md source/manifest.json --editable authoring/door.bbmodel `
  --review-images index.html previews/review.png
```

Provenance/editable/gallery are metadata sidecars, not runtime destinations. Sources, manifest and metadata are saved with hashes/delivery digest; changes make the delivery stale. Independent reviewer JSON binds request snapshot, digest, reviewer, decision, findings and viewed-image hashes, e.g.:

```json
{"request_id":"airlock-r1","snapshot_key":"<request snapshot sha256>","delivery_digest":"<recorded sha256>","reviewer":"reviewer-2","decision":"ready_for_owner","findings":["..."],"viewed_images":[{"path":"review.png","sha256":"<image sha256>"}]}
```

`review ID --reviewer NAME --result needs_changes|ready_for_owner --report report.json` verifies bindings, bounded report, reviewer distinct from claimant, and declared PNG hashes/signature/dimensions. Hashes do not prove inspection or truth. `import-assets ID --target WORKTREE [--owner-receipt receipt.json] [--plan-out plan.json]` is dry-run. A receipt must bind request, accepted decision, and delivery digest:

```json
{"request_id":"airlock-r1","decision":"accepted","delivery_digest":"<exact digest>"}
```

```powershell
python tooling/agent_workflow/workflow.py import-assets airlock-r1 --target . --plan-out preview.json
python tooling/agent_workflow/workflow.py import-assets airlock-r1 --target . --owner-receipt owner.json --plan-out accepted-plan.json
python tooling/agent_workflow/workflow.py import-assets airlock-r1 --target . --owner-receipt owner.json --plan accepted-plan.json --apply
```

Only ready independent review plus exact owner receipt permits apply. Plans bind target prior hashes and receipt; changed targets/sources/reports/receipt require a fresh post-acceptance plan. Overwrites require `--allow-overwrite` at both plan and apply. All paths preflight before writes; directories/non-directory parents conflict. Copy OS errors create `import_attempt.json` with copied/failed/not-attempted paths; earlier copies may remain, with no rollback. Reconcile manually. No deletes, staging, or merges.

## Coding gate commands

The author owns the plan file and edits it before submission; submit reads and freezes its contents into the candidate. Tools do not generate or maintain author plans. A submitted plan is immutable: make changes in a new file (for example `gate-plan-r27.json`) and submit a fresh candidate/revision; editing the old file does not update that candidate. Plans express check intent/coverage and fixture data, never worker deadlines, retry or cleanup mechanics. Coding submissions require explicit safe `--sprint`; candidate plan JSON requires `title`, `kind` (`sprint|feature|bugfix|docs|art|other`), `acceptance_criteria`, existing repo-relative `fixture`, `manual_steps`, and checks (`argv`, `rationale`, `covers`). Checks may declare `kind` (`command|build|junit|readiness|gametest|launch-smoke`); recognized Gradle test/readiness tasks need matching typed evidence. Readiness uses exact selectors, builds declare artifact paths, launch-smoke declares marker/log. Documentation-only plans may omit checks only with `no_checks_rationale`.

Diagnostic-only is solely for a genuinely lead-owned native check needed to implement/diagnose not-yet-ready work; it is not readiness review, owner approval, or a way around routine author checks/broad-validation policy. Reuse an existing fixture/title/check plan. Set `kind` to `diagnostic`, put the diagnostic question(s) in `acceptance_criteria` (questions, not acceptance assertions), provide the exact serialized checks, and set `manual_steps` to `[]`. `--steps` must explicitly say `Diagnostic only; no owner/manual readiness`.

Author commands (use the installed `workflow.py` CLI; the global `--repo` option precedes the command and may be omitted to use the current repository):

```powershell
python tooling/agent_workflow/workflow.py --repo . submit ID --agent NAME --sprint SPRINT_ID `
  --base-ref main --build-identity TEXT --steps "Diagnostic only; no owner/manual readiness" `
  --unverified TEXT --gate-plan diagnostic-plan.json --diagnostic-only
python tooling/agent_workflow/workflow.py gate-request ID --origin-session SESSION_ID
```

The dispatched integration lead—not the author—runs the serialized check:

```powershell
python tooling/agent_workflow/workflow.py gate-validate ID --actor integration-lead
```

`gate-validate` returns a job ID and state, not a test result. The observer delivers diagnostic completion with bound receipt/findings even if checks fail; no `gate-report`, `gate-finish`, manual-review entry or readiness approval. Diagnostic snapshots cannot be promoted. Implement, then submit a fresh readiness candidate with manual-ready steps. Dispatch/operational policy is in the [canonical contract](README.md).

The shared policy and manual-ready boundary are in [README.md](README.md). Process/prose work does not enter this gate.

```powershell
python tooling/agent_workflow/workflow.py submit ID --agent NAME --sprint SPRINT_ID `
  --base-ref main --build-identity TEXT --steps TEXT --unverified TEXT --gate-plan plan.json `
  [--evidence PATH ...]
python tooling/agent_workflow/workflow.py gate-request ID [--origin-session ID]
python tooling/agent_workflow/workflow.py gate-preflight ID
python tooling/agent_workflow/workflow.py gate-validate ID --actor integration-lead
python tooling/agent_workflow/workflow.py gate-report ID --actor integration-lead `
  --result ready_for_owner `
  --finding 'src/path/File.java:ChangedSymbol; focused tests cover changed path; parity matches resolved prototype default' `
  [--scoped-review review|fidelity-reviewer] --finish
```

The worker owns maximum deadlines and cleanup; callers cannot override ordinary/server timeouts, wait duration or central worktree policy. Safeguards cannot be disabled. For GameTest, the running server completes its whole batch so all failures are collected; it is not stopped on an individual test failure. Once that batch completes unsuccessfully or without required evidence, later unstarted checks are skipped. Do not infer readiness from check passes alone. Bound receipts, progress and explicit interruption reasons are delivered by the observer; interrupted checks are not verdicts and the same job/fixture must not be blindly rerun. Quarantine only actual unknown cleanup, never based on mere interruption; agent confirmation cannot clear a live PID. This is coordination, not a sandbox; only trusted plans may run. Candidate freshness is checked around execution. For job states and what may be requested, use `--help`; requests may identify candidate/content, not execution mechanics.

For commands run from neutral worktree 05, prefix the command with `--asset-workspace "../Asset Building Workspace"` to select the existing exchange. The integration lead invokes `gate-scope-review ID` from 05; the tool roots its read-only reviewer in the candidate author's worktree. Do not broaden MAIN read roots. Visual ready reports require `--scoped-review review|fidelity-reviewer`; exact candidate/snapshot/report and copied/original image hashes are checked. The report attributes visual inspection to that reviewer, not the lead. Use the report path returned by the CLI with `gate-finish ID --result RESULT --report PATH`; finalization sends an idempotent short notice. `gate-deliver ID --actor integration-lead` retries notification only. Dispatch uses the authenticated existing OpenCode CLI/service; uncertain admission or idle session is blocked, not approval. No raw HTTP/backend or credentials. Timeout/pending is neither pass nor rejection.

`status` is recorded metadata, not freshness verification. `status --candidate ID` retrieves an exact candidate; add `--verify` only to check that candidate's source/publication freshness. Global `status --verify` may check all historical sources and be expensive. `status --candidate ID` remains useful if a report read is denied; denial alone says nothing about review status. Owner `candidate-decision ID --owner-receipt owner.json` records explicit owner disposition separately from technical result. Receipt binds candidate/snapshot, accepted/deferred/needs_changes, nonempty owner reference and note; it is not authenticated, inferred from tests, or a merge/release.

Reports, scoped images and logs are temporary per-sprint evidence. `sprint-cleanup --repo AUTHOR_WORKTREE --sprint SPRINT_ID` previews removal of only receipt-bound new-format records; `--apply --owner-reference "..."` is explicit closure acknowledgement, not acceptance. Pending gates, unfinished scoped reviews and selected quarantined process ownership block cleanup. Unknown files, legacy records, native artifacts, caches, worlds and external asset requests are preserved. Cleanup is not old-report migration.

## Restricted-shell and publication notes

Use one CLI command per shell invocation; use `read` for JSON and `glob` for listings. Avoid `Get-Content`, `Get-ChildItem`, compound housekeeping, and permission broadening after a denial. A malformed command denial does not mean all CLI use is blocked.

MAIN is the development source. Publication is dry-run-first via `pwsh -NoProfile -File tooling/agent_workflow/publish_workflow.ps1`; privileged owner applies it. Receipt-verified backups protect owned files; unknown/unreceipted edits stop rather than overwrite. Shared runtime publication/binding status is not implied by this reference; see [HANDOFF.md](HANDOFF.md).

Owner-only MCP setup: after the adapter and pinned requirements are in the shared payload, run `pwsh -NoProfile -File tooling/agent_workflow/install_workflow_mcp.ps1` to verify/install `mcp==1.26.0` in the shared isolated venv. Preview role-bound MCP config changes with `publish_workflow.ps1 -WorkflowMcpOnly -PreviousReceipt <current-shared-receipt>`; the privileged owner applies only after reviewing the staged plan. MCP transport budgets are owner-coded (15-second startup/catalog, 5-minute tool-call ceiling), never agent arguments. This path changes only managed MCP config/permissions and the receipt-owned 05 lead profile; it preserves other servers, model settings and existing role grants.
