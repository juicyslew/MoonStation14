# Exported default-airlock integration

Closed by owner acceptance on 2026-10-08. Start from `HANDOFF.md` for the durable
state and upcoming-design starting points; this file retains detailed history.

Latest jar: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`106A3EEE97C004B774B38C4F3E84FD88943F265F689EC3BD4C646BEAC0392600`.
Install on both client and server and restart both. Earlier hashes below are
historical; current knockdown correction evidence is at the end.

## Scope

Replace the procedural default airlock with the modeler's exported Minecraft Java
component models. `.bbmodel` remains an external authoring master; no Blockbench
interpreter or animation-library dependency is introduced.

Existing controller state, resolved `AirlockData` timings, wire operations, power,
collision, atmosphere sealing, and networking are retained. Server examination
geometry changes with the model, so client and server must use the same jar.

## Asset source

`C:\Users\William\Documents\Minecraft Mod Dev Files\Asset Building Workspace\assets\Structures\Doors\Airlocks\default`.
The export manifest's models, textures and animation metadata are installed in
their intended resource locations. See `../airlocks-sprint/NEW-MODEL-ASSETS.md`
and the packaged model-directory `ATTRIBUTION.md` for credits and license.

## Architecture and invariants

- Minecraft bakes the component models. The block-entity renderer binds existing
  synchronized presentation state and applies motion; it does not parse model
  JSON every frame or generate another hardcoded mesh.
- Canonical export coordinates face -Z. A 180-degree local Y adaptation preserves
  existing block-facing semantics. Leaves travel 6.5/16 blocks outwards using the
  existing two-stage openness function and resolved controller durations.
- Translated quads are clipped to local `[0,1] × [0,2] × [0,1]` before facing
  transforms. UV interpolation preserves artwork rather than stretching it;
  clipped geometry is not replaced with synthetic cap faces.
- Shared `AirlockGeometry` reads the same authored component boxes once, applies
  matching motion/clipping, and supplies piecewise examination hits and outline
  bounds. It must not turn the open aperture into a solid selection box.
- Maintenance components follow their owning leaf. Power, visible bolt lights,
  panel presentation and denial use existing replicated fields. Unsupported
  welded/emergency/effect states are retained as assets, not fabricated gameplay.
- The airlock item references the exported closed model and authored display
  transforms. Render bounds stay within the root's one-wide, two-tall block volume.

## Exported-model baseline validation (before follow-up fixes)

- `cleanTest test build compileGameTestJava --max-workers=4`: successful;
  2,889 JUnit tests passed, zero failures/errors/skips. Includes CPU-side Minecraft
  `BlockModel` parsing and `FaceBakery` verification of 13 component/state models,
  163 elements and 978 authored faces, runtime clipping and normalized atlas UVs.
- Full native GameTests: 708/708 selected/discovered; 696 passed, 12 required
  failures, none incomplete. The suite is not green. The two failed airlock cases
  passed a focused rerun (`airlock_log_pulse,airlock_power_cycle`, 2/708 selected,
  2 passed). No examination-selection or airlock-geometry test failed. Other
  failures were not investigated as part of this rendering change; they are not
  claimed to be proven pre-existing regressions.
- Logs: `build/validation-logs/airlock-model-final-unit.log`,
  `airlock-model-full-gametest.log`, and `airlock-model-focused-airlock.log`.
  Failed native artifacts remain in `build/gametest-airlock-model-fixture`.
- All 44 manifest resources are packaged, with attribution and the new item
  parent verified. Jar: `build/libs/moonstation14-0.0.1.jar`, 7,223,836 bytes;
  SHA-256 `7FC195957065F0484FBDAB2493ABE51F076343108FACA76231EBB7A1B8EAF464`.
- Independent source review passed after UV, lighting, boundary-degeneracy and
  maintenance-parent transform corrections.
- Client startup smoke was deferred to avoid interfering with the owner's active
  client in another workspace. No GUI input or client launch was performed, and
  no owner configuration changed. Default generated client arguments retain
  game directory `.` and contain no QuickPlay/autoconnect options.

The model was handed back for owner manual visual testing, not visually accepted;
use `MANUAL.md`. The subsequent fixes below replace this baseline artifact.

## Manual-test follow-up fixes

- The redundant `airlock_screwdriver` item and prototype aliases are removed.
  Keep the ordinary `screwdriver` ID: airlocks consume its resolved `Screwing`
  quality and disposal consumers already use it. Its ordinary loose-object profile
  and tools-tab entry replace the duplicate's role. The retained texture filename
  refers to generic SS14 screwdriver art, not another item or a special capability.
- Unsafe partial-close solidification now attempts the existing powered `BUMP`
  shock on each captured occupant after crush effects. This supplies the missing
  closing contact for stationary, critical or already-stunned bodies; it does not
  add proximity activation. Root capture deduplicates both halves, and the phase
  transition supplies the contact edge without a per-tick scanner. Reopening and
  closing again creates a new contact episode. Safe obstruction aborts do not
  solidify or run the new shock path.
- Correspondence: pinned SS14 `SharedDoorSystem.OnPartialClose` enables collision
  before `Crush`; Robust contact processing can then generate `StartCollideEvent`
  for an overlapping body, consumed by electrification's `OnBump` handler.
  Minecraft executes the equivalent contact synchronously after solidification;
  exact upstream physics-step timing was not runtime-tested. See
  `../airlocks-sprint/ELECTRIFICATION.md`.
- Timing-wire behavior is unchanged: modifiers apply when auto-close is armed;
  changing the wire alone retains an already-running deadline.

### Follow-up validation and current artifact

- `compileGameTestJava`, `test assemble`: successful; 2,889 JUnit tests passed,
  zero failures/errors/skips.
- Filtered native tags `airlock_stationary_contact,airlock_crush_cycle,airlock_electrified_open`:
  5/711 selected/discovered, 5 passed, zero failures/incomplete. Live tests cover
  a stationary living Critical body, one 7-electrical plus 15-blunt application
  per solidification, no repeat within the closed episode, a fresh later cycle,
  shock paralysis lasting beyond the shorter crush stun, and safe-abort,
  power-loss and adjacent-actor negatives. Independent source/fixture reviews
  passed for both contact behavior and screwdriver consolidation.
- Evidence: `build/validation-logs/airlock-stationary-contact-gametest.log`,
  `airlock-stationary-contact-server.log`, and `airlock-stationary-contact-build.log`.
  Passing disposable contact-fixture directory was removed after log retention.
- The full native suite was not rerun for these focused fixes. Its earlier red
  baseline above is not replaced by the filtered result. No client was launched
  or owner configuration changed; in-world visual retesting remains outstanding.
- Current jar: `build/libs/moonstation14-0.0.1.jar`, 7,222,898 bytes;
  SHA-256 `6D0B17DA3B53574269CDFFA7E3AB8DDF2636600B005C41022B53786E51948A8F`.
  Install on both client and server, replacing the baseline artifact above.

## General static-contact correction

The first follow-up only restored electrified solidification contact. It did not
restore SS14's separate closed-state bump recovery and was too narrow for a body
left against a door.

Pinned `SharedDoorSystem.CheckDoorBump` (lines 717–728) checks actual contacting
entities for `DoorBumpOpener` and delegates normal opening eligibility. `Update`
(733–759) calls it when a scheduled active-door transition reaches Closed, not
as a global per-tick scan. The inherited human tag is not conditional on life
state, so a corpse can qualify; proximity without contact cannot.

The local counterpart checks bounded doorway contacts at that scheduled closed
edge. Side-face tangency uses a small numerical tolerance against the existing
Minecraft collision volume, not the thinner rendered mesh or a detection radius.
Retained medical corpses use the existing loaded-body predicate; contained,
incorporeal, removed and spectator bodies are not physical contacts. Static
opening still consumes resolved bump-opener composition and ordinary power/bolt
eligibility. Departmental credentials remain unsupported: current airlocks are
all-access, so this does not claim a local access-card implementation.

Contact, obstruction and crush are distinct queries. Opening recovery must not
produce another electrical shock for the same solid contact. Reopening and
solidifying again can create a new electrical contact. Safe obstruction can hold
the door open; a non-crushed side contact can reopen a completed close.

Preserve active crush waits: upstream `CurrentlyCrushing` redirects the timed
state progression to opening before Closed, whereas the existing local adaptation
reaches Closed before its separate two-second release. Its deferred-crush path
must therefore skip ordinary closed-contact recovery rather than release victims
early. SS14's crush loop has no alive-only gate; physical retained medical corpses
must not disappear from collision queries merely because native health is zero.

### Current validation and artifact

- `test compileGameTestJava assemble`: successful; 2,890 JUnit tests passed,
  zero failures/errors/skips.
- Focused native contact/crush/bump regressions: 9/715 selected/discovered,
  9 passed, zero failures/incomplete. Coverage includes stationary medical-DEAD
  and Critical contacts, repeated closing/recovery, safe obstruction, preserved
  crush retention, power loss, nearby/gapped-body exclusion and no duplicate
  closed-recovery shock. Independent final source/fixture review passed.
- Evidence: `build/validation-logs/airlock-static-contact-gametest.log`,
  `airlock-static-contact-build.log` and `airlock-static-contact-server.log`.
  Passing disposable static-contact fixture was removed after retaining logs.
- Current jar: `build/libs/moonstation14-0.0.1.jar`, 7,223,610 bytes;
  SHA-256 `88F8899E32C036C198432CEFF8121B3430B7CFB1E79C92F05104ACCA21C0EE3A`.
  This supersedes both earlier artifacts in this document. Install on both sides.
- The full native suite was not rerun for this correction; its earlier red
  baseline remains recorded above. No client was launched. Owner visual retesting
  is pending. Native fixtures use enrolled character bodies, not an authenticated
  player ghost handoff; source tracing confirms the retained player corpse keeps
  its explicit binding and resolved bump-opener composition after ghosting.
- Dead-body crush damage/paralysis are delegated to the existing shared effect
  systems without dead-specific overrides. Electrical contact geometry uses the
  existing Minecraft block collision volume, not the thinner exported visual mesh.

## Knockdown posture correction (latest)

Shock/paralyze applied stun and knockdown markers but did not set authoritative
posture. The actual player-character renderer and physical dimensions use the
posture attachment, so the legacy status-only render fallback was insufficient.

Resolved `knockdown.json` now composes `knockdown_posture` behavior, consumed by
`DefaultStatusEffectLifecycle` on application, refresh and reconciliation through
`PostureSystem`. This reuses the existing synchronized posture, physical pose,
dimension refresh and renderer path for all knockdown sources. Slip's redundant
direct posture call is removed; its launch/stun behavior is unchanged. Already-
down orientation and manual stand policy are preserved. Active knockdown blocks
get-up; expiry/removal uses existing timing, consciousness, medical and headroom
checks, not an unconditional stand operation. See `../KNOCKDOWN.md`.

- `test compileGameTestJava` and `assemble`: successful; 2,890 JUnit tests,
  zero failures/errors/skips.
- Filtered native tags `posture_knockdown,airlock_shock_posture,airlock_stationary_contact,airlock_crush_cycle,airlock_electrified_open`:
  11/716 selected/discovered, all 11 passed. Includes actual player-character
  harness physical dimensions, shared status projection, posture refresh/manual
  policy, stun-only behavior, recovery and electrified contact.
- A broader run including `posture` selected 36/716: 35 passed, one failure,
  `jumpingTautPullerFallsAndLandsAfterInputStops` (trusted body-pull admission
  denied). This named case also failed before this correction; its cause was not
  investigated here. The filtered pass does not replace that broader red gate.
- Logs: `build/validation-logs/airlock-knockdown-focused.log`,
  `airlock-knockdown-server.log`, and `airlock-knockdown-broad-posture.log`.
  Passing final disposable fixture removed after log retention. Final independent
  source review passed; no client launch or automated GUI input was performed.
- Latest jar: 7,224,139 bytes; SHA-256
  `106A3EEE97C004B774B38C4F3E84FD88943F265F689EC3BD4C646BEAC0392600`.
  Owner visual retesting remains pending; use `MANUAL.md` step 9.
