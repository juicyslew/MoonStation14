# Stripping, dressing and spatial calibration handoff

## STATUS

Closed with owner approval on 2026-10-09. Changes remain unstaged/uncommitted;
closure does not authorize a commit or push. Owner closure is not a claim that
the assistant launched a client or that the full native suite is green.

Delivered timed equipment/hand transfers between characters, a compact draggable
target inventory, ordinary-villager readiness, equipment bug repairs, and approved
grounded-speed/interaction-reach calibration. See `VALIDATION.md` for historical
and final evidence; `MANUAL.md` is retained as a concise retest checklist.

## ARCHITECTURE

- Typed resolved character composition supplies actor `Stripping`, target
  `Strippable`, and hand stripping permission. Inventory slots supply timing and
  hidden-pocket metadata; Clothing supplies additive delay. Inventory eligibility
  is independent of `Hands.CanBeStripped`, which gates only hand actions.
- `StripService` authenticates actor authority, maintains one private target view
  per viewer, and starts existing `TimedInteractionSystem` operations. Requests
  correlate actor epoch/sequence and admission revisions. Progress uses exact
  captured source identity, not whole-body revision equality: unrelated changes
  and distinct-source removals can coexist.
- `BodyStripTransfer` reuses immutable `LiveHands` candidates, fit/dependency
  policy, staged drops, and `LiveHandsCommitGateway.batch`. Read-only preflight
  shares transfer planning. Conditional compensation proves both owners before
  restoring; ambiguous ownership is recovery-required, not a clean denial.
  Publication failure after committed ownership does not undo successful transfers.
- Strip snapshots are separate from owner credentials and public appearance.
  Hidden occupied pockets carry occupancy without real item presentation; visible
  items use sanitized `ItemPresentation`, excluding nested private contents.
- `StripWindow` reuses the pointer workspace/overlay registry, window geometry,
  clothing-grid metrics, slot chrome, item painter, and movement-friendly host.
  `StripTargetBinding` tracks identity independently of contents, so pending OPEN
  and contentless unavailable replies do not accidentally close the panel.
- Normal character joins bootstrap composed Mob inventories after enrollment.
  Explicit `LiveHands.initialize` returns existing compatible state unchanged;
  incompatible/materialized-null state remains rejected. Viewing never initializes
  target gameplay state.

## IMPORTANT FILES

Java paths below are under `src/main/java/com/juicyslew/moonstation14/`:

- `ms14/stripping/`: capability, timing, shared strip range, service, networking,
  requests/snapshots; `client/`: session correlation, binding, layout and window.
- `ms14/hands/live/BodyStripTransfer.java`, `BodyHandBootstrap.java`, `LiveHands.java`,
  `BodyInteractionAccess.java`, and `LiveHandsCommitGateway.java`.
- `ms14/equipment/`: item/template composition, inherited merging, fit and
  quick-equip preference; `client/BodyAppearanceClothingLayer.java` fixes the
  mixed authored/fallback buffer lifecycle.
- `ms14/hands/network/BodyHandActionRequest.java`: equipment-move wire validation;
  `ms14/hands/client/BodyHandClient.java`: shared input/pointer integration.
- `eventhooks/ModEventHooks.java`: ordinary-character join bootstrap.
- `ms14/movement/CharacterMovementPolicy.java`: grounded speed conversion;
  `ms14/interaction/InteractionReach.java`: flat ordinary-interaction addition.
- `ms14/medical/MedicalTreatmentService.java` and
  `ms14/medical/syringe/SyringeService.java`: mapped body interaction reach.

Resolved resources are under
`src/main/resources/data/moonstation14/moonstation14/{character,inventory_template,equipment_item}/`.
Native coverage includes `StripTimedGameTests`, `BodyStripTransferGameTests`,
equipment/bootstrap tests, production medical/syringe reach predicates, and
`GroundedHarnessWorldStep` coverage. `PARITY.md`, `BACKEND-CONTRACT.md`, and
`ASSETS.md` contain supporting references. Start equipment follow-up from
`../equipment-sprint/HANDOFF.md`.

## SS14 CORRESPONDENCE

Reference checkout: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Human ancestry and defaults were checked through the species/inventory bases;
`SharedStrippableSystem`, inventory equip/unequip, Hands pickup-or-drop, and
DoAfter systems establish the gameplay correspondence.

- Slot stripping defaults to 4 seconds; human shoes/eyes/ears/pockets/suit storage
  use 3, jumpsuit/outer clothing/ID/belt/back use 6. Hand delay is 4; additive
  Clothing delay defaults to 0. Critical/dead targets multiply by 1/2 or 1/3.
  Zero duration maps to one local tick.
- Actions revalidate every tick/completion. Actor displacement and actor-target
  radial-distance change cancel above 0.3; damage threshold is 1 per event.
  Insertion retains its exact active source; removal permits active-hand changes.
  Same-source repetition cancels/blocks duplicates; distinct sources can run together.
- Full actor hands do not prohibit removal: prefer active pickup, another available
  hand, then actor-world drop. Dependent slots drop intact beside the target.
- `simple_coat` still lacks suit-storage permission. No item-ID exception is used.

## DECISIONS / WHY

- E opens an eligible, unblocked other body. Panel rows are ID/back/belt, actual
  hands, pockets/suit storage, then unchanged clothing coordinates/blank cells.
  At most three columns; every cell uses clothing sizing (normally 15px, 1px gap).
  Walking remains available. Leaving reach closes the panel; within-range LOS
  obstruction can leave it unavailable. Close-on-range is requested port UX, not
  established upstream BUI parity.
- Grounded walk and fast speeds both scale **1.25x** once at resolved-policy
  conversion. Raw human 2.5/4.5 remains unchanged; effective speeds are
  **3.125/5.625 blocks/s**. Acceleration/friction, status factors, geometry and
  ghost/observer flight are unchanged.
- Ordinary SS14-derived interaction reach receives a **flat +1.5 blocks**, not
  a multiplier. Stripping, treatment and syringe reach are **3 blocks**, using
  physical roots plus loaded physical-eye LOS. Existing Minecraft-specific aiming,
  pickup/block/device limits and analyzer 3.5 scan/3.0 UI policies remain separate.
  Movement/radial cancellation thresholds are not enlarged.
- Blank cards declare typed `quick_equip_preferred_slot_flags: [IDCARD]`.
  ID-first Z is an owner-requested departure: pinned SS14 can select earlier
  pockets for Small cards. Manual pocket fit remains allowed. Other items retain
  template order unless their resolved composition declares a preference.
- `MOVE_EQUIPMENT` retains canonical zero unused-grid fields consistently through
  construction/codec validation, fixing the pocket-to-ID drag crash.
- Utility Case physical size is `Ginormous`, using the shared size enum, matching
  ToolboxBase size. It remains an inert starter; internal storage was not added.
- The F5 crash came from retaining a fallback vertex consumer across authored
  texture buffer switches. Each fallback garment now reacquires its borrowed
  consumer; no layer flushes the caller's batch. Authored exports are unchanged;
  no first-person arm/item viewmodels were introduced.
- The unchanged SS14 hidden-information PNG has packaged attribution; see
  `ASSETS.md`. No audio was added.

## KNOWN GAPS

No target backpack/belt browsing, drag-chain transfers, new restraints/skills,
combat-mode gating, or functional PDA/access behavior is claimed. Generic upstream
BUI lifecycle correspondence remains incomplete. Positive connected-player packet
effects and GPU visuals were not assistant-tested; native authority rejects
FakePlayer and no accepted positive connected-authority fixture exists. Owner
closure records acceptance without inventing detailed manual test results.

## VALIDATION

Final serial compile/test/JAR build: **2,968 unit tests passed**, zero failures,
errors or skips. Filtered native: **368/732 selected**, all 368 completed/passed,
zero failures/incomplete. Full native: **732/732 completed, 729 passed, 3 failed**,
zero incomplete. Failures outside the changed scope:
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`livesourcesubstationapcbatteryandlampchain`, and
`liveapcmeterdrivesscheduledtwophasedoorandautoclose`.

Logs: `build/validation-logs/stripping-calibration-{build,focused-gametest,full-gametest}.log`.
The passing disposable focused fixture was removed; logs and full failure artifacts
are retained. Native reach cases cover 2.5 admission,
inclusive 3.0, beyond-range denial, wall LOS, and strip timed conservation.
Independent source reviews passed; tests do not certify rendered pixels.

JAR: `build/libs/moonstation14-0.0.1.jar`; verified SHA-256:
`F53B53861F485BD3D3B417D8B05261A8F094E1B4F9F15C955E1261939F7A48F3`.

## NEXT WORK

Use the shared conversion boundaries for future calibration; do not edit raw SS14
prototype speeds or blindly add reach to unrelated distances. Preserve exact-source
timed semantics, read-only views, and conditional ownership compensation. Treat
the documented power failures and deferred inventory/PDA features as separate work.
