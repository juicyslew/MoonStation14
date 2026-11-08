# WR04: inactive local coordinator and thin SDK adapter

WR04 technically READY after independent review: 149/149 relevant workflow/store tests, zero failures or skips. This is not installed or connected to the live exchange, and is not owner-accepted. The old observer remains the sole live scheduler; replacement occurs only at WR12.

## Ownership and entry points

- `workflow_coordinator.ExplicitCoordinator` is inert until `start()` or ASGI lifespan entry. It consumes the WR02 outbox and calls the WR03 admission facade. It does not run legacy observer reconciliation or maintain another lifecycle JSON authority.
- One per-database process registry plus the existing nonblocking OS `gate_lock` prevents duplicate dispatch ownership. Failed thread startup releases its own guard. A stop timeout retains ownership rather than pretending an executing child/thread stopped.
- `load_coordinator` reads an explicit administrative configuration; `serve --config ... --port ...` binds loopback only, one worker, no reload. No service install, default exchange/database, automatic OpenCode backend or publication occurs.
- Configuration contains exactly `database`, `artifact_dir`, `opencode_server`, and `bindings`. Each binding supplies `token_file`, stable `caller`, and `context_file`; the latter uses WR03's trusted context shape. Database/artifact paths must be outside every configured worktree. Tokens and callers are unique. Context updates require explicit configuration/reload work; no request can supply or replace its trusted context.

## API and caller identity

The configured bearer binding selects the caller/context. New and repeated submissions verify the actual configured OpenCode session/worktree, approved `orchestrate-` author agent and primary role through existing `gate_dispatch._session`/`_role` helpers. No inference is needed for these reads. Tests inject isolated identity responses; real installed OpenCode authentication remains a later deployment check.

- `POST /v1/submit`: exact body `{idempotency_key, request}`; returns `{response: <WR01 receipt>}` only after repository commit. The request cannot bypass preflight or supply a run, context, executor or previously passed flag.
- `GET /v1/status`: optional run/key selectors, scoped to the configured author/worktree. Reports immutable saved receipt/current attempt and `freshness: not_checked`, never fabricated source freshness or consumption.
- `GET /v1/health`: local coordinator/pending-event/error observations. Status does not query the provider or require a live author session lookup.

`workflow_coordinator_mcp.py` exposes only `submit(request_path, idempotency_key)` and explicit `status` through official MCP SDK stdio. It reads a prepared request and forwards it to the configured loopback endpoint. It owns no database, queue or execution. It reuses legacy bounded response/thread/schema helpers without constructing the old MCP facade or its services. Requests/replies are bounded; redirects/proxies cannot reroute loopback transport. Uncertain responses preserve the same key and report recovery, not a replacement submission.

## Dispatch and reconciliation

Admission directly signals the dispatch thread after commit. Startup and bounded code-only fallback reconcile saved outbox identities if a wake is lost. Handlers receive stable event IDs; reclaimed effects must be observed before reposting. APPLIED acknowledges only that outbox effect, ABSENT permits dispatch, and UNKNOWN retains the claim and durable incident without an unsafe duplicate. Transport/outbox acknowledgement is not a worker claim or accepted task result.

Dispatch faults use the existing incident owner and page-projection wake. Internal incident projection wakes are consumed without claiming a human saw the page. Missing backend capability leaves work pending rather than discarding it or inventing a verdict. No actual native handler is configured by the WR04 starter; WR05 supplies the existing executor integration. Review/delivery/owner actions remain WR06–WR09.

The only added repository APIs are aggregate outbox/event reads. Existing transactional state/result authority and execution ownership are not bypassed.

## Validation and limits

Tests use real disposable Git/SQLite preflight, isolated HTTP/ASGI, a real official-SDK stdio child, owned loopback Uvicorn fixtures and cross-process guard checks. They exercise admission/replay, changed input, identity denial, missed wake/startup, ambiguous effect reconciliation and later guard reuse. They do not start Minecraft, call live OpenCode or mutate installed tools.

`latency-sample.json` records initial local fake-handler timing. Saved-event creation to handler start is an upper bound including the commit tail; preflight/admission duration is separate. The current sample is not a p95 guarantee, model-startup measurement or native queue latency. Post-admission-return timing is not instrumented. WR11 owns broader latency/real-adapter acceptance.

Dependencies are declared separately in `requirements-workflow-coordinator.txt` using already-present MCP/Starlette/Uvicorn versions. No dependency installation or existing MCP configuration was changed here. Keep this inactive until cutover; the owner has no new live interaction to perform yet.

Dynamic worktree author mode is a per-call identity adapter for explicitly configured worktree slots. The SDK sends the current session ID in `X-MS14-Author-Session`; the coordinator verifies the exact OpenCode session, directory, orchestrate agent prefix, and primary role before author operations. It neither creates nor resumes chats. The author `status(session_id)` context response is limited to the authenticated caller, repo, typed shared floors, and pinned publication-receipt reference.
