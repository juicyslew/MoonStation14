# Stripping and dressing others

## Status

Closed with owner approval on 2026-10-09. Current state, architecture and final
validation are indexed in `HANDOFF.md`; historical evidence remains in
`VALIDATION.md`, and `MANUAL.md` is a retained retest checklist. Closure does not
claim an assistant-run client or a green full native suite. All changes remain
unstaged/uncommitted; no commit or push was performed.

## Scope

Open another eligible character's inventory and perform timed removal/insertion
of worn items and held items. Use resolved target composition and inventory
templates, not a second hardcoded human inventory. Preserve item ownership,
dependent-slot behavior, existing storage contents, and observer appearance.

### Required panel

- A compact, draggable window in the **existing transparent pointer workspace**.
  It must not pause play, cover the screen, or capture movement keys. Walking,
  turning and existing pointer/camera controls remain usable while it is open.
- At most **three slot columns**. Hotbar equipment and hands go above clothing.
  All cells use the clothing panel's exact live tile size and grid spacing:
  normally 15px cells with 1px gaps, including hand cells. Do not use the owner's
  larger 18px hand tiles here. Narrow-screen/GUI-scale adaptation is shared.
- Preserve clothing's resolved relative coordinates and intentional empty cells.
  Do not flatten or reorder the clothing arrangement into a packed list.
- Title/target identity, close control and draggable chrome; shared viewport
  clamping, focus and front-window input priority. Slot artwork and item painting
  reuse existing assets/renderers. No duplicated HUD inventory behind the window
  may receive a click through it.

Proposed human grid, top to bottom (every cell has the same size):

| Column 1 | Column 2 | Column 3 |
|---|---|---|
| ID | Back | Belt |
| Left hand | Right hand | |
| Pocket 1 | Pocket 2 | Suit storage |
| Eyes | Head | |
| Neck | Mask | Ears |
| Jumpsuit | Outer clothing | Gloves |
| | Shoes | |

This is presentation grouping, not a new gameplay template. Only declared slots
and actual target hands appear. Blank cells are not inventory or drop targets.

### Interaction

- Integrate opening into normal world activation (currently E), selecting an
  unblocked, eligible other body before activating a block behind it. Reuse
  `BodyHandClient`'s normal-world-use routing; preserve Alt/Shift actions,
  examination, medical use, construction and storage input priority. No new
  generic verb-menu framework or conflicting dedicated hotkey is required.
- Clicking an occupied target slot starts removal. Clicking an empty target
  slot with an active held item starts insertion. Invalid actions do nothing;
  no automatic occupied-slot swap or client-side transfer chain.
- Follow upstream pickup-or-drop behavior for removal, including full actor
  hands. Do not import self-equipment's empty-active-hand click restriction.
- Reuse existing timed progress and cancellation presentation. Moving while
  an operation is active can cancel it; **having the window open never prevents
  movement**. Looking away is not itself a new cancellation condition.
- Preserve occupied-pocket hidden presentation. The viewer may act on an occupied
  hidden slot without receiving its real icon/name. Empty pockets remain empty.

## Architecture and required reuse

1. Extend existing typed character, hand, clothing and inventory-template data
   with the relevant supported stripping composition/defaults. Live admission,
   timing and rendering consume resolved values. Do not introduce a separate
   universal timed-policy catalog or put delay fields on the Stripping marker.
2. A bounded, authorized strip-view projection is distinct from owner inventory
   credentials and public appearance. Reuse `ItemPresentation` sanitization;
   omit nested grids and hidden-pocket identity. Capture exact item identities
   server-side when starting an action and revalidate them through completion.
3. Implement the missing cross-character transfer consumer using immutable
   `LiveHands` candidates, shared fit/dependency logic, staged drops and
   `LiveHandsCommitGateway.batch`. Reuse or narrowly extend existing pure removal,
   insertion and conditional publication/compensation code. Do not copy the
   entire world-grid transaction service or create another attachment writer.
   Check for additional reuse before adding each operation.
4. Reuse `TimedInteractionSystem` for per-tick validation, completion, damage and
   actor-movement cancellation, duplicate handling, and progress replication.
   Add only the hand/target-distance semantics this consumer actually requires.
   No items change ownership at operation start or during progress prediction.
5. Implement `PointerOverlayWindow`, register through `PointerOverlayRegistry`,
   and reuse `WindowGeometryController`. Extend/extract the smallest shared
   responsibility in `EquipmentPanelLayout`/`HandBarLayout` for cell sizing and
   clothing coordinates. No new Screen, workspace manager, or copied drag engine.

`BodyBodyGridTransfer` moves between grids on **one body**, not two characters.
`BodyGiveService` handles server `/give` grants, not stripping. Neither is a
ready-made cross-character transfer; their names must not justify misusing them.

## Ordered work

1. **Composition and policy:** finish the narrow audit gates in `PARITY.md`; map
   upstream defaults into typed local composition and inherited template fields.
   Test resolution, defaults, capability absence, and live timing consumers.
2. **Authority, view and timed transfers:** implement authorized target views,
   removal/insertion for equipment and hands, exact-source revalidation,
   conditional two-body publication and dependent drops. Test failure paths
   before wiring client gestures. Preserve concurrent distinct-item removals.
3. **Shared panel integration:** shared grid metrics, overlay registration,
   draggable chrome, normal activation, slot clicks and progress feedback.
   Verify every approved pointer host routes input once and passes movement.
4. **Integration acceptance:** independent source/reuse/parity review; full unit
   tests, targeted native tests, one full native run for the broad integration,
   then a concise connected-player manual check. Run expensive checks serially.

## Acceptance

- Other-player equipment and hands can be removed/filled through authoritative
  timed actions. Actor capability/state and target composition control access;
  no blanket target-can-act requirement that rejects corpses.
- Verified slot/hand/clothing timings and critical/dead modifiers come from live
  resolved composition/state. Source replacement, ineligible fit, changed actor
  hand on insertion, range/access loss, damage and movement cancel as specified.
- Hidden pockets, stale replies, target removal, body/epoch changes, disconnects
  and competing actors cannot expose unintended contents or cause wrong transfers.
- Whole stacks, tokens, target dependent drops, bag/belt grids and observers'
  appearance remain conserved/correct through success, cancellation and failed
  publication. The existing self-equipment path remains intact.
- Panel columns, original clothing positions, exact shared cell sizes and hit
  rectangles pass layout tests at normal and narrow widths. Dragging and walking
  with the panel open are verified in-game; unit tests alone do not certify pixels.
- **All stripping and related equipment/storage/timing tests pass.** Report full
  native selected/total, pass/fail/incomplete counts. Unrelated failures are
  recorded separately, not silently waived or pulled into this sprint.

## Outside this slice

Opening or searching the target's backpack/belt contents, drag/drop transfer
chains, automatic clothing swaps, new restraints/uncuff systems, pickpocket skills,
stealth equipment content, radio/access functions, and new wearable meshes.
Do not add unsupported upstream components solely to resemble YAML. Preserve
existing authored assets and attribution; import any necessary hidden-slot artwork
from SS14 with provenance. No new stripping sound is currently established.

## Starting points

- `../equipment-sprint/HANDOFF.md`, `MERGE-MAIN.md` and `PARITY.md` there.
- `../hands-inventory-program/HANDOFF.md`.
- `../timed-interactions-ingestion-sprint/HANDOFF.md` (especially new-consumer gaps).
- `../examination-sprint/HANDOFF.md` for target identity/input coexistence.
- Local code paths and verified upstream references in this sprint's `PARITY.md`.
- `HANDOFF.md` for the closed sprint state, `VALIDATION.md` for evidence and
  `MANUAL.md` for a retained retest checklist.
