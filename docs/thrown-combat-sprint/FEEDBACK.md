# Owner feedback revision — 2026-11-04

Closed by the owner on 2026-11-05. See `HANDOFF.md` for final state. The final
targeted repair automatically converts the old saved plain-K Actions binding to
Ctrl+K after Options registration, rebuilds lookup, drains stale clicks and saves
once. It supersedes the earlier manual-rebind guidance. The owner declined further
validation after this small repair; no replacement gate was submitted for it.

## Latest owner observations

- Removal icon accepted; approved armor models look good enough.
- Returning self-hits, including ricochets, work; prone hypodart retrieval works.
- Moving-bola hit consistency and the remaining K/self-turnover conflict need investigation.
- Airborne catching is acceptable for now. Per-item catchability restrictions
  (for example bolas/hypodarts versus grenades/jugs) are explicitly future work.
- Actions-window utility and the behavior of a second bola against an already
  ensnared target are open questions, not new behavior requirements yet.

These are partial manual observations, not sprint closure or blanket acceptance.

## Current requested follow-up

- Fix moving-target bola misses at ordinary movement/throw speeds. Establish a
  regression in authoritative contact handling; do not dismiss the report as prediction.
- Move the Actions window to **Ctrl+K**. Plain K/Shift+K remain turnover controls;
  direct disposal climb moves to Ctrl+J. Menu-chord ownership suppresses an old
  saved Ctrl+K climb binding; owner settings are not overwritten.
- Explain the complete pinned SS14 response after a second ensnare is rejected,
  distinguishing the ensnare cap from collision/landing physics.
- Do not add catchability restrictions or expand Actions-window management features.

The moving-hit correction handles a concrete entry-clip hole: when the projectile's
segment starts inside the target's expanded bounds, the entry-only AABB clip returns
no contact. The shared thrown-contact helper now accepts that existing overlap,
retaining the old sweep for outside starts. This is not a general relative-motion
CCD or lag-compensation implementation. Regression coverage includes the old empty
clip and a native target moving at ordinary speed into a bola's start bounds.

## Scope and acceptance

1. Import the approved basic armor delivery through the managed asset workflow,
   then bind its actual runtime assets through resolved equipment prototypes.
2. Use a recognizable temporary removal symbol; keep hotkey labels outside icons.
   Tighten capability-strip edge margins without changing arrival-order allocation.
3. Plain K opens Actions; Shift+K retains body turning. Honor rebound modifiers,
   including quick press/release events, without breaking numbered capability keys.
4. Make a floor-clipped attachment on another prone body selectable/removable,
   retaining reach, occlusion, exact source identity and own-attachment exclusion.
5. Suppress initial thrower overlap, but allow normal thrown-contact damage after
   the object clears its thrower and returns. Preserve contact-entry suppression,
   attribution, embedding, injection and item conservation.
6. Preserve the EVA behavior the owner reported working.

## Parity decisions

Pinned SS14: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- Ordinary embedded-item removal does **not** cancel on movement:
  `SharedProjectileSystem.cs:44–62` leaves `DoAfterArgs.BreakOnMove` at its false
  default (`Content.Shared/DoAfter/DoAfterArgs.cs:79–119`). Keep this behavior for
  both self-removal and removing from another actor. Bola's explicit
  `canMoveBreakout: true` independently permits movement during breakout.
- Upstream `Content.Shared/Throwing/ThrownItemSystem.cs:62–79,93–115` excludes the
  thrower for the entire thrown state. Post-separation self-hits are an explicit
  owner-requested Minecraft 3D adaptation, not an SS14 parity correction.
- The temporary removal icon is a project-authored cyan outward/up-left arrow,
  selected by the existing action prototype; it is not an imported SS14 sprite
  or a replacement wearable model.

## Approved armor boundary

Request: `thrown-combat-basic-armor-wearables-20261103-r1`.
Delivery: `8146d749484b087e1d929f7d5edc267430bc3f6bf92c205e6f553010bb29c55f`.
The owner explicitly authorized importing this exact delivery without overwrites.
Independent asset review inspected the hash-bound gallery and identified runtime
schema/material integration as a separate responsibility. The managed import is
complete; all six imported files match the approved hashes. Resolved equipment
profiles and the production client bundled catalog now include both armor models.
The existing authored renderer routes the single whole-helmet mesh as translucent;
it does not also draw the supplied separate shell/visor meshes. See `ASSETS.md`.

## Current implementation

- Capability icons retain their 24×24 logical image area in a 28×40 cell. Key
  labels occupy a separate line below; paint, pointer geometry and adaptive column
  placement share that cell layout. Viewport resize invalidates the painted cache.
- Capability-strip and shared chat/status top/right external margins are one GUI
  pixel; hand/inventory bottom spacing remains two. Internal window padding is unchanged.
- The menu key rejects mismatched modifiers at the input event, so releasing Shift
  before the next tick cannot turn Shift+K into plain K. Rebound menu chords remain valid.
- Attached-item selection extends only its top face by the existing 0.2-block
  tolerance. Client nomination and server reach/occlusion use the same bounds;
  ordinary loose items and own-attachment world exclusion are unchanged.
- Per-flight thrower clearance uses physical separation, not a timer. Later contact
  re-entry goes through ordinary typed thrown damage and attachment handling.

## Validation boundary

The previous merged-source gate PASS is historical. This feedback revision needs
fresh focused checks and a replacement OWNER_READY gate. Automated geometry and
material-routing checks do not establish GPU appearance, fit/poses or owner acceptance.
