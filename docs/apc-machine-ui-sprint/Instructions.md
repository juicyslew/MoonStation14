# APC machine UI — sprint instructions

## Status and scope

This is the implementation handoff for the first MoonStation14 machine UI: an APC control screen, inspired by Space Station 14's APC. The owner has authorized this bounded APC system. It does **not** authorize a generic UI framework or any unrelated system work. The older [device UI platform proposal](../device-ui-platform-sprint/proposal.md) is background only: its superseded UI-first ordering is not current, but its Stage 0 API-audit ideas remain useful. APC is the newly authorized concrete first UI; do not treat the old proposal as authorization for a broader spike.

Keep the existing game-system organization, in particular `src/main/java/com/juicyslew/moonstation14/ms14/power/**`, and the established block/entity registration boundaries. Target Minecraft **1.21.1** and NeoForge **21.1.224** (see `gradle.properties`). Audit those exact local APIs before choosing menu or transport patterns; do not assume examples for another version apply.

### Required outcome

- Right-clicking an APC opens a real client screen through normal authenticated Minecraft player/menu transport.
- The screen presents a server-authoritative main breaker control and truthful current battery state (bar and percentage). Show external-power state and load only if the local runtime can provide meaningful, current, well-defined values; otherwise omit those fields or explicitly present an honest unknown/unavailable state. Never invent values or imply SS14-equivalent power semantics.
- Keep the APC battery and breaker owned by `PowerDeviceBlockEntity`; retain its save/load behavior and existing server-side power runtime. UI transport is not a new owner for this state.
- Refresh every viewer of the same APC from authoritative server state after mutation/runtime updates. Closing, removal, unload, context changes, or invalid session context must not leave a usable stale control session.
- Keep server and client classloading separated. Server-side code must not load client Screen classes.

### Explicit exclusions

No atmospherics or lifecycle changes; no changes to Mind, MobHarness, character/action authorization, movement, or legacy gates; no gameplay action UI; no copied SS14 code; no additional sprites/assets for this sprint. One licensed static APC PNG has already been imported and is documented in [assets-and-attribution.md](assets-and-attribution.md); that existing image is the sole exception, not permission to add more assets. Do not add per-channel controls, emag/lock behavior, engineering ID access, or other SS14 mechanics unless the corresponding local model and authorization already exist and are explicitly in scope. Do not claim full SS14 electrical semantics. Do not run Minecraft manually; owner two-client acceptance is pending and remains an explicit owner-run gate.

## Local evidence to preserve

- `PowerDeviceBlock.useWithoutItem` currently handles APC interaction by toggling the breaker directly. Replace this UI-less click behavior with open-screen behavior while preserving the existing safety contract; non-APC devices continue to pass.
- `PowerDeviceBlockEntity` owns `energyJoules` and `breakerClosed`, validates server-side breaker toggles, marks persistence dirty, sends block updates, and serializes APC state. Preserve this single source of truth and persistence.
- APC capacity is 1 MJ (`PowerDeviceRules.APC_CAPACITY_JOULES`). The live power runtime solves every 20 ticks (`PowerRuntime.TICKS_PER_SOLVE`), but this does not itself establish trustworthy UI load telemetry. Audit source/port knowledge, staleness, units, and semantics before displaying live external power or load.
- `MS14Bridges`/`MS14Provider` bridge dual holder traits/attachments and data components. They are not an alternative store for APC BlockEntity-owned state. Do not put APC breaker/battery state into holder traits, provider bridges, or a parallel attachment.
- Upstream design reference is the local SS14 checkout at `C:\Users\William\Documents\SS14-dev\space-station-14`, APC UI files under `Content.Client/Power/APC/UI/`, the associated APC system/UI files, and `Resources/Textures/Structures/Power/apc.rsi` with `meta.json`. Reference content is heavily modified from tgstation; consult [assets-and-attribution.md](assets-and-attribution.md). Reference behavior is inspiration, not a local contract.

## Authorization and validation boundary

An authenticated `ServerPlayer` menu session authorizes only this ordinary device UI interaction. It is **not** MobHarness action authorization or proof of possessed-body access. For every mutation, server-side code must validate the currently open UI/menu session, its bound APC identity, same server level/dimension, currently loaded device and matching APC block/entity, current range and permission (`mayBuild` or the audited equivalent), and current request/session revision as applicable. Never trust a client-supplied position/device as authority, client state, or a stale/late request. Do not load chunks to satisfy a UI request. Fail closed when the device/context is unknown or unloaded. Reject without mutation and safely close/invalidate stale sessions.

Opening a session should be bound server-side to the actual clicked APC; audit exact 1.21.1/21.1.224 `MenuProvider`, `MenuType`, Screen registration, client class isolation, and open-menu lifecycle/handshake before implementation. A button action should carry only a bounded intent (for example, desired breaker state or toggle intent), not an authoritative device coordinate or a replacement snapshot. Prefer idempotent desired-state requests if that fits the audited transport. Verify the breaker state again on the server before applying. On accepted changes, synchronize persistence, block/runtime state, and every open viewer. Local presentation may respond immediately to a click, but must reconcile to the authoritative server response; it must not be presented as confirmed device state before that response.

## Required working practice

1. Read [architecture.md](architecture.md), [milestones.md](milestones.md), and [assets-and-attribution.md](assets-and-attribution.md), then inspect current call sites and tests before editing.
2. Complete M0's pinned API and telemetry audit before implementation. Stop for owner review if safe authenticated binding, dedicated-server isolation, current-session validation, or a truthful UI state cannot be established.
3. Keep changes limited to APC UI and directly required power/UI tests and registrations. Respect existing project game-system organization; propose rather than silently replace established architecture.
4. Add focused JUnit tests and dedicated-server GameTests at the seams each milestone specifies. Do not report tests as passed unless run and observed. Do not claim connected behavior from FakePlayer-only or helper-only tests.
5. Do not launch Minecraft/client/server manually. Record owner connected manual acceptance as pending until the owner performs and reports it. There is no server-wait label/pulse in the current UI; do not ask testers to validate one.
6. Validate only relevant automated targets when practical, and report exact commands/results and any unrelated failures. Do not modify atmospherics/lifecycle work to make validation pass.

## Current milestone status

- **M0 — platform/API audit:** recorded in `api-audit.md`.
- **M1 — authority/session:** implementation and available automated verification are recorded in the handoff and tests; connected transport still needs owner retest.
- **M2 — visual presentation:** implementation and available automated verification are recorded in the handoff; connected rendering/refresh still needs owner retest.
- **M3 — server tests:** automated server-side coverage has been run; see the exact reported results in `current-handoff.md`.
- **M4 — owner feedback fixes:** fixes are present and automated-verified, but connected owner retest is pending. The sprint is not done or accepted.

## Definition of done

Automated focused tests prove breaker/session authority, state persistence, truthful battery presentation calculations, rejection of invalid/stale/unloaded contexts, and synchronization behavior at the available test seams; dedicated-server startup/tests show client UI classes remain isolated. Owner-run two-client acceptance verifies open, valid breaker change, refresh in both viewers, stale/wrong context rejection, close/reopen, and removal/unload handling. Until that connected acceptance is supplied, mark it pending and do not claim the sprint fully accepted. Record API/telemetry limitations and any tests not performed. Do not require an out-of-range walk-away test on the vanilla screen where movement is blocked, or infer failure from battery charge not changing on a breaker toggle: charge behavior depends on available external power and elapsed solve/load conditions.
