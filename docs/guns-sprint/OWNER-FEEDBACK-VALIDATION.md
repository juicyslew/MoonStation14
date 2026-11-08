# Mk58 correction evidence

This candidate follows owner feedback after technical PASS
`run_4b7d9acb0beb24aab152cdbc`; it is not owner acceptance.

## Changed consumer boundaries

- **Racking:** `HeldUseRequestDispatch` retains distinct, identity-bound instant
  rack presses while their own request/refresh is unresolved. `BodyHandClient`
  drains once per tick against correlated acceptance and fresh proof; failed sends
  do not consume intent. Modifier chords, rejection and context/identity loss do not
  become later rack actions. Native fixture `registeredRepeatedEmptyMk58RacksPublishEachSound`
  checks three accepted rack cues, stale-token silence and no empty-fire text.
  The first correction gate exposed Mk58/Lecter rack cue IDs missing their
  registered `/cock/` path. Those data references are corrected, with a bundled
  cue audit covering event registration, `sounds.json` and sample files.
- **Aim/HUD:** the gun bore targets the actual humanoid head's local forward frame;
  tests compose the Mk58 display and shared grip transforms for both hands. Ammo
  counters use source-hand identity and resolved current/capacity data, not outer
  magazine stack count or an assumed right-hand position. The live HUD uses
  `HandAmmoHudProjection` for standalone containers without a gun selection and
  reserves a real paint row for each visible hand, including single-hand layouts.
  Gun/magazine and revolver/magazine rows are independent; numbers and glyphs do
  not share the same space.
- **Reloading:** click precedence follows pinned SS14 hand-button behavior. Explicit
  dragging can use either captured hand without selecting it. Named-source updates
  preserve token/count/reservation/revision checks and one atomic commit; old
  active-only use callers retain their active-hand restriction. Fresh magazines
  materialize once on insertion; initialized empty magazines do not refill.
  `rawCreativeMk58MagazineInsertsThenRacksAndFires` exercises real replacement
  acquisition, inactive-source insertion, selection preservation, chambering and firing.
- **Casings:** resolved cartridge presentation controls spent-only cosmetic outputs,
  native client voxel collision/bounce/rolling and one landing cue. Real live-round
  ejections remain recoverable. The registered Mk58 fixture checks actual outbound
  casing payloads, absence of duplicate spent entities and a live racked round.
  SS14 casing/shell sounds and original sprites are retained; unknown per-file audio
  provenance remains recorded in `docs/audio-unknown-provenance.md`.
- **Controls window:** the existing held proof/window owner now uses shared geometry,
  title drag/capture/release, close chrome, compact measured layout and row projection.
  Visibility/retention is independent of pointer capture. Established locked-cursor
  world-click routing is preserved, rather than globally enabling pointer-window hits.

## Current bounded checks

Fresh focused author checks: **57 exact classes, 322 tests, zero failures/errors/skips**;
all three Java compile targets and JAR packaging passed. The final native fixture
source received a subsequent successful `compileGametestJava` dependency check.
Current replacement evidence: `build/guns-sprint/owner-feedback-r3/final-evidence/`.

The production-painter CPU preview manifest contains nine current Mk58/Lecter cases
at 320×240 and 640×360, including disabled-magazine and close/title/action highlights.
Independent reviewer `ses_e4f02ed73ffeOQfxIkRuJdqQj2` reopened all nine current images
and reported PASS: readable real titles/labels/reasons, unobscured rows and controls.
Final regeneration was byte-identical to those reviewed images. Casing scale uses
the resolved generic loose-item visual canvas rather than an arbitrary tiny quad.
These PNGs do **not** establish native GPU appearance, dragging or cursor behavior.

Four additional source-hand ammo HUD previews use the live projection, layout and
counter painter at both viewport sizes and hand orders. Independent reviewer
`ses_e4e7c24e7fferin1alJ4aFAlP4` opened all four and reported PASS: gun 7/10 and
standalone magazine 3/10 follow their corresponding hands, remain readable and do
not overlap glyphs. Final regeneration matched all 13 reviewed image hashes.

The revised native fixtures and readiness suites await the serialized integration
gate. The focused owner retest is [MK58-MANUAL.md](MK58-MANUAL.md), not an eight-gun
walkthrough request. Do not infer owner acceptance from these checks.

The first correction gate (`run_97a114d2948d694501cee8dc`) passed focused JVM and
readiness checks, then 270 / 272 selected native tests (1,443 total). Besides the
audio reference issue, the new reload fixture incorrectly selected a magazine by
global entity scan in a shared test world. It now binds to the accepted DROP
transition's world UUID and host token, retaining semantic full-stack and ten-round
preservation assertions. Both failures require a fresh replacement gate verdict.
The second correction gate (`run_0f1ce17b4c3933bdf2da4be5`) passed 271 / 272:
its remaining new reload-fixture failure used the pre-DROP hand revision for the
replacement pickup. It now refreshes proof after DROP, checks the empty source
hand remains selected until the explicit gun selection, and includes a read-only
10/10 projection assertion for the actual registered raw magazine.
