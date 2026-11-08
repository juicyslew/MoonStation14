# WR03: authoritative preflight and focused check packs

WR03 technically READY after independent review (128/128 focused and store tests; 68/68 WR01 static fixtures). Inactive and not owner-accepted. Nothing is installed, scheduled or published by importing these modules. No native validation is run by preflight.

## Entry points and ownership

- `workflow_submission.submit_submission` is the new admission facade. A new request always passes authoritative preflight internally; supplying a previously printed bundle or skipping the local command cannot bypass it. The low-level `WorkflowStore.submit` remains a trusted repository operation, not an author-facing API.
- `workflow_submission.py check` is a read-only local check. `ready_for_admission: true` is neither an admission nor a validation PASS. Administrative `submit` requires explicit database and external artifact-directory paths. There is no default live database/exchange.
- `PreflightContext` contains program-configured author/worktree identity, agreed policy, evidence metadata and the pinned publication receipt. These are **trusted inputs**, not new author-controlled request fields. The local context-file CLI does not authenticate OpenCode; WR04 must establish that binding and load trusted policy before calling the facade.
- `WorkflowStore.lookup_submission_receipt` and `submission_artifacts` expose saved receipts and admitted submission evidence without another lifecycle store. Exact retries return the original receipt even after source changes. Changed input under the same key conflicts; another author/worktree context cannot obtain an accepted replay.

## Frozen inputs

`workflow_preflight.capture_source` uses existing bounded Git helpers and hashes all tracked and nonignored untracked files, including uncommitted changes and tracked deletions. Unsupported links/special files fail closed. SHA-256 canonical JSON encoding comes from the strict WR02 codec. Source, full resolved plan and agreed-policy digests are recomputed, not accepted from model prose.

Every agreed criterion must appear exactly once as COMPLETE, with available byte/hash/size-verified evidence. References may be shared across criteria. Preflight evidence must name the actual SUBMISSION producer identity; it is not a fictitious completed CHECK. Required manual setup must exist. Explicit incompleteness/checkpoint purpose, invalid schemas, missing evidence, inappropriate selectors and oversized review inputs reject before admitting any run or task. These declarations cannot prove arbitrary gameplay implementation completeness; later independent review still owns judgment.

Configuration verification reads the receipt-based publisher's exact worktree ownership rows and compares the actual configuration bytes. MAIN's hash cannot substitute for an author's tailored hash. Unknown/corrupt/deleted/ambiguous ownership is ERROR; an unapproved mismatch against known ownership is actionable FAIL. An override requires exact trusted tooling-change provenance, not an author claim.

Before admission, source and receipt/evidence bytes are checked again. Frozen source manifest, bundle and evidence copies live outside the source worktree, with hash-bound registered artifact references. Generated artifact identities include their submission namespace. This is a bounded snapshot observation, not an OS lock preventing later edits; execution must recheck applicability.

## Check packs and rendered reviews

`workflow_check_packs` has a finite version-1 registry: provenance, unit, readiness, focused/full GameTests and package. It reuses `workflow_runtime.resolve_gate_plan` and its typed producer plan shapes; it does not add a Gradle runner or reparse execution logs.

Trusted `coverage` maps agreed criteria to required packs. Trusted `selector_requirements` binds the minimum selectors for every selected unit/readiness/focused-GameTest pack; request selectors may add narrow checks but cannot replace those requirements. The empty mapping is required when no focused packs are selected. Coverage is explicitly declared, never inferred from filenames.

Readiness selectors use the existing `ms14Readiness` categories; unit tests use narrow Gradle `--tests` selectors. Focused GameTests always include tags and an isolated fixture directory. Full GameTests are rejected without an exact trusted owner exception matching sprint, worktree and input. Fresh packaging and provenance are mandatory. The package pack runs `cleanJar assemble` so runtime package outputs are recreated while retaining unrelated build outputs. Artifact evidence records `observed_fresh_this_run` when stat/hash observations indicate a change within the current run window; this is not proof of producer execution or recreation. Use the executed argv/log together with artifact evidence. `changed_this_run` remains a content-hash comparison and can be false for reproducible bytes.

The shared `render_review_input` creates a strict WR01 dispatch envelope plus bounded objective, criteria, manual steps and protocol instructions. Preflight measures the whole rendered body for every role, reserving bounded future identities and a completed-receipt reference. It does not invent a passing receipt. WR06 must reuse this renderer and check the final actual dispatch body again after adding current completed evidence.

## Results, checks and limits

Expected rejection persists an actionable FAIL receipt and real diagnostic reference, without a run/task. Infrastructure/staging faults persist ERROR with a matching incident/page intent when storage is available; they do not request a new revision or unrelated work. If storage itself cannot retain a receipt, `AdmissionUnavailable` reports that honestly. Global incidents currently retain fault context in their message, with diagnostic artifacts separately registered; no fake run is created to attach evidence.

Focused tests use disposable real Git repositories, isolated SQLite databases, CLI subprocesses and fault injection. No Minecraft suite or live OpenCode session is used. Exact runtime authentication, execution, author consumption, provider/cancellation recovery and owner acceptance remain later units.

Local check (from the repository root, using external prepared request/context files):

```powershell
& '../MoonStation14-shared/.venv-workflow-mcp/Scripts/python.exe' tooling/agent_workflow/workflow_submission.py check --request '<request.json>' --context '<trusted-context.json>' --caller '<bound-caller>' --key '<same-request-key>'
```

The request uses the unchanged WR01 submission schema. The trusted policy/bundle schema is `contracts/preflight.schema.json`; context files contain exactly `repo`, `authenticated_author`, `policy`, `artifacts`, `publication_receipt_path` and `publication_receipt_sha256`.
