# WR09: Integration Status board

Technically **READY**. Decision and immutable evidence bindings: `build/agent-workflow/workflow-redesign-plan/wr09/acceptance.json`. No installation, live cutover or owner acceptance is implied. The old observer remains the sole live scheduler until WR12.

## Architecture and reuse

- `workflow_store.readonly_snapshot` reads an existing SQLite store in one `mode=ro`, query-only transaction, including live WAL state. It never constructs a writable store, creates missing directories/databases or migrates schema. `_schema_inventory` is shared with initialization. `feature_heads` selects current worktree/sprint cards; non-head runs remain history. Domain sections retain owner decisions, acceptances, regression jobs, immutable terminal results, tasks, artifacts, incidents, resources and delivery/ACK distinctions.
- `workflow_board.project` produces privacy-whitelisted version-1 data. It does not expose credentials, claim tickets, raw prompts or config bodies. Native occupancy counts `native_validation`, not held review sessions; queued native work counts CHECK tasks. Persisted AI pause is distinct from live inference health.
- `gate_progress.render(..., workflow_snapshot=...)`, `workflow_page` and the existing `_PAGE` own the board. No second dashboard was introduced. Default legacy callers keep their previous behavior and read-only footer. Workflow mode uses compact cards, expandable details, collapsed changes editing, needs-you notices and saved-snapshot fallback.
- The existing coordinator serves a generic data-free `/integration-status` scaffold. Owner-authenticated `/v1/board/snapshot` supplies worktree-scoped data. `/v1/board/events` emits only `board-revision` hints and heartbeats through the existing wake owner; hints are not durable lifecycle authority. Clients fetch a snapshot on connection/reconnection. Complete DB-backed board operations are offloaded from the ASGI loop. Closing the page/subscription does not stop work.

## Phase and action invariants

Six horizontal lists: Building, Queued, Checking, Reviewing, Your testing, Accepted. Current persisted attempt/owner state determines phase, never model text, TTLs or activity guesses.

- Corrective feature FAIL or owner request-changes returns to Building. PASS awaiting owner goes to Your testing.
- ERROR, pause and uncertain cleanup remain at their affected Check/Review boundary. STOPPING stays visible until CLOSED; canceled, owner-closed and superseded work is retained in history.
- ACCEPT remains Accepted despite a separate regression FAIL/ERROR. Owner CANCEL on an unaccepted PASS closes the owner view without changing historical PASS.
- Incident seen is not resolved. UNKNOWN resources and unresolved historical faults remain visible. Failed-preflight worktree text is untrusted: runless admission errors expose only a generic unscoped count, no invented feature/owner action.

Owner actions reuse the existing snapshot/decision, cancellation and recovery services. The board adds no TEST decision, general reset, lock deletion, retry-everything or author-wake command. `OwnerService.decide` now checks the admitted run’s exact worktree before receipt replay/artifact effects; the store repeats the fence transactionally. Foreign owners receive 403 without acceptance/regression/staging side effects.

Bearer credentials stay in page memory, never HTML, URLs or browser storage. Actions bind to displayed run/generation/input/sequence/result identity. Lost replies retain original command/payload for explicit replay after a real connection; stale or changed views require explicit review. Background refresh never silently authorizes new source. PENDING is not RESUMED. Disconnection retains cards with saved timestamp and unknown live health, disabling actions. Connected transport does not establish inference health.

## Validation

Canonical evidence root: `build/agent-workflow/workflow-redesign-plan/wr09/`.

- Final `wr09-visual-suite.{json,log}`: **177 discovered / 169 selected / 8 inherited duplicate exclusions; 169 passed, no failures/errors/skips**. All 138 before/after source hashes match. Includes store initialization/read-only/WAL, phase projection, owner commands, HTTP/event responsiveness, shared Node DOM and legacy renderer checks. Flat/package fixture patches target actual imported modules; the shared Node runner uses UTF-8 stdin, avoiding Windows command-line limits.
- `owner-worktree-fence-tests.log`: 32 focused passes. Actual rendered client commands round-trip through the real ASGI owner service/store: same-owner acceptance/replay creates one regression; request-changes/CANCEL preserve PASS; foreign owners reject all three actions with zero side effects; stale views and author credentials reject.
- `browser-visual.json`, log and `browser-visual-evidence/`: real contained Edge run, 4.23 seconds; six readable lists, details, narrow horizontal layout, correct footer and UTF-8, phase movement from SSE-triggered snapshots, clock independence, stale-owner rejection and disconnected card retention. Counter 1 after browser closure, native fake-producer PASS and verified bound Job0 cleanup. Four runtime hashes match the final focused report. Parent inspected screenshots; independent visual review passed (`ses_eb82c69beffe0juEap1p0MgZ7G`).
- Initial browser FAIL and intermediate runtime PASS remain unmodified. Three closed browser roots were identity-verified dead, archived with CRC/member-byte/hash manifests, then removed; see `fixture-cleanup.json`.
- Preservation: six configs, 108 installed rows, four authorized contracts, receipt and publication pointer unchanged. Twenty-two preexisting deletion-policy deviations remain untouched.

This is tooling/fixture validation, not Minecraft/Gradle execution or live-owner acceptance. The browser’s strict fake owner endpoint tests client transport; real mutation ownership is covered by the ASGI/store tests. Illustrative actor metadata is synthetic. No inference-health guarantee is established.

## Interaction when activated

After the later controlled installation/cutover, open the configured local `/integration-status`, connect with the owner credential, expand Details/manual steps and choose the action for the displayed revision. Review any changed/stale source before acting. Until WR12, screenshots are previews—not a live redesigned pipeline.
