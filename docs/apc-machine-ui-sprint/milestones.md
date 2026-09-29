# APC machine UI — milestones and acceptance gates

Milestones are ordered gates. Completing an earlier milestone does not automatically authorize scope beyond this owner-authorized APC UI. Stop and report blockers rather than weakening authority, editing unrelated systems, or fabricating acceptance.

## M0 — pinned API, interaction, telemetry and asset audit

Inspect local NeoForge 21.1.224/Minecraft 1.21.1 APIs and repository patterns for menu open, server/client registration, dedicated-server isolation, action/state transport, menu validity, multiple viewers, and lifecycle close/invalidation. Trace current `PowerDeviceBlock.useWithoutItem`, `PowerDeviceBlockEntity`, its rules/serialization, block-entity registration, power runtime's state update path, and tests. Audit whether external power/load are currently supportable and truthful; document units, knowledge/unknown states, and update cadence. Inspect reusable local APC sprites and reference asset metadata/provenance.

**Gate:** short written map in implementation notes or code review establishes a version-appropriate safe route with authenticated player and bound device, dedicated-server isolation, stale request rejection, persistence retention, and a clear truthful minimum UI. If any cannot be established, stop and request owner review before implementation. Remove external/load from UI scope if their telemetry cannot be proved.

## M1 — server-owned menu/session and breaker control

**Implementation status:** server menu/session foundation only. `api-audit.md`
records the pinned route. The server opens a UUID-bound menu, exposes
authoritative breaker, revision, and bounded battery-percentage menu data, and accepts revision-checked,
session-bound breaker intent only from the current authenticated menu. No
client Screen is implemented in this milestone per task scope. End-to-end menu
GameTest and connected multi-viewer evidence remain open.

Replace direct right-click/toggle interaction with opening a server-bound APC menu. Add minimal transport types and (in the later client milestone) client Screen registration using audited APIs; keep client classes isolated. Screen shows current main breaker state and a bounded control. On every mutation, server validates current open session, identity, current APC block/entity, loaded context, level/dimension, range, and build permission. Preserve BE ownership, existing permission intent, NBT persistence, and block/runtime state. Ensure a valid action refreshes every viewer; stale/wrong-session/wrong-device/unauthorized actions reject without mutation.

**Gate:** focused JUnit tests cover pure snapshot/percentage/request validation helpers as applicable; dedicated-server GameTests exercise server-side valid breaker change, permission/range rejection, wrong/replaced device/session rejection, and unchanged state on reject. No helper-only test is called an end-to-end menu proof. Verify the server does not load client Screen/UI classes.

## M2 — battery snapshot and truthful screen

Bind the existing server-authoritative energy to the session snapshot. Display a battery bar and percentage derived from energy / 1 MJ capacity with safe bounds, plus breaker state. Explicitly omit or visibly mark as unavailable any external-power/load item not proved by M0. Refresh on relevant BE/runtime state change at a bounded rate; handle multiple open viewers without stale rollback. Use only already-available APC 2D art, subject to recorded attribution/license requirements.

**Gate:** unit tests cover zero, full, intermediate, over-capacity/clamped or invalid-value handling according to local BE rules and presentation conversions. GameTests prove energy survives menu close/reopen and BE save/load, refreshed snapshots reflect server state, and old snapshots cannot overwrite newer state. If test seams cannot establish a case, record it open rather than claiming proof.

## M3 — stale context and lifecycle rejection

Exercise invalidation/rejection for APC replacement/removal, unloaded chunk without force-load, level/dimension or player context change, close/reopen, disconnect/session teardown where harness permits, stale/late action, and multiple viewers. Verify open handlers and per-action checks fail closed; do not rely on a client close packet. Verify breaker persistence and existing power runtime behavior remain intact.

**Gate:** dedicated GameTests and JUnit cover the cases that can be faithfully exercised in their harness. Tests that cannot create genuine authenticated menu sessions are explicitly limited; do not overstate them. No dedicated-server classloading regression. No atmospherics/lifecycle/power semantics changes outside the minimum direct UI seam.

## M4 — connected owner acceptance and handoff

Owner manually tests with two real connected clients: open the same APC on both; verify authoritative breaker state; valid authorized mutation and both-client refresh; rejected unauthorized/out-of-range or stale context; close/reopen; battery updates; device removal/unload behavior; and no stale late action mutation. Verify controls remain responsive and visually understandable. Record tested build/revision and scenarios. **This is owner-run; implementation agent must not manually launch Minecraft/client/server.**

**Gate:** until owner evidence is supplied, mark connected acceptance pending. FakePlayer-only tests, successful compilation, screenshots from an unconnected/local-only view, or a helper test do not substitute. Report any unavailable scenario and its residual risk. No automatic transition to generic UI, another machine, or broader mechanics.

## Acceptance summary / stop conditions

Done means M0-M3 evidence is recorded, focused automated checks actually run with exact outcomes, server/client isolation is verified, and M4 is owner-accepted or explicitly left pending without claiming full acceptance. Preserve all unrelated existing gates. Stop for owner review if the only apparent route trusts client-selected device state, fails to bind normal authenticated transport to a current menu, loads chunks on request, leaks UI classes to dedicated server, requires weakening permission checks, or needs lifecycle/atmospherics edits. Also stop if proposed readouts would be fabricated or sourced from unknown/stale data.
