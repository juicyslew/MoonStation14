# Beakers, machine-owned item slots, and ChemMaster 4000

## Goal and scope

**Closed by the owner on 2026-10-10.** Implementation was authorized on 2026-10-08.
See `HANDOFF.md` for the final state and documented remaining issues. Deliver a usable powered
ChemMaster with the complete **Input/buffer workflow** shown in the owner's image,
and prove a reusable machine-owned removable-item-slot capability. The Output tab
exists, but output UI and pill/bottle production are deferred by the owner.

Playable loop: fill a beaker using existing liquid interactions, insert it, open
the powered machine, extract selected reagents into its buffer, swap beakers,
return selected reagents to the new beaker or discard them, then eject it.
The buffer must genuinely manipulate the inserted item's solution components.

**Owner-approved scope addition: aim-and-release world dumping.** Capture a loose
jug/container on button press, aim at the ChemMaster, and release to dump its
whole solution into the persistent buffer. This replaces SS14 world drag/drop;
it does not add a destination-first bulk mode or a three-click “Use with” menu.
The container remains in the world and does not enter the input slot. Preserve
normal use/alternate-use bindings outside this gesture, camera movement and
optional mouse lock, and all existing nonmodal window lifetime rules. Invalid
release or cancellation must not change chemistry. Final binding/integration is
subject to the existing input audit, without introducing a separate modal screen.

Include standard Beaker (50u) and Large Beaker (100u) as inherited variants.
Use SS14 beaker sprites and attributable machine audio. Use a clearly identifiable
placeholder ChemMaster block model; the owner's modeler supplies its final model.
No custom model production is part of this sprint.

### Owner client feedback: presentation correction

Client testing found an oversized, low-contrast window, unclear controls, literal
reagent localization keys, and position-dependent clipping that hid reagent rows
and dose buttons. Acceptance requires actual machine visual-kit panels/buttons,
distinct sections, and corrected rendering/hit-testing at moved window origins.
Target roughly one-sixth screen area with five visible input reagent rows and
eight visible buffer rows, independently scrollable; dose buttons belong beside
the reagent label, not below it. Preserve all existing dose presets and nonmodal
lifetime rules. The Output production UI remains deferred.

Expand fixture stock for more than eight distinct buffer reagents, more than five
input reagents, and supported reaction demonstrations. Add resolved beaker fill
levels and mixture tint using upstream sprites and existing appearance services.
Native validation alone does not establish satisfactory client presentation.

**Deferred, do not implement:** examination can prefer the ground over aimed
loose items/mobs. Desired future behavior prioritizes those entities while
preserving glass pass-through and manual cycling to underlying blocks. Starting
points: `ExamineWorldSelector`, `ExamineCandidateCycle`, and their existing tests.
Preliminary estimate: **3–6 active engineering hours** including focused tests
and connected-client verification, with moderate integration uncertainty.

### Second connected-client correction pass

- Fix both lists' actual absolute GUI scissor calls. The pinned Minecraft 1.21.1
  `GuiGraphics.enableScissor` ignores pose transforms; moving the window must not
  change which content is visible. Keep partially visible rows inside the panel.
- Use small left-aligned text-sized tabs, compact Transfer/Discard controls,
  tighter container-info spacing, the SS14 label **Container**, and overflow-only
  scrollbars for both lists.
- Empty beaker models must not include a liquid layer.
- Default dumping to **Shift + left drag**. Defer examine during capture; a short
  click examines the captured source, never drinks. Preserve ordinary examine
  outside the gesture and Ctrl pulling. This is separate from deferred examine
  target-priority work.
- Owner-requested distance adaptation: reuse ordinary pickup reach for both dump
  endpoints, rather than SS14's 1.5-unit range; preserve loaded/access/LOS checks.
- Wire resolved Jug/Beaker drain/refill/transfer/drink capabilities into shared
  liquid interaction services. SS14 defaults are 5u transfer and consumption;
  preserve existing ingestion authority/timing, capacity clamps, and reactions.
- Play recipe-specific reaction audio only after accepted commits, with the
  upstream bubbles default and explicit silence/overrides supported. Inert
  buffer dumping must not produce reaction audio.

Reported `UnknownHostException` for `api.minecraftservices.com` and
`sessionserver.mojang.com` is consistent with external authentication/profile
DNS failures. Retained logs do not contain the exception stack, so origin is not
confirmed; do not change authentication/network settings based on this alone.

### Third connected-client correction pass

Reserve the actual title's GUI-space height before laying out logical-space
tabs. Make tabs visibly join their pane; give the container caption, Eject and
right-aligned Transfer/Discard consistent inset margins. Omit volume/capacity
when no container is inserted. Use correctly centered compact labels for dose
and mode buttons, and disable hover/tooltips while the pointer is mouse-locked.
List rows, scissors, scrolling, scrollbars and hit testing must all use the
panel's interior content rectangle, not its border/rim.

All numeric reagent readouts must share exact concise formatting: `200`, `2.5`,
`2.05`, not padded decimals. Preserve cent precision and existing examination
visibility policies.

Fast valid gestures must not require a minimum hold time. Hold an early release
intent until matching server capture acceptance; then dispatch once, unless
cancelled, replaced or invalidated. Show a rejected Begin's actual reason rather
than masking it with a subsequent missing-capture error. Keep server ownership,
epoch, sequence, source snapshot and final interaction checks intact. The exact
reported fast-input rejection cause remains unverified without an incident trace.

### Tab/alignment clarification

Latest owner direction supersedes the earlier mode-control alignment: group
Transfer/Discard on the **right**, with Sort on the **left**, retain their compact labels, and restore the
Sort button's normal larger font independently. Input/Output must use a joined
tab strip and content-pane border: the selected tab shares the pane surface and
opens into it; inactive tabs remain visibly separate. Three outlined button-like
rectangles alone do not satisfy tab presentation.

### Header, row margin and caption correction

Right-align Input/Output tabs alongside the title in a coordinated header row;
remove the redundant divider beneath the title without changing other machine
windows. Preserve the joined selected-tab/pane surface. Bring reagent buttons
and quantity labels closer to the right panel interior, reserving scrollbar space
when needed. Append `u` to reagent quantity labels only, never dose buttons.

Inserted-container name and volume/capacity must remain visible. The confirmed
caption defect was a negative width computed from Eject's left coordinate;
use the actual caption region instead. Empty slots still omit volume/capacity.

Latest Buffer control correction: place Sort after the Buffer heading with a
clear gap; align Discard's right edge with the Buffer panel's right edge. Dose
buttons stretch from the existing first-button left anchor to just before each
row's measured amount label. Do not reserve a worst-case quantity width for all
rows. Rendering and hit-testing must share the same per-row geometry.

### Live pouring and lost-capture follow-up

### Reinsertion pending-state correction

Open-session unsolicited snapshots must preserve that viewer's acknowledged
request watermark. Publishing `-1` after an action was already acknowledged
causes `lastRequest > acknowledgedRequestId` to become true again and freezes
the UI after refresh/reinsertion. Reuse the server's per-session LAST_REQUEST
tracking; do not mix viewer IDs, relax revision checks, add retry timers, or
close the window as a workaround. Test acknowledged action → newer slot refresh
→ next genuine pending action through the production workspace/frame policy.

### Existing interaction follow-up

The primary-click helper already nominates the target for `USE_TARGET`. The
confirmed pour blocker is its server dispatch: a missing medical-catalog entry
throws and returns DENIED before generic liquid transfer. Treat normal catalog
absence as nonmedical and continue shared container use, while preserving
medical/syringe precedence and denial on genuine lookup failure. Test the actual
dispatch decision in addition to the existing transfer-core fixtures.

`no active dump capture` identifies absence, not the reason for removal. Add
short gesture-correlated client send/ACK and server acceptance/removal traces,
including specific tick-cleanup reasons. Do not weaken capture authority or
claim the missing-capture bug fixed without identifying its removal/order cause.

**Mandatory UI contract (owner clarification):** ChemMaster is a nonmodal managed
workspace window. It coexists with other UIs, allows normal movement, and supports
the existing optional mouse-lock/cursor modes. Opening another UI, changing focus,
looking away, mouse-lock changes or idle time must not end its interaction.
Only explicit dismissal or genuine interaction invalidation closes it: power loss,
leaving the allowed range, Critical/death/action incapacity, lost body authority,
machine removal/replacement/unload, disconnect or a changed world/body context.
Closing one window must not close its siblings or steal their pointer ownership.

## Verified SS14 target

Reference checkout revision verified: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Paths below are relative to `C:/Users/William/Documents/SS14-dev/space-station-14`.

- `Resources/Prototypes/Entities/Objects/Specific/Chemistry/chemistry.yml`:
  `Beaker -> BaseBeaker -> BaseItem`; solution `beaker`, 50u, Small item size,
  dispenser compatibility and normal refill/drain/inject/draw solution behavior.
  `LargeBeaker -> BaseBeaker` overrides capacity to 100u and size to Normal.
  Neither is a stackable supply. `Objects/base_item.yml` supplies inherited defaults.
- `Resources/Prototypes/Entities/Structures/Machines/chem_master.yml`:
  `ChemMaster -> [BaseMachinePowered, ConstructibleMachine]`;
  `BaseMachinePowered -> BaseMachine -> BaseStructure` in
  `base_structuremachines.yml` and `Structures/base_structure.yml`.
  Anchored/static machine; inherited powered receiver load 1000 is overridden to
  **250 W**. Separate input/output containers; input accepts `FitsInDispenser`,
  not a hardcoded beaker prototype name. Output composition is deliberately deferred.
- Machine solution `buffer` has no gameplay capacity limit. Ejecting/swapping the
  beaker leaves it intact. Use the existing supported fixed-point numeric limit,
  not an invented 50u/100u buffer capacity.
- `Content.Server/Chemistry/EntitySystems/ChemMasterSystem.cs`:
  individual-reagent transfer and discard, authoritative state publication.
  Buffer changes bypass reaction processing; return to an ordinary beaker uses
  solution-system updates and can trigger normal supported reactions.
- `Content.Shared/Containers/ItemSlot/ItemSlotsSystem.cs` and
  `Content.Server/Power/EntitySystems/ActivatableUIRequiresPowerSystem.cs`:
  UI opening requires power, and power loss closes it. Ordinary slot insertion
  and ejection are not power-gated for this machine.
- `Content.Shared/Chemistry/SharedChemMaster.cs` and
  `Content.Client/Chemistry/UI/ChemMasterWindow.xaml(.cs)`:
  quantities **1, 5, 10, 15, 20, 25, 30, 50, 100, All**; Transfer/Discard modes
  apply to both lists. Input transfer goes to buffer; buffer transfer returns to
  input, limited by remaining beaker capacity. Discard deletes the selected amount.
- Buffer sort cycle: **Oldest First -> Alphabetical -> Quantity -> Recent First**.
  Sorting changes presentation, not the stored solution. Oldest/recent refer to
  reagent insertion order, not alphabetical IDs or individual dose timestamps.
  The input container list is not affected: upstream `ChemMasterWindow.xaml.cs`
  lines 246 and 249–325 build the container list separately and sort only BufferInfo.

Map these resolved compositions to typed local prototypes and only locally
supported components. Record adaptations explicitly rather than copying unused
upstream component names. Upstream machine-frame destruction and complete beaker
glass-damage behavior are not established as equivalent to Minecraft block/item
removal; resolve their relevant lifecycle mapping before implementing removal.

## Architecture and mandatory reuse

### Reusable item slots, not another inventory

Machine owns a stable identified slot containing one whole item/token and its
actual components. Slot policy describes supported compatibility and insertion/
ejection rules; ChemMaster's processing service is a separate consumer.

- Extend `ms14/hands/transfer/ItemLocation` with machine-slot identity and reuse
  `ItemLocationLedger` compare-and-move rules. The ledger is currently live in
  disposal ownership, not a universal ledger of every hand/grid item.
- Reuse/generalize the relevant level-ledger lifetime from
  `ms14/disposal/unit/DisposalUnitLedger`, without creating competing ledgers for
  overlapping machine/disposal owners or retrofitting all inventories.
- Reuse token/payload/codec and staged release behavior from
  `ms14/disposal/item/DisposalContents`. Extract only genuinely shared small parts;
  do not inherit disposal routing, timed payload ticks, or body containment.
- All hand changes go through `LiveHandsCommitGateway`. Reuse existing whole-item
  take/insert validation and canonical drop creation/placement from
  `BodyHandItemTransfer`; `DisposalUnitHandAdapter` is an insertion precedent,
  not a complete generic transaction or permission grant.
- Provide one server-thread transfer boundary for hand-to-slot and slot-to-hand/
  world ejection. Preflight exact actor, item, owner, machine and revisions; publish
  only committed state and compensate ordinary failed admission/spawn. Gateway
  batching alone is not atomic across independent stores.

### Chemistry and presentation

- Beaker uses `IReagentTrait`, `ReagentComponent`, and the existing liquid planner
  used by `BodyLiquidInteraction` and `SyringeContainerService`. Capacity and
  compatibility come from resolved item policies; do not subclass Bottle's
  hardcoded 30u/drinking behavior merely to obtain reagent storage.
- Reuse exact hundredths through `ReagentUnits` and `ReagentAttachment`. Existing
  `transferUnits` is proportional; extend the same chemistry responsibility with
  a selected-reagent transaction using existing admit/remove/validation rules,
  rather than implementing another float store or calling proportional transfer.
- Keep buffer and staged beaker changes paired. Buffer never reacts; returning
  reagents invokes existing supported beaker reaction behavior. Discard is the
  explicit intentional sink, not an accidental capacity-overflow loss.
- Reagent maps do not preserve insertion order. Persist a small buffer ordering
  list alongside existing quantities, not a second quantity store; maintain it
  when a reagent first appears, is exhausted, or is reintroduced.
- Extend existing `PowerRuntime` registration/load paths and real receiver meters;
  do not introduce a parallel power loop or a speculative consumer plugin system.
- Reuse the nonmodal analyzer/storage workspace: `HealthAnalyzerWorkspace`,
  `StoragePopupWorkspace`/`StoragePopupScreen`, `StoragePopupLifecycle`,
  `PointerOverlayRegistry`, `PointerWorkspaceLayers`, shared sibling ordering,
  and `ms14/ui` window/tabs/buttons. Join an existing workspace rather than
  requiring `mc.screen == null` or replacing other windows. Preserve the existing
  `BodyMovementIntentScreen` movement and mouse-lock routing.
- Use an independent authenticated server interaction/subscription, reusing
  `HealthAnalyzerServer` subscription/explicit-unsubscribe concepts. Do **not**
  open/replace an `AbstractContainerMenu` for the ChemMaster window: air-alarm/APC
  menu transport would introduce exclusive menus and movement-blocking behavior.
  Validate exact actor/body/epoch, machine identity, session/request sequence and
  revisions for actions; reuse current hand admission and commit services for
  item transfer. Mere window visibility is not permission. Separate interaction
  validity from temporary input/menu focus so another UI cannot incidentally
  terminate the subscription. Server-owned contents are never optimistically
  mutated by the client.
- Reuse examination contributors and solution formatting for the beaker and
  machine; opening a vanilla menu alone never grants possessed-body authority.

## Ordered work units

1. **Composition mapping and beakers.** Record supported inherited policies; add
   resolved standard/large definitions, live capacities/storage footprints,
   dispenser eligibility, sprites, creative/give availability and normal liquid,
   syringe and examination integration. No new reaction catalog.
2. **Machine slot foundation.** Implement ownership, policy validation, preserved
   components, conditional item mutation, insertion, manual removal, ejection and
   ordinary save/load. Prove alternate policies with focused fixtures, not another
   production machine. Slot code must not depend on ChemMaster reagent names.
3. **ChemMaster server gameplay.** Placeholder placed machine, facing/identity,
   real 250 W demand, inert persistent buffer, selected transfers/discard, beaker
   swaps, revisioned snapshots and lifecycle invalidation. Machine removal must
   have explicit item/solution outcomes; no accidental duplication or silent
   ownership loss. Finish the upstream teardown audit before choosing those
   Minecraft removal outcomes, and document any departure.
4. **Complete Input UI.** Match the supplied hierarchy: loaded container name,
   volume/capacity and Eject; scrolling reagent rows and all quantity buttons;
   buffer rows, four sort modes, Transfer/Discard controls; clear empty, full and
   disconnected/power-loss states. Output tab displays a brief deferred placeholder.
   Test GUI scaling, small windows, scrolling, pending/rejected requests and normal
   close behavior. Share the nonmodal pointer workspace, focus/z-order and close
   chrome; never add a standalone blocking machine Screen. Movement and optional
   mouse lock continue with the window open, including beside other windows.
5. **Aim-and-release addition.** Resolve upstream Jug/Beaker drainable and
   ChemMaster dumpable composition/defaults; extend the corresponding existing
   typed policies and live consumers. Reuse nominated loose-item targeting,
   controlled-body action authority, exact reagent storage, and the persistent
   buffer. Stage the world source and buffer together at release, validating both
   entities' identity, interaction access, loaded state, and unchanged source.
   Dump the entire mixture without reacting the buffer or requiring powered UI.
   Add press/capture/aim/release/cancel feedback and focused input/transaction
   tests. Extend the spawnable fixture with loose sample jugs for the human check.
6. **Acceptance.** Independent reuse/parity review, focused tests, one full build/
   native regression run, then a concise connected-client gameplay checklist.

Beaker and slot implementation may run independently after their shared policies
are agreed. Machine integration precedes authoritative UI wiring. The owner has
authorized this feature sprint; unrelated stabilization and the explicit non-goals
below remain deferred.

## Acceptance evidence

- Prototype inheritance/default resolution and **live** consumers: 50/100u,
  item size/footprint, slot eligibility, 250 W demand, buffer reaction distinction.
- Generic slots: occupied/incompatible rejection, exact whole-item component/token
  preservation, failed-transfer nonmutation, ejection fallback, repeated actions,
  changed body/machine, save/load and removal/replacement. No duplicate live owner.
- Chemistry: every amount and All, selected species only, partial/full recipient,
  depleted source, exact nonreacting transfer conservation, intentional discard,
  unchanged nonselected reagents, swap-retained buffer, correct supported reactions
  on return, and stable oldest/recent ordering across serialization and sorting.
- Aim-and-release: eligible loose source capture; whole-mixture conservation into
  an inert buffer; empty container retained with unrelated components preserved;
  input slot unaffected; dumping without power; invalid target/source changes,
  blocked access, cancellation, stale/replaced machine, and changed body rejected
  without mutation. No persistent destination or accelerated batch mode. Ordinary
  click/alternate-use, workspace controls, camera movement, and mouse-lock modes
  remain usable; trusted fixtures alone do not prove connected-client gestures.
- Networking: authenticated controlled-body path, current machine/session identity,
  stale revision rejection/resync, two-viewer convergence, power-loss closure,
  range/Critical/death invalidation, disconnect/context cleanup, no forced chunk
  loads or dedicated-server client classloading. Trusted/FakePlayer fixtures alone
  do not prove real transport. No arbitrary popup lifetime or idle expiry.
- Nonmodal lifecycle: coexist with storage/analyzer/other managed windows; opening
  a sibling or changing screen focus does not unsubscribe ChemMaster. Walk with
  the window open, switch cursor/mouse lock in both directions, and look away
  without dismissal. Explicit close affects only this window; invalidation closes
  only the invalid interaction. Pointer recapture follows shared workspace rules.
- Tagged native checks (`machine-slots`, `beaker`, `chemmaster` plus existing domain
  tags) use fixture-owned bounds and shared readiness. Record filtered/full
  selected/total and pass/fail counts; preserve useful failures and clean disposable
  passing worlds. Do not expand this into general fixture-space enforcement.
- Human smoke test: fill -> insert -> extract -> swap -> return -> eject, discard,
  sorting, two viewers and APC power loss; verify UI readability and audible sound.
  Also coexist with other UIs, move and mouse-lock while open, explicitly close
  one sibling, then confirm range/Critical/death closure and valid reopen.

Pre-sprint merge evidence: build and 2,891 JUnit tests passed; unfiltered native
708 completed, 707 passed, one airlock obstruction/safety failure. That failure is
outside this sprint and must not be silently waived or repaired as chemistry work.
The preceding merge was committed as `5f8c7f6`, and the owner reports it integrated
into main. ChemMaster work remains separate and uncommitted; do not finalize,
reset, or otherwise alter owner Git history as part of this sprint.

## Explicit non-goals

Output slot UI, pills, bottles, packaging/labels; full chemistry department;
heaters/dispensers/new recipes; power cells/chargers/electronics implementations;
machine-board construction graphs or a generic inventory/UI framework; backward
compatibility/migrations; extensive anti-cheat/crash recovery; final 3D model;
unrelated suite fixes. Generic slots enable later machines without implementing
them now.

Assets: upstream `Objects/Specific/Chemistry/beaker.rsi`, `beaker_large.rsi`,
`Structures/Machines/mixer.rsi` and `/Audio/Machines/machine_switch.ogg` are starting
points, not substitutes for provenance review. Retain attribution and record any
unclear audio provenance in `docs/audio-unknown-provenance.md`.
