# Shared construction architecture

Implementation is awaiting aggregate validation and connected-client acceptance. See `validation.md`; this is not a completion handoff.

## Composition

`ModConstruction`, `ConstructionCatalog` and `PrototypeRuntime` compose resolved construction graph, recipe and entity-policy prototypes. Definitions live under `data/moonstation14/moonstation14/construction_{graph,recipe,entity}/`. Browser metadata references graph start/initial/target nodes; starting costs are derived from graph steps rather than duplicated in recipes. Node output counts drive portable results. Tool quality, speed, fuel and optional use sounds come from resolved construction-tool prototypes.

The slice includes existing supported disposal shapes, a shared girder starting form with steel/plastic wall goals, and MetalRod (one Steel, two rods, zero work duration). Machine frames, equipment UI and general inspection are outside scope.

## Separate state owners

- `ConstructionPlanMode`: ephemeral authenticated actor/body/epoch intent. It consumes no hand slot. Its disposal adapter delegates physical placement/orientation to the existing disposal implementation.
- `ConstructionWorldData`: dimension-persistent foundation identity, recipe, remembered goal, actual graph node/step and anchored state. Later plan changes do not rewrite placed goals. External host writes invalidate records; internal transitions use a scoped mutation path.
- `ConstructionCraftNetworking` / `CraftQueue`: ephemeral sequential portable work. Item browsing, Craft and Cancel do not clear the structure plan. Only the active craft consumes inputs; missing materials/capacity or invalid authority stops the queue.

Shared-plan and legacy-disposal snapshot sequence domains are independent. Adapter-generated disposal sequences must not reject otherwise-current shared snapshots.

## Runtime invariants

`ConstructionWorldService` advances actual graph edges from explicitly supplied materials/tools. Remembered goals guide, never restrict branches. Wrench anchoring is real state; a held screwdriver cannot imply unanchoring. Welder continuity permits passive fuel evolution without permitting source/hand/authority replacement; action fuel is charged from current fuel at completion.

Active structure plans now take priority over hit-host progression when the held
item matches the resolved initial material, even if its quantity is insufficient.
Placement failure returns denial rather than advancing the hit girder/wall.
Exiting the plan restores normal steel progression; unrelated materials/tools
retain ordinary interactions. This is an explicit owner input adaptation, not
an upstream blanket-priority claim.

`ConstructionCatalog.initialStage` resolves the first physical graph node/entity,
material steps and duration. Initial block and disposal placement share
`ConstructionWorldService.beginReservedPlacement`: body-owned materials are
deducted into a per-job reservation before work, success commits it and
cancellation/denial refunds it once. Unique reservation source keys avoid initial
duplicate cancellation. Reserved initial work does not require the source hand
to remain unchanged. Disposal candidate preparation and installation remain in
`DisposalBuildMenuService`, shared with its existing physical transaction; the
shared browser route bypasses the legacy per-body timer. Later material work
remains hand-dependent but tolerates count changes in the same surviving token.

`WORK_STARTED` distinguishes accepted reservation from completed placement and
forces a fresh hand query. The single `DeferredConstructionUse` owner retains
one pending structure click for fresh-proof replay, qualified by confirmed plan,
body/epoch, source identity and captured target. Timed terminal world-operation
transitions reuse the existing external-completion hand-refresh path.

Resolved entity policy supplies block mapping, anchoring and airtight behavior. Girders pass gas despite the Minecraft collision adaptation; completed walls seal it. Successful block changes explicitly notify atmosphere topology and retain sealed-wall/disposal host invalidation semantics.

`BodyCraftInventoryService` stages size-aware body output capacity before consumption. Source order is adequate hands, worn storage, then qualifying nearby loose material. Nearby lookup is bounded/loaded/unobstructed, not crosshair nomination; Minecraft pickup delay is not a crafting requirement. Exact-state publication compensation must not overwrite divergent owners. No carrier inventory or silent output spill is permitted.

## Presentation

`ConstructionBrowserWindow` is a client-owned overlay, not an exclusive Minecraft screen. It reuses `MachineWindowRenderer`, `MachineSearchList` / `FilteredListModel` and `WindowGeometryController`. The small `PointerOverlayRegistry` joins it to the existing storage pointer workspace: locked-mode rendering is a GUI overlay, unlocked rendering/input uses the transparent pointer layer without replacing storage windows. Search focus, window hit bounds and pointer capture govern input admission; ordinary controlled-body actions reuse `BodyHandClient` routing. Connected-client acceptance of this integration remains required.

The browser uses family navigation, global recipe-name search and clipped independent scrolling; category tabs are absent. Structure instructions list the complete graph-start→goal requirements, including foundation materials: one concise resource/tool line per step, numbered `1)`, `2)` only when multiple steps exist. No node headings, repeated title/output, “Add”/“Step” labels or durations are shown. Portable recipes retain their unnumbered input→output summary. Only portable item recipes have quantity/Craft controls, and an active queue retains progress/cancellation independently of the selected structure plan. Tap G toggles the browser; hold G/scroll changes current-family variants. R/H retain rotate/exit semantics, with configured binding labels. Selecting a structure closes the browser and locks the cursor; an empty-space pointer click locks without triggering a world action. The browser is hidden on unsupported modal/pause screens. `PointerWorkspaceLayers` owns flushed, depth-ranked window passes with matching input order. Widget clips explicitly use screen-space coordinates because Minecraft 1.21.1 does not pose-transform scissor arguments.

Latest browser styling targets approximately one eighth of the viewport, with
smaller/thinner list rows, compact header and toolkit-painted shared close
chrome. Instructions have top margin; content-sized action controls and compact
status above Craft belong to the right column. No bottom banner reduces the list. The list's
model page size follows its actual viewport. Nonempty search crosses families,
including when reopened into a remembered family; clearing restores browsing.
Direct overlay search clicks explicitly focus the actual EditBox: vanilla widget
click handling alone does not establish focus outside a Screen container.
The current font remains in use; choosing/importing a different font is deferred.
`PointerOverlayRegistry.renderWorkspace` paints all visible windows once in both
front orders. Storage brings itself forward without hiding the browser. On the
storage canvas, screen-pre browser hooks defer to the canvas's sole input router.
Gameplay uses HUD-post painting; approved screen hosts use current-screen-post
painting through the same dispatcher. HUD-post is before screen rendering in
NeoForge, so it must not also paint screen-hosted windows. Both paths preserve
actual frame interpolation and GUI viewport dimensions.

Preview providers resolve the initial entity's supported block/disposal mapping,
not recipe IDs or family names. Block bulk vertices use the shared typed alpha
wrapper; disposal geometry/orientation stays in its existing mesh path.
`ConstructionHudRenderer` centers a content-bounded plan title, avoids published
chat bounds and colors material counts without readiness words. Pending family
intent is displayed coherently until a shared acknowledgement settles it.
Shared family scroll and palette highlighting derive their index from that
displayed recipe identity. `legacyCursor` belongs only to legacy `SHAPES`; its
snapshot updates must never supply a shared recipe-option index. These orders
are different, and treating one cursor as both caused variant bouncing after ACKs.
Optional world targets in existing timed snapshots drive ephemeral cell/progress
markers. Owned first-person world-target jobs also retain the existing camera-
anchored bars so workspot visibility does not determine whether progress is
visible; unanchored work retains actor bars. These are not persisted plans or
separate timers. Snapshot start publication remains immediate and authoritative.

Guidance is nonmodal HUD presentation after 0.5 seconds of continuous controlled-body aim, below the actual plan bounds. Unfocused storage overlays permit dwell; modal screens/search focus suppress it. One catalogue-backed producer sends structured goal and next actionable requirement: goal-name header, one material/tool requirement, anchoring first when needed. Long requirements wrap into additional rows; physical-pixel metrics size the panel and protect glyph/shadow margins from its bottom border. There is no client prose parsing, stage/next label or full-path list. Resolved first-stage roles distinguish unfinished girders from completed menu goals; a Girder recipe is not evidence that all girders are finished. Looking away resets/dismisses; stale target/body/epoch/incarnation replies are rejected. Completed alternate walls are not mislabeled unfinished because their original goal differed.

Tool sounds broadcast after successful work, not canceled/denied work. Canonical imported clips have unresolved file-specific rights recorded in `docs/audio-unknown-provenance.md` and adjacent notices. No client-local audio prediction parity is claimed.
