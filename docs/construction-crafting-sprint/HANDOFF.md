# Construction and crafting

## STATUS

**Sprint closed at the owner's direction.** Delivered the shared construction/
crafting browser, nine disposal shapes, Girder → steel/plastic wall branches and
Metal Rod crafting. Final polish centers sprites/meshes without moving text;
the configured G binding now closes an open browser. Owner previously reported
construction/search working and accepted the other UI/family changes.
Construction is committed as `5e366dc`; subsequent main integration is indexed in
`MERGE-MAIN.md`. No assistant merge commit or push made. Aggregate native
certification remains non-green; closure does not certify unobserved connected-client
or authenticated paths.

## ARCHITECTURE

- `ConstructionCatalog`, graph/recipe/entity prototypes and `PrototypeRuntime`
  own resolved composition. `initialStage` derives the first physical entity,
  material costs and work duration; gameplay consumes these resolved values.
- `ConstructionPlanMode` owns ephemeral body/epoch-qualified intent;
  `ConstructionWorldData` persists host identity, actual graph progress,
  anchoring and remembered goal. Goals guide but never restrict alternate branches.
  `CraftQueue` independently owns sequential portable work.
- `ConstructionWorldService.beginReservedPlacement` shares initial material
  reservation, timed work, completion and cancellation/denial refund. Disposal
  geometry/install remains in `DisposalBuildMenuService`. Later material work
  requires its surviving source token, but tolerates that stack's count changing.
- `BodyCraftInventoryService` consumes body hands, worn storage and qualifying
  nearby materials, with output-capacity preflight/publication compensation. No
  carrier inventory, silent output spill or automatic wall completion is permitted.
- Browser joins the existing storage pointer workspace. One registry dispatcher
  paints all visible windows in depth/input order: gameplay HUD-post, or approved
  current-screen-post after screen contents, never both. Storage focus raises a
  window rather than hiding others. The storage canvas is the sole input router;
  direct overlay EditBox clicks explicitly establish focus.
- Family selection/highlighting derives from pending-or-confirmed recipe identity.
  `legacyCursor` is exclusively for legacy `SHAPES`; its different order must not
  index shared recipe options. Shared and disposal ACK sequence domains remain
  independent; stale replies cannot rewind newer pending intent.
- Open-browser toggle presses route through the existing configured-key matcher
  and `ConstructionKeyGesture` close owner. It consumes that physical press until
  matching release from screen-pre or raw input, including when closing an empty
  storage canvas relocks the cursor. Focused text retains typing; a held close
  cannot reopen the browser or activate the family palette. Closed-browser
  tap/hold behavior is unchanged. Screen-release-pre must include storage hosts:
  handled screen releases never reach the raw-input fallback.

## IMPORTANT FILES

- `architecture.md`, `parity-audit.md`, `validation.md`, `client-checks.md` here;
  `MERGE-MAIN.md` records integration with medical/posture/pulling and airlocks.
- `ms14/construction/{prototype,domain,world,inventory,plan,network}/` and
  `data/moonstation14/moonstation14/construction_{graph,recipe,entity}/`.
- `construction/client/ConstructionBrowserWindow`, `ConstructionBrowserLayout`,
  `ConstructionRecipeGuide`, `ConstructionRecipeIconResolver`, guidance/HUD classes.
- `ui/client/navigation/MachineSearchList`, `ui/model/FilteredListModel`,
  `ui/client/window/WindowChromeButtons`, `ui/client/pointer/PointerOverlayRegistry`.
- `hands/client/{BodyHandClient,StoragePopupScreen,DeferredConstructionUse}`;
  `disposal/ui/client/DisposalConstructionClient`; `MoonStation14Client` event hooks.
- `timed_interaction/client/` presentation/refresh policies; native
  `ConstructionGraphWorldGameTests`, `TimedConstructionDisposalLifecycleGameTests`.

Paths above are under `src/main/java/com/juicyslew/moonstation14/ms14/` unless noted.
Tests live in matching `src/test/java/` and `src/gametest/java/` packages.

## SS14 CORRESPONDENCE

Reference verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91` in
`C:\Users\William\Documents\SS14-dev\space-station-14`. Full supported parent
chains/defaults and system consumers are indexed in `parity-audit.md`.
Girders pass gas; finished walls seal it. Wrench changes actual anchoring;
screwdriver dismantles only unanchored girders. Initial Girder/pipe placement
uses 2 Steel over 1 second; walls require another 2 Steel or Plastic. Metal Rod
uses 1 Steel → 2 rods with zero duration. Basic material stacks cap at 30.
Tool quality/speed/fuel/sounds and returns come from resolved data.

Minecraft adaptations include G browser/family controls, block collision/preview
mapping, half-second aim guidance, compact material-only browser instructions,
and matching active-plan initial material taking priority over hit-host graph
progression. SS14 separates ghost placement from existing-entity interaction;
the blanket priority is the owner's explicit input choice, not upstream parity.
Existing attributed sprites/items and disposal meshes are reused for icons.
Six tool clips retain unresolved rights notices in `docs/audio-unknown-provenance.md`.

## DECISIONS / WHY

- Tap G opens a closed browser; pressing G again closes it unless text is focused.
  With the browser closed, hold G + wheel cycles the selected family. R rotates,
  H exits, all configurable. Select plan closes/locks immediately; outside-window
  clicks lock without a world action. Pause/unsupported modal screens hide overlays.
- Matching initial material while a plan is active attempts new placement before
  host progression, including above built walls. Denial never falls through into
  finishing that host. H restores normal progression; other materials/tools retain
  ordinary interactions.
- Browser targets roughly one-eighth viewport, with full-height scrolling left
  list, right-side compact actions/status, toolkit chrome, global family-crossing
  search and no bottom banner. Structure guides include complete foundation→goal
  requirements, numbered only for multiple steps; portable guides retain input→output.
- Guidance shows remembered goal plus next actionable requirement, anchoring first
  when needed; wrapped content expands within bounded viewport/chat space.
- Timed snapshots publish immediately. World targets provide ephemeral workspot
  markers; own first-person jobs also retain camera-anchored bars. No second timer
  or persistent ghost plans. `WORK_STARTED`, one deferred-use owner and terminal
  refresh deduplication reuse existing hand-proof/query services.
- Machine frames, strip/equipment UI, crit/death/prone/pulling and new fonts remain
  outside this slice. No bespoke models or authority bypasses were added.

## KNOWN GAPS

Combined main integration completed 666/666 native tests: 652 passed, 14 failed;
causality remains unresolved. See `MERGE-MAIN.md` for failures and retained artifacts.
Sprint-closure full native run: 586/595 passes, nine unresolved power/ventilation/despawn
failures. Focused intake also fails “sealed finite source claimed”; parent
reproduction has not established inherited status. Trusted-body/domain fixtures
do not exercise authenticated successful construction/crafting admission; a
registered CHARACTER fixture through normal control/handshake remains absent.
Owner construction/search/UI reports and these fixture limitations are distinct.
Final icon nudge and G-toggle lack separate connected-client observations.

## VALIDATION

Before final icon-only polish, full unit/build/GameTest compilation passed
2,590/2,590, zero failures/errors/skips. Focused construction/disposal selected
49/595, zero untagged: 48 passes, one unresolved intake failure, no construction
failures. Full native was not rerun for that narrow client-only follow-up.
Icon polish passed 17/17 filtered checks. Final G-toggle repair passed independent
source review and 56/56 filtered checks, build and GameTest compilation, all with
zero failures/errors/skips. Latest jar: `build/libs/moonstation14-0.0.1.jar`,
6,018,033 bytes, UTC `2026-10-08T05:15:46.4474640Z`.
These are sprint-closure results/artifacts. Logs/failure worlds are indexed in
`validation.md`; subsequent combined-build evidence is in `MERGE-MAIN.md`.
Owner configuration was not overwritten. No final client pixel/input observation
is claimed.

## NEXT WORK

For new construction content, audit pinned prototype ancestry, extend resolved
graphs/components and reuse the existing transaction/preview/inventory paths.
For UI/input bugs, trace actual host event/focus/paint consumers—not just layout
helpers. For family issues, keep recipe identity canonical. Investigate remaining
native failures in their own scope; do not weaken authority to fill fixture gaps.
