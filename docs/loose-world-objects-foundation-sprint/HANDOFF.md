# Loose World Objects — Foundation + Jug

Follow-up: [ventilation control and fixture-tag integration](control-tag-alignment.md)
records the merged control routes and tagged validation after this sprint's closure.

## STATUS

Owner-closed October 3, 2026, after four connected-feedback rounds. Persistent loose objects, jug interactions/appearance, exact pickup, fixed-speed throwing, and bounded pulling are delivered. Hands/storage ownership and window continuity remain authoritative. Final validation: **1,910 unit tests and 440 required GameTests pass**.

Read this handoff first. `connected-feedback-04.md` records the latest corrections; earlier feedback documents preserve crash, coordinate-conversion, and measurement evidence. `CONTINUATION.md` is historical, not current acceptance status. Closing the sprint does not establish player capacity or additional paired-player/viewport evidence. Changes were not committed or staged by the assistant.

## ARCHITECTURE

- `LooseItemEntity extends ItemEntity` retains existing stack/storage transfer seams. Its inherited stack is the sole owner; vanilla pickup, merging, age, motion, and rendering are replaced. Persistence preserves whole-stack components. Native pickup animation requires the covariant `copy()` override: a plain `ItemEntity` carrying the loose entity type crashes the typed renderer. Copies are isolated, unattached, fresh-identity loose instances.
- Inherited `LooseObjectProfile` prototypes govern geometry, presentation, motion, and specialized effects. Unknown explicit profile IDs fail closed; unprofiled sprites use generic presentation, while BlockItems have a separate 3D fallback. Motion uses loaded-only swept bounds without force-loading chunks. `LooseChunkBounds` uses floor-and-shift and half-open maxima—not packed `ChunkPos` accessors.
- Exact nominated pile pickup is separate from legacy nearest-hint pickup. Visible selection is bounded to 32 candidates with a separate scan ceiling. The muted blue-gray outline follows physical bounds and native `xOld/yOld/zOld` interpolation; no pile text or theme framework exists.
- Liquid transactions use copied reagent components, integer-cent plans, source-first publication, exact-state checks, and compensation. Puddle cleanup occurs only after confirmed stomach admission. Jug breakage uses total pre-contact travel speed, requires actual contact, and seeks the first loaded floor/puddle below the near-side impact column. Rejected/partial spills retain authoritative remainder.
- Pulling retains world identity and reserves a hand through transient `LiveHands` metadata. The server applies a bounded horizontal feet-plane constraint; movement snapshots carry the shared **0.95** movement factor into client prediction before replay. Loose target motion is not predicted.
- `ItemFootprintProfile` prototypes supply server/client storage geometry through existing prototype synchronization. Grid decoding checks structure without side-specific catalogs; live ownership validation checks server geometry. Storage mutations wait for the relevant catalog. Outer item footprints do not change container capacity or loose dimensions.
- Immutable `ItemPresentation` snapshots retain appearance components while stripping ownership/container payloads. Hand, equipment, body-grid, world-grid, and legacy-child presentation feed one `ItemUiRenderer`, including drag previews. Native model fallback receives the full display stack; jug layering is resolved centrally. Common `JugItemAppearance` supplies fill/tint/capacity to world and UI rendering. Display stacks cannot become another inventory owner.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java`
- `ms14/loose_objects/`: profiles, chunk bounds, diagnostics, `interaction/`, `appearance/JugItemAppearance.java`, and `client/LooseItemRenderer.java`.
- `ms14/hands/live/`: `BodyHandItemTransfer`, `BodyItemTargetProbe`, `BodyLooseThrow`, `BodyLiquidInteraction`, `BodyPuddleDrinkService`, `PuddleTargetProbe`, and `BodyPullService`.
- `ms14/hands/network/`: presentation snapshots/builders/codecs and authoritative request services.
- `ms14/hands/client/`: `BodyHandClient`, `BodyInputGesturePolicy`, `AltTapGesture`, `ItemUiRenderer`, `ItemUiAppearance`, and storage render/preview adapters.
- `ms14/storage/grid/`: `ItemFootprints`, `ItemFootprintProfile`, `ModItemFootprints`, `GridContents`.
- Prototype JSON: `src/main/resources/data/moonstation14/moonstation14/{loose_object_profile,item_footprint}/`.
- `mixin/GiveCommandCreativeFakeDropMixin.java`, `mixin/ItemInputReagentValidationMixin.java`: scoped give-dummy suppression and command reagent validation.
- `docs/hands-inventory-program/HANDOFF.md`: prerequisite ownership/window invariants. `future-extensions.md`: deferred work. `docs/audio-unknown-provenance.md`: unresolved individual audio provenance; preserve redistributed asset licenses.

Java paths abbreviated above are relative to `src/main/java/com/juicyslew/moonstation14/`.

## SS14 CORRESPONDENCE

Research pins: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; Robust `3136118b5338ef2d9580178caf5c723e65eb76e7`.

Controls separate **Shift+E belt smart-equip/quickstore**, **Shift+B backpack**, **V/Shift+V storage windows**, **E normal world use**, **Alt+E/Alt+left-click alternate world use**, and **Z normal held use**. Standard storage bags/belts do not quick-equip on Z. Alt+Z's separate in-hand capability is not implemented. Alternate bindings are generic interaction actions, not universal liquid buttons. Mapped keyboard actions are tick-owned; mouse alternate use calls the same helper. An unused Left Alt tap toggles the pointer on release; modifier gestures do not. Native inventory screens are suppressed only while a committed body is controlled.

Resolved SS14 size defaults/explicit shapes cover all **22 portable core items**: materials/jug/floor tiles 2×2; paper/30-unit chemistry bottle/waffle 1×1; tools/grenade/cable coils 1×2; backpack 4×4; leather waistbag 6×6; pickaxe five-cell T. The floor-pry tool uses a local crowbar-equivalent mapping. Source references are in `connected-feedback-03.md`.

## DECISIONS / WHY

- Jug is item-only, 150-unit capacity, glass-like locally, with 0.5×0.75 world bounds and six fill tiers. Obsolete jug block/block-entity registrations/classes are removed; model assets remain.
- Fixed throw speed is **0.75 blocks/tick**, with eye aiming and a clear hand-height launch origin. Contact break threshold remains **0.5 blocks/tick**; floor impacts include horizontal speed, and wall spills descend via a bounded 512-block surface scan rather than a fluid simulation.
- Midair catching is deliberately retained by owner direction.
- Puddle sipping is **immediate, one unit per explicit gesture**, temporarily overriding SS14's researched three-second delay until visible generic timed interactions exist. No continuous drinking or automatic repeat was added. Shared tolerant targeting retains five-block reach, loaded checks, and real-shape visibility; it does not enlarge the physical puddle.
- Unknown positive command reagent IDs are rejected at `ItemInput.createItemStack`, not through global component-codec catalog checks. Creative mod-item drops become loose objects; uncontrolled Creative pickup remains possible, without controlled carriers bypassing body hands. Give-dummy suppression does not suppress real overflow.

## KNOWN GAPS

Heavy active-object work exceeds the provisional direct-work guardrail: 256 continuously jostled objects measured p95 approximately **7.299 ms** versus 5 ms; 1,024 settled objects approximately **0.771 ms**. These are not whole-server/client/network or player-capacity guarantees.

Armed bag-child grenades lack independent contained/offline ticking; held/loose-root continuity is covered. Full atmospheric wind/new explosion impulse adapters, charged throwing, render-distance changes, construction, clothing, machinery, and rigid-body simulation remain deferred. Unprofiled installed BlockItems retain generic storage fallback, not audited portable-item parity. Existing storage placements are not migrated/repacked. Generic denial feedback remains diagnostic. Final connected pixel output and paired-player behavior were not separately captured.

## VALIDATION

Final logs: `build/loose-feedback-04-unit-final.log` and `build/loose-feedback-04-gametest-first.log`; `git diff --check` passed. Tests cover real loaded/unloaded motion, subtype-safe named/damaged pickaxe copies, exact/airborne pickup, jug floor/wall contact and conservation, command ingress, immediate puddle admission/cleanup, prototype footprints, generic presentation wire state/isolation, and mapped input policy. Successful disposable fixtures were removed.

Retained measurement artifacts: `build/reports/loose-feedback-02-measurements/` (150 hash-verified samples). Earlier unrelated intermittent power/status/animation failures retain useful logs/artifacts; no unrelated subsystem fixes were included to obtain final green validation.

## NEXT WORK

Start follow-on scope from `future-extensions.md`; continuous ingestion and visible timed interactions need their own design/parity investigation. Investigate active-object performance before making capacity promises. Restart server/client for the changed snapshot wire layout; reset stale alternate-use bindings if needed. Do not revive superseded contextual Shift+E or invisible puddle timers.
