# WR02 storage API (inactive library)

WR02 technically READY after independent review; not deployed or owner-accepted. `tooling/agent_workflow/workflow_store.py` is not connected to any live service. `WorkflowStore(path)` requires an explicit local database path; imports only read the protocol schema. No default exchange, scheduler, MCP tool, transport or executor is selected or started.

## Authority and transactions

- One SQLite database owns records. Operation-scoped connections verify WAL, foreign keys, FULL synchronous commits and bounded busy waits. Initialization accepts an empty database or the identified compatible schema; no adoption, repair or migration of other databases occurs.
- Mutations use `BEGIN IMMEDIATE`; their response/history and outbox effects commit together. External verification, API calls, process waits and native mutexes stay outside transactions.
- `submit` freezes admitted request/run/task identities and emits an internal `TASK_READY` wake. `record_rejection` also records malformed strict-JSON requests without admitting a run. An ERROR rejection requires its matching durable incident/page intent; ordinary actionable FAIL rejection is not an owner emergency.
- Caller/operation/key namespaces and canonical request digests fence retries. Transition/recovery command receipts preserve exact responses. `feature_heads` is the current pointer, not a uniqueness constraint on all historical runs; explicit replacement requires a settled terminal head.
- JSON payload columns remain database records, not another lifecycle authority. External exchange JSON/HTML are not read or written. `get_run` projects the SQL current-generation pointer and sequence over immutable submission data.

## Tasks, evidence and ownership

`logical_task_key` identifies one work slot per attempt independently of opaque task IDs. Dependencies must be saved, noncyclic and in the attempt, except explicitly retained CHECK sources. Review admission requires completed coded checks.

The initial CHECK is program-owned, not a fictitious OpenCode reviewer. `dispatch_task` saves its native identity/token or an exact REVIEW `dispatch_envelope` before claims. `claim_native_task` and `complete_native_task` are distinct from REVIEW `claim_task`/`complete_task`. Saved session/process, run, evaluation/assignment generation and input bindings fence new results and retries. Result and `TASK_COMPLETED` intent commit together. ERROR requires a typed fault report; FAIL needs corrective findings.

`register_artifact` stores immutable external path/hash/size/provenance references. Preflight SUBMISSION artifacts may precede admission but must resolve to the actual admitted submission when consumed. TASK artifacts bind the saved producer/input. OWNER acceptance provenance is unsupported until the real owner-decision integration exists; it is rejected, not invented. Large artifacts remain outside SQLite.

Resource claims retain history and exact owner identity. HELD/UNKNOWN claims cannot be stolen by timeout; registered claim-bound cleanup proof frees the specific resource for later work. Stale callbacks cannot release a later owner. STOPPING closes admission, records never-started cancellation or cancellation intents, and revokes unfinished assignments. `confirm_task_cancelled` records an assignment-bound adapter attestation; it does not itself stop an OS process.

## Results and recovery

Terminal PASS/FAIL/control closure require settled children/resources. Unresolved cleanup produces incident-backed ERROR, retaining ownership supervision. CLOSED records CANCELLED/SUPERSEDED control, never a fourth verdict. Terminal attempt results cannot reopen.

`recover_error` creates a linked higher generation on the same immutable run only after actual child/resource settlement and registered fresh-input provenance. `reused_checks` links retained PASS sources rather than cloning checks or their results. Required CHECK obligations carry through every recovery generation; missing work cannot disappear after another ERROR. All retained coverage permits a REVIEWING restart; otherwise it remains queued for explicit missing work. No native check is launched by recovery. A generated `RECOVERY_STARTED` status intent identifies the prior ERROR and author/page recipients.

Result digests hash canonical JSON of exactly `{run_id, evaluation_generation, input_digest, result, findings, artifact_refs}`. This semantic digest differs from the complete delivery-body digest. SHA-256/JSON encoding matches the existing `snapshot_key` convention; the small strict wrapper avoids importing the legacy filesystem lifecycle runtime and additionally rejects duplicate keys, non-integer protocol numbers and non-JSON Python values.

Delivery records preserve typed recipients, immutable payloads, fenced leases, transport receipts and protocol ACKs separately. An exact author ACK may precede the HTTP receipt; old-generation ACKs settle only historical delivery. Page publication is not proof a human read it. One incident owner handles evaluation and delivery faults; seen and proof-backed resolution are distinct. Delivery incidents never rewrite a validation verdict.

## Validation and limits

Focused tests cover SQLite records, real subprocess commit boundaries, concurrency, public-API recovery-to-PASS, consecutive-generation missing obligations, leases, typed contract rejection and incident state/outbox fault rollback. The final independent run selected 77 tests: 77 passed, zero failed. The unpublished v1 STOPPING schema erratum aligns null committed results with exactly one proposal; 34 positive and 34 negative static fixtures cover the correction. It intentionally changes the four frozen WR01 contract files, not the agreed behavior.

`sqlite3` is standard library; `jsonschema` is already supplied by the pinned MCP SDK environment. No new service or database package is required. Use that existing venv for the `test_workflow_store*.py` suite.

This library trusts authoritative preflight and adapter verification. It does **not** establish actual source freshness, SS14 parity, artifact bytes, OS/session cessation, policy authorization, notification consumption, deployment readiness or owner acceptance. Those runtime integrations remain later work; no live activation occurs before WR12.
