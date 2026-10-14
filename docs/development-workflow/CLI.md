# Agent workflow CLI

The workflow implementation is Python standard library code. After publication the canonical executable is `../MoonStation14-shared/tooling/agent_workflow/workflow.py`; all five assigned-worktree `workflow.py` shims dispatch to that one runtime while preserving caller arguments and cwd. For deployed integration-proof submissions from MAIN, invoke the published CLI with `python ../MoonStation14-shared/tooling/agent_workflow/workflow.py --repo . ...`, not the mutable MAIN development copy. Before publication, the MAIN development entrypoint is `python tooling/agent_workflow/workflow.py`. Builds/game checks run only as explicit integration-lead gate commands; no asset generation, Git write, watcher or automatic acceptance. Default exchange remains the sibling `Asset Building Workspace/work/agent_exchange`, found from the Git common directory (main and linked worktrees converge); publication does not create a competing queue. `--asset-workspace DIR` redirects isolated fixtures to `DIR/work/agent_exchange`. Read-only `status` and `plan-manifest` do not create the exchange directory. The workflow does not store OpenCode credentials or alter global OpenCode settings.

## Asset request → candidate → independent report → owner decision

Request JSON includes stable id/title/brief, explicit scope/runtime/source pin/asset IDs/dependencies and context paths. Example:

```json
{"id":"airlock-r1","title":"Airlock panel","brief":"One bounded correction","scope":"art only","target_runtime":"Java 1.21.1 / NeoForge 21.1.224","source_pin":"c9df5ef... or unverified","asset_ids":["airlock"],"dependencies":[],"context":["docs/brief.md"]}
```

`request --from-json request.json --context-root .` validates all IDs/contexts before creating a queue entry; context paths are copied with their relative directories and hashes. `claim ID --agent NAME` allows one claim. A corrected delivery uses a new request/revision ID, never overwrites an existing published delivery.

### Supported export shape and limits

Initial byte-copy support is intentionally ONLY the flat compact-airlock-style manifest:

```json
{"files":[{"file":"export/resourcepack/assets/.../model.json","destination":"src/main/resources/assets/.../model.json"}]}
```

Every row is required and copied as a complete declared group; no cherry-pick. This transport check does not prove that the manifest itself is semantically complete, preserves every asset dependency, or validates source fidelity. Use the package's current read-only verifier and review its provenance/binding/source records for those claims. Each source and unique safe destination must exist. Nested `items[]` wearable/equipment manifests (including null geometry destinations, bone meshes, frame geometry and OBJ/MTL map remaps) are **unsupported and blocked**, not treated as flat transfers. No transforms are implemented. Start with read-only planning:

```powershell
python tooling/agent_workflow/workflow.py plan-manifest `
  --manifest "../Asset Building Workspace/assets/Structures/Doors/Airlocks/compact_straight_bottom_r2/export/manifest.json" `
  --source-root "../Asset Building Workspace/assets/Structures/Doors/Airlocks/compact_straight_bottom_r2" `
  --target .
```

This validates and reports source hashes/destinations plus target conflicts for a pending/unreviewed package without copying it. It is the read-only preview path before delivery/review/owner acceptance. Actual request delivery requires a claim, full manifest, explicit revision, provenance, editable project(s), and real review gallery image paths:

```powershell
python tooling/agent_workflow/workflow.py deliver airlock-r1 `
  --manifest package/export/manifest.json --source-root package --revision r1 `
  --provenance README.md source/manifest.json --editable authoring/door.bbmodel `
  --review-images index.html previews/review.png
```

Source provenance/editable/gallery are recorded as metadata sidecars (not falsely added to runtime destinations). Delivery stores original manifest and exact sources/hashes, plus immutable delivery digest. Subsequent changed manifest/source/metadata, delivery copy, or review evidence marks it stale; create another revision request.

Reviewer supplies a JSON report, bound to exact candidate and delivery digest:

```json
{"request_id":"airlock-r1","snapshot_key":"<request snapshot sha256>","delivery_digest":"<recorded sha256>","reviewer":"reviewer-2","decision":"ready_for_owner","findings":["..."],"viewed_images":[{"path":"review.png","sha256":"<image sha256>"}]}
```

`review ID --reviewer NAME --result needs_changes|ready_for_owner --report report.json` validates request snapshot + delivery digest binding, bounded findings/report, reviewer identity, declared image hashes and actual PNG signature/dimensions. Reviewer name must differ from claim agent. Inspection remains the human reviewer’s self-declared assertion; hashes do not prove eyeballs or truth. Current evidence/report hashes are rechecked before import.

`import-assets ID --target WORKTREE [--owner-receipt receipt.json] [--plan-out plan.json]` is read-only dry-run and can be used before review/owner acceptance to inspect the proposed exact destination writes. Existing differing files block; same bytes are no-op. If supplied, an owner receipt must match request, decision and delivery digest; its SHA-256 is bound into that exact plan. A preview plan without the owner receipt is never applicable. Only after a ready independent review and owner receipt bound to exact delivery digest may apply:

```json
{"request_id":"airlock-r1","decision":"accepted","delivery_digest":"<exact digest>"}
```

```powershell
python tooling/agent_workflow/workflow.py import-assets airlock-r1 --target . --plan-out preview.json
python tooling/agent_workflow/workflow.py import-assets airlock-r1 --target . --owner-receipt owner.json --plan-out accepted-plan.json
python tooling/agent_workflow/workflow.py import-assets airlock-r1 --target . --owner-receipt owner.json --plan accepted-plan.json --apply
```

The apply plan records each target’s prior hash and exact owner-receipt hash; changed target contents, sources, reports or receipt bytes refuse apply and require a fresh post-acceptance plan/review. Overwrites require `--allow-overwrite` both when producing and applying a fresh plan. All paths are preflighted before the first copy: a destination directory or existing non-directory parent component is a conflict even if it looks like an absent file target. Ordinary copy-time OS errors produce an `import_attempt.json`, enumerate copied/failed/not-attempted paths and mark `import_failed`; earlier copies may remain. There is no rollback guarantee. Inspect the receipt and reconcile manually before retrying. No deletes/staging/merges.

## Coding candidate

`submit ID --agent NAME --base-ref main --build-identity TEXT --steps TEXT --unverified TEXT --gate-plan plan.json [--evidence PATH ...]` records source snapshot plus structured test/manual plan. The plan JSON requires `title`, `kind` (`sprint|feature|bugfix|docs|art|other`), `acceptance_criteria`, `fixture` (repo-relative existing file), `manual_steps`, and `checks` (`argv` array, `rationale`, `covers`). Checks may declare `kind` (`command|build|junit|readiness|gametest|launch-smoke`); recognized Gradle readiness/GameTest/test tasks must use the corresponding typed evidence kind. Readiness plans specify exact selectors; builds specify artifact paths; launch smoke specifies readiness markers and a captured log. Explicitly justified documentation-only plans may have no checks with `no_checks_rationale`. The integration lead records fresh native counts/selection or artifacts through the shared evidence reader; unavailable evidence is unverified and blocks technical-ready publication. Fixture bytes are captured by hash. `candidate-review ID --reviewer NAME --report report.json --result needs_changes|ready_for_owner` checks fresh source/evidence and independent author identity; report contains `candidate_id`, `snapshot_key`, `reviewer`, `decision`, findings, and `{path,sha256}` PNG records. Nonvisual reports may use `viewed_images: []`. Candidate statuses never imply owner acceptance or test success. Legacy submit records can still receive technical reviews, but cannot be published for owner testing without a gate plan.

The primary author invokes the named integration lead after submission with `gate-request ID [--central-worktree PATH] [--origin-session ID] [--server URL] [--wait-timeout SECONDS]`. It synchronously resumes/creates the integration-lead session in the central worktree, waits for its response, and returns JSON: ready=0, needs-changes=3, blocked=2. Lead runs `gate-preflight ID`, then `gate-validate ID --actor integration-lead`. It executes exact argv checks sequentially, in the candidate worktree, under a blocking OS mutex shared by linked worktrees; detailed records, logs and artifact hashes are kept under `gate-reports/`. Native GameTest/readiness/JUnit evidence must be fresh and selector-bound; command-only completion has unknown test counts and is not coverage. `launch-smoke` observes fresh output for bounded regex readiness markers (maximum 120 seconds), then stops only its owned process tree and requires a controlled stop. That is startup-only evidence: it proves no GPU/visual quality, live gameplay consumer, or owner input acceptance. Commands run with `shell=False`; timeouts attempt to stop only the owned process tree with a bounded wait. If Windows tree stop is unverified, a shared `gate-blocker.json` quarantines subsequent expensive jobs; only integration-lead can clear it with `gate-clear-blocker --actor integration-lead --pid PID --confirmed-stopped` after independently confirming that owned tree stopped. This is coordination, not a sandbox: actor labels are declarative and only trusted plans should be run. Candidate freshness is checked around execution. A needs-changes independent report is finalized with `gate-finish ID --result needs_changes --report report.json`; actionable findings return to the author, who fixes and submits a new ID/revision. A passing check and independent ready review can be finalized with `gate-finish ID --result ready_for_owner --report report.json`, which publishes a concise `manual-tests.md` for William and requests a native Windows notification. The log leads with open entries and gives each candidate's preparation, a few manual steps, check status/counts and a link to detailed artifacts; full command output and hashes stay in the bound JSON report. Visual reports bind copied viewed PNG bytes to `original_images` source paths/hashes; both are freshness-checked. `gate-scope-review ID --reviewer review|fidelity-reviewer --actor integration-lead [--server URL] [--wait-timeout SECONDS]` requests a scoped reviewer report. `manual-tests` refreshes the derived index; owner dispositions remain solely in candidate records. Notification failure is explicit and retryable with `manual-notify ID`; OS delivery does not guarantee the user saw it. This is a manual test request, never owner acceptance or merge. Stale candidate sources, report, evidence artifacts, or validation logs block publication.

The dispatcher discovers the already-running OpenCode service with `opencode service status`, then sends bounded requests through the authenticated `opencode api` CLI. By default it omits `--server`, using the existing background service; explicit `--server URL` requests also use normal CLI authentication. It does not speak raw HTTP, start a standalone backend, persist credentials, or change global OpenCode auth/settings. A dispatch waits for the exact integration-lead session and verifies its persisted candidate outcome; uncertain prompt admission or an idle session is `blocked`, not a second candidate or false pass. `gate-scope-review` invokes the selected review role read-only at the candidate worktree and records copied-view/source-original PNG hash bindings in its report. MAIN is the development source; after publisher deployment the one runtime is `../MoonStation14-shared/tooling/agent_workflow/workflow.py`, and installed local shims preserve the caller's cwd/arguments while loading it. Publisher unit fixtures only test isolated temp trees; they do not apply the publisher or validate live worktree installations.

The fifth coordinator records William's explicit candidate disposition with `candidate-decision ID --owner-receipt owner.json`; receipt fields are `candidate_id`, exact `snapshot_key`, `decision` (`accepted`, `deferred`, or `needs_changes`), nonempty `owner_reference`, and nonempty `note`. Example:

```json
{"candidate_id":"code-42","snapshot_key":"<exact candidate snapshot sha256>","decision":"accepted","owner_reference":"William: explicitly accepted candidate code-42","note":"Acceptance is separate from build/test results."}
```

Only a fresh candidate with an independent `ready_for_owner` report can be accepted; defer/needs-changes can record explicit owner direction for a fresh candidate without approval. The receipt path and hash are kept separate from technical review. The tool cannot authenticate the author of owner-reference text; do not self-accept or infer a decision from green tests. Status labels these owner outcomes separately and marks later source/receipt changes stale while retaining the recorded decision as history. Use a new candidate ID for revised work. No merge/release occurs.

`status` prints the shared queue without creating it. Queue records are JSON under `work/agent_exchange/{requests,candidates}`. There are no imports into existing asset packages in these workflows; import is explicit and dry-run first.
