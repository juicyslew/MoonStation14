# Shared MCP: feasibility review and stop conditions

**Current notice · 2026-10-19:** The shared MCP update is deployed and healthy; nine scoped bindings are connected/refreshed without a global reload. Current usage, limits, and record locations are in [author diagnostics](AUTHOR-DIAGNOSTICS.md). The acknowledged genuine MAIN result remains FAIL; diagnostics make its bounded log excerpt available but do not fix the validator failure. Historical source audit and design notes below remain historical, not current blockers.

**2026-10-18 · Design-only, read-only source audit. No runtime changes or transport experiments.**

## Verdict

The goal—**no persistent Python MCP adapter children, with MCP served by the existing coordinator process**—has a credible implementation path. It is **not yet integration-proven or ready for cutover**. The installed host and SDK contain the necessary transport mechanisms, but that is not proof that our credentials, catalogs, permissions, and lifecycle work together.

The audit found a concrete flaw in the simplest earlier sketch: **one SDK server cannot safely switch its shared tool cache between our different author and reviewer catalogs.** That design must change before implementation.

The biggest remaining external uncertainty is **private credential delivery through OpenCode's scoped runtime configuration path**. Prove this first, not after implementing all workflow operations. Absolute assurance is not possible from static inspection; an early isolated compatibility fixture is the way to avoid a late discovery.

## What was actually checked

- Project adapter, binding generator, coordinator routes/lifespan, store, publication checks, and supervisor source. Existing installed evidence is `build/agent-workflow/workflow-redesign-plan/wr12/installed-idle-192-evidence.json`.
- Installed Python MCP SDK **1.26.0** source, including HTTP manager, low-level server/cache and request context, and transport security.
- Installed OpenCode executable's embedded configuration/transport source. Audited binary SHA-256: `13ab389dcc490d1f3c1bc05b3c20811a1e8761b6db494c036d5f651e1518874f`.

No private token contents, live API calls, configuration replacements, provider turns, or production tasks/results were used for this audit. Existing 192-check installation evidence certifies the old arrangement, **not** the proposed transport. Its genuine reviewer-dispatch/result-ACK and exact tool-picker boundaries remain open.

## 1. Confirmed SDK trap: catalog state is shared

**Source-confirmed; changes the design.** SDK `mcp/server/lowlevel/server.py:434–488, 521–530` stores tool definitions in `_tool_cache`, keyed by tool name. One form of `tools/list` clears and replaces that entire cache. Tool calls consult it for schema validation.

Our author and reviewer catalogs differ, and both define `status` with different arguments. Switching a single server's catalog according to the requesting credential can overwrite the definitions another caller needs. This can happen through ordinary interleaved calls; it does not require malicious inputs. Stateless HTTP alone does not fix a cache on a shared server object.

**Revised architecture:** use fixed MCP catalog profiles with separate SDK server/cache ownership, inside the **same coordinator process**. Author and reviewer are distinct profiles; retain any genuinely required additional author-mode variant rather than silently changing its contract. Four reviewer responsibilities can share their identical catalog without sharing their authorization.

Separate paths such as `/mcp/author` and `/mcp/reviewer` are acceptable. One literal URL is not a requirement; one shared process without replicated Python children is the resource objective. Each profile needs its own correct SDK-manager lifecycle. Do not mutate caches, monkey-patch the SDK, or disable authoritative validation to preserve a one-URL sketch.

**Required proof:** interleave both profiles' discovery and calls, including their conflicting `status` schemas. Correct calls must keep succeeding; inappropriate calls must remain denied. Establish the exact installed author modes, not just a simplified fixture catalog.

## 2. Credential loading: a potential compatibility blocker

**Partly established, partly unproven.** OpenCode's remote config accepts URL, headers, OAuth disablement, protocol selection, and timeouts. Embedded file-backed config loading applies `{file:...}` substitution, reading and escaping the referenced file's trimmed contents.

However, our scoped installation uses `PUT /api/experimental/mcp/:server` with an already-typed config object. The source trace did **not** establish that this path performs the same substitution. A literal file reference could reach the HTTP transport instead of the token. This is unproven, not confirmed broken.

**Stop condition:** before building the full bridge, demonstrate a safe configuration/load path using isolated fixture credentials. Verify what reaches the endpoint, persistence/reload behavior, and that no credential appears in public configuration, receipts, logs, errors, or evidence. The existing publisher may need to distinguish persisted references from privately materialized runtime headers. Do not assume generic config interpolation also applies to runtime PUT; do not add a credential-broker daemon as a hidden workaround.

## 3. Installed protocol is not the newest documentation

**Manageable compatibility dependency; wire proof outstanding.** SDK 1.26.0 implements the older initialization-based protocol. The current July 2026 documentation describes different discovery/statelessness behavior; it cannot be copied as the installed wire contract.

The host exposes `legacy`, `auto`, and `2026-07-28` modes. Existing binding generation already pins `protocol: legacy`, with startup/catalog/execution timeouts of 15/15/300 seconds. Retaining explicit compatible settings is preferable to relying on untested automatic fallback. The SDK's own stateless HTTP mode is not an upgrade to the July 2026 protocol.

**Required proof:** real installed host connects, discovers tools, and calls the fixture server using compatible settings. A direct SDK client alone does not prove host compatibility. Do not silently upgrade either installed component to make the proposal work.

## 4. Sharing a process must not share caller identity

**Existing authority can be reused; new transport mapping needs proof.** Keep the nine binding identities, names, and credentials. The bearer credential selects trusted caller/worktree/reviewer authority. A supplied `session_id` still needs the existing identity check; it is not a credential or permission grant.

SDK request context is set for individual messages (`lowlevel/server.py:727–761`). Use request-local authenticated context, not a mutable global caller. Authenticate every applicable HTTP method. In stateful HTTP mode, additionally bind SDK session IDs to the credential's principal; an SDK session ID alone must not let another principal inherit access.

Prefer evaluating SDK stateless HTTP mode first because these tools do not require transport-owned conversation state. Confirm actual client behavior and lifecycle before selecting it. Application run/task IDs and receipts remain durable regardless of transport mode.

**Required proof:** concurrent callers remain separate; missing/incorrect credentials, wrong sessions/tasks/generations, wrong-profile calls, and any cross-principal session reuse are rejected. Discovery creates no validation job or provider turn.

## 5. A route is not the whole implementation

**Manageable, but more than a config swap.** Current REST handlers are nested inside `ExplicitCoordinator.app`. Shared MCP commands must reuse their application operations and admission/identity/result owners, not copy their logic or call the store through a bypass. A focused extraction may be necessary, with regression coverage of REST as well as MCP.

Existing blocking work is offloaded with AnyIO. Keep blocking file reads, identity checks, SQLite work, and preflight out of the async event loop. Do not make synchronous HTTP calls back into the same coordinator. Store connections are per-operation; no inherent SQLite thread-affinity blocker was found.

SDK manager `run()` must be composed with the coordinator's existing startup/shutdown lifespan. Each manager instance is one-shot; use the SDK's lifecycle owner, not a second scheduler. In-process MCP defects can affect coordinator responsiveness, so exception, cancellation, and cleanup boundaries matter.

**Required proof:** discovery/status remain responsive during a slow command; repeated startup, disconnect, reconnect, and shutdown settle their tasks. No request creates a second coordinator or worker merely to expose tools.

## 6. File arguments, errors, and retries must retain their meaning

**Behavioral regression risk.** The adapter currently reads request/result files before sending REST payloads. Moving that responsibility centrally must retain trusted repo-relative resolution, valid absolute artifact references, strict decoding/JSON handling, and the 1 MiB input bound. Current `Path` resolution is not itself proof of a root-confinement policy; do not invent a restriction that breaks legitimate shared artifact paths, or discard existing authorization/artifact checks.

Preserve bounded responses, command deadlines, receipt fields, and actionable errors. A timeout or disconnected client does not prove submission was never accepted. Recover with the same key/status receipt; do not create another run or cancel durable work just because the transport disappeared. Preserve `CHECK_SCOPE_STATUS` after uncertain registration and existing result-ACK semantics.

**Required proof:** equivalent inputs produce equivalent operation results through REST and MCP; missing/oversized/invalid files fail clearly; an accepted operation whose response is lost is recoverable without duplicate effects.

## 7. The host may change names, retain connections, or consume memory

**Unverified integration/resource boundaries.** Nine entries pointing at a shared service must retain their existing tool namespaces and per-agent permissions. Schema support does not prove that same-service entries remain distinct or that real tool visibility matches our assumptions. Preserve `codemode: false` unless a separate change is deliberately evaluated.

Removing Python children does not diagnose why 32 instances accumulated. The host might still retain excessive clients/connections or memory. A leak can move rather than disappear. Existing supervisor launch-helper imports also remain a separate resource concern.

**The numerical budget is not achieved merely by deleting adapters:** the observed supervisor and coordinator already total **245.56 MiB private commit** (206.31 + 39.25), before adding an in-process MCP SDK endpoint. That already exceeds the proposed 200 MiB Python target. The supervisor's retained administrative launch dependencies need a separately validated lightweight arrangement, or the budget needs explicit reconsideration; do not promise either target from transport consolidation alone. These private-commit figures must not be confused with their 170.65 MiB summed working sets.

**Required proof:** actual host catalogs and configured permissions are correct; repeated scoped replacement/removal/reconnection has bounded counts and memory; no local Python MCP adapters launch. Measure the coordinator, supervisor, and host-attributable increment, not just Python process count. Proposed 200 MiB Python/250 MiB overall incremental private-commit goals are still unmeasured, not promises.

## 8. HTTP and publication introduce concrete obligations

**Source-confirmed requirements; not architectural blockers.** SDK transport security is disabled by default when no settings are supplied. Configure intended loopback Host/Origin handling and authentication explicitly; do not expose the route on all interfaces. Verify OAuth is disabled for these local bearer bindings, and credential/redirect handling stays within the intended endpoint boundary.

New code and bindings change publication hashes. Extend the existing staging/publisher/receipt owners; old receipts cannot certify new bytes. Do not edit installed runtime files or public configs ad hoc. Any eventual idle replacement must preserve work, immutable evidence, and the maintenance hold. Exact ownership must establish which old adapters can be retired; no blanket kills or global location reload.

## Ordered proof plan: find deal-breakers early

1. **Compatibility fixture before the full refactor.** Installed host plus installed SDK; fixed catalog profiles, fixture credentials, safe scoped loading, actual tool names/permissions, representative read-only calls, protocol and timeout settings. No production tasks or provider turns. Stop if the credential or host integration cannot be made correct simply.
2. **Shared-command fixture.** Reuse existing owners; compare REST/MCP results and test concurrent identities, admission hold, path/payload bounds, lost responses and idempotent recovery. Do not replace domain logic with a transport-specific implementation.
3. **Lifecycle/resource fixture.** Repeat connect/replace/remove/shutdown cycles; measure idle/peak memory and responsiveness. Confirm zero adapter children and bounded host/server resources.
4. **Separately authorized idle publication.** Use canonical publication/lifecycle owners, preserve the hold, and verify exact loaded tools. Verify genuine reviewer dispatch/result ACK only on authorized genuine work—not a manufactured production task. Those proofs remain distinct from fixture success.

These are future implementation/validation activities, not actions performed by this design review. No coding-gate submission or runtime cutover is needed merely to deliver this report.

## Decision

**Retain the shared-process direction, revise the catalog design, and require compatibility proof first.** We have not found a fundamental reason it cannot work. We have found enough concrete integration hazards that calling it “just add one endpoint” or promising success now would be unjustified.

### Source locators

- Project: `workflow_coordinator_mcp.py:17–34, 64–132`; `workflow_coordinator.py:422–427, 429–465, 634–691, 896–911, 943–1007`; `workflow_coordinator_bindings.py:379–391`; `workflow_store.py:328–347` under `tooling/agent_workflow/`.
- Installed SDK 1.26.0: `mcp/server/lowlevel/server.py:434–488, 521–530, 727–761`; `streamable_http_manager.py:30–44, 86–103, 143–199, 215–223`; `transport_security.py:12–43, 102–127`.
- Installed host embedded source: remote config around byte `148626405`; file/environment substitution `149418602–149418935`; file-config load `149458918`; scoped MCP route schema `149627441`. String-pool occurrences near 94 MiB are not handler implementation evidence. Source offsets are locators for the hashed executable, not an end-to-end runtime proof.
