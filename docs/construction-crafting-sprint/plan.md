# Shared crafting and construction sprint

## Status and sequence

**Complete; closed at the owner's direction.** Final icon alignment and open-browser
G-toggle fixes are built and tested. Known native failures and unobserved client/
authenticated paths remain documented in `HANDOFF.md` and `validation.md`.
Scope recorded 2026-10-07, after the
physical disposal-pipe work was merged. This sprint builds on that implementation
and replaces direct family entry with the shared catalogue. Existing unit and
pipe-configuration interfaces are not the shared construction browser.

## Goal

One understandable browser for making portable items and starting world
structures. Preserve SS14 recipes, timed work, material/tool requirements,
physical construction stages and branching where practical. Avoid crafting
grids, workbenches, ethereal inventory blueprints and separate erase mode.
Steel is a material, not a command to construct disposal pipes.

## Agreed player interaction

### Shared browser

- One crafting/construction menu and one configurable keybind to open it.
  Opening it during construction retains the selected plan and highlights it.
  The same key is the HUD's **Change** action; no separate change-plan key.
  Pressing it again closes an open browser unless a text field has focus.
- A compact two-pane catalogue using the machine UI toolkit, with search and
  family navigation; classifications are removed and favourites are deferred.
  Structure steps remain graph-derived; portable instructions are one unnumbered
  input→output line without timing or redundant headings. Target roughly one
  eighth of the screen, with smaller icon-bearing list rows and toolkit-painted
  close chrome. The list keeps its full height, synchronizes model page size to
  displayed rows and leaves less than one row of unused space. Search crosses
  remembered families and explicitly establishes text-field focus on clicking.
  Instructions have top margin; content-sized thin actions sit below them on the
  right, with compact material warnings above Craft and no bottom banner.
- Item selection exposes **Craft** and quantity. Structure selection exposes
   **Select plan**, without quantity controls, and enables world placement previews.
   Selecting a plan closes the browser and locks the cursor immediately. Otherwise
   the browser may remain visible during cursor-locked gameplay.
- Selecting another structure replaces the active plan directly. Browsing or
  crafting an item does not silently clear a construction plan.
- Opening the browser preserves other windows and does not blur the world.
   Only pointer hits inside its window and focused search typing are captured;
   ordinary controls and the existing cursor-lock binding remain available.
   Clicking outside all windows locks the cursor without performing a world
   action. The browser is hidden over vanilla pause/modal menus; window render
   depth and input order must agree, including storage sprites and hand HUD.
   Storage focus changes ordering, not browser visibility. Screen-hosted windows
   paint after their screen; gameplay windows paint after the hand HUD.
- Instructions beyond starting requirements are available on demand, rather
  than always displaying the entire construction graph.

### Portable item crafting

- Select the output explicitly; identical ingredient lists are allowed.
  No arbitrary patterns to disambiguate recipes, crafting grid or workbench.
- Quantity schedules sequential work. Only **one item is crafted at a time**;
  each completes its own required work/time before the next begins. Batch
  quantity must not collapse do-afters or multiply simultaneous production.
- Automatically consume valid ingredients for the current craft without
  repeated dragging, including nonstackable inputs. Do not consume or reserve
  all future ingredients upfront. Unavailable requirements stop the queue.
- Use actual size-aware body inventory ownership and output capacity. Do not
  represent ingredients as equal-sized grid squares or silently scatter excess
  output. Establish permitted source locations and result placement from SS14
  and existing body inventory rules before implementing them.
- Quantity/batch controls are a deliberate addition: pinned SS14's menu sends
  one item recipe request per Craft press, not a built-in Craft Maximum queue.

### World construction

- The plan is persistent intent stored **outside hand slots**, not a physical
  or ethereal inventory item. A configurable exit-construction key clears the
  active plan and preview. The HUD shows both Change and Exit bindings.
- Construction changes only use of the selected recipe's starting material
  on eligible placement space. Retain movement, hands, storage, doors, ordinary
  tool use and interactions with existing structures. No broad input capture.
- Hold the required starting material and aim to preview the intended block;
  confirm with ordinary Use. Derive orientation from the targeted face and
  full body look, not a Facing selector. Placement consumes the recipe's
   starting material/time, not every component of the finished structure.
   Initial work reserves materials before its timer, refunds cancellation and
   permits concurrent jobs. Distinct existing targets can also progress together
   while the same surviving held source remains valid. Show ephemeral progress
   evidence at each work spot, not persistent ghost plans. All nine supported
   disposal foundations require 2 Steel over 1 second from the resolved graph.
- Keep the plan selected for repeated placement until explicitly cleared or
  replaced. Players can otherwise play normally with a plan selected.
- Families may offer a compact palette of related starting objects (e.g.
  disposal straight/bend/junction/trunk). This changes object type without
  reopening the full browser; selection is still explicit, not inferred from
  merely holding steel.
- Tap G opens a closed browser; pressing G again closes it when not typing.
  With the browser closed, hold G opens a compact palette for the current family,
  with scroll selecting variants. Rotation and exit use
  configurable binds (initial suggestions R and H); tap/hold and bind conflicts
  must be disambiguated in settings.
- Infer orientation from compatible neighbouring ports first, then targeted
  face/hit and body look. A rotate input cycles meaningful alternatives without
  requiring the player to reposition in confined space. Prefer connected
  alternatives, but keep disconnected orientations accessible for new runs.
  Deduplicate equivalent port arrangements while retaining directional routing
  distinctions. Do not automatically reshape installed objects.
- Screwdriver adjustment changes placed-object orientation, not its object
  type. Show the proposed orientation and require confirmation/tool work;
  warn about disconnection rather than forbidding deliberate rerouting.
  This replaces SS14's screwdriver junction-type cycle by owner choice.
- For disposal sealed-wall crossings, preserve the existing eligible-host
  exception to empty-space placement: the wall is retained, not replaced.
- Once a construction object exists, explicitly apply materials, parts and
  tools to advance its actual graph stage. Do not auto-assemble from a backpack.
  A matching active plan's initial material instead attempts new adjacent
  placement before hit-host progression, including player-built walls. Placement
  failure must not fall through into finishing a girder. Exit the plan to resume
  that material's ordinary progression; unrelated materials/tools remain usable.
- Sprint scope: disposal pipe family; girder/wall branching; and one simple
  portable item to prove sequential crafting. Machine frames and boards are
  deferred, not acceptance criteria. A chosen final goal may guide work, but
  must not lock out valid graph branches.
- Persist the intended goal on the placed foundation. Aiming at an incomplete
  construction object continuously for 0.5 seconds shows a guidance popup based
  on its current graph stage and remembered goal; looking away dismisses/resets
  the timer. The popup is not an inspect-key or general inspection system.
  Changing the active plan later does not rewrite the foundation's remembered
   goal. Guidance must never block actual valid graph branches.
   Presentation is only the goal name as header and one next actionable
   material/tool requirement, with anchoring first when necessary. No Stage/Next
   prose or complete future material list; family palettes have no misleading
   Select/Close button footer.
- Disambiguate initial forms through plan selection when material/context/action
  alone is ambiguous. Change recipes only for meaningful component/material/tool
  distinctions, not arbitrary uniqueness requirements.
- No erase mode or persistent free ghost blueprints. A preview vanishes when
  inapplicable or the plan is cleared; real frames use normal dismantling steps
  and material returns. Players can lay out actual scaffolding before finishing.

### HUD and preview readiness

Keep a content-bounded top banner visible while a plan is selected, even with
the wrong item in hand or no preview. Center the object title, avoid chat, and
show material counts plus configured controls. Starting requirements come from
the resolved initial physical stage. Do not show Ready/Hold/Insufficient words.

| Situation | HUD | Preview |
| --- | --- | --- |
| Empty hand/wrong material | Red: Steel 0/2 | Hidden |
| Correct material, too little | Yellow: Steel 1/2 | Dim/unavailable at plausible target |
| Enough material, valid target | Green: Steel 2/2 | Green translucent ghost |
| Enough material, blocked target | Material readiness stays green; Placement blocked | Red |

Colorblind-friendly symbols are deferred by owner choice. Material readiness and spatial
validity are independent. Suppress irrelevant ghosts while holding tools or
unrelated items; selection remains visible in the HUD. Additional starting
requirements/tools must be shown when the resolved recipe actually needs them.

## Architecture and SS14 audit requirements

1. Reverify upstream checkout revision
   `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Resolve the full parent chains,
   inherited components/defaults and runtime consumers for every included frame,
   tool, output and construction recipe before designing local feature fields.
2. Use resolved typed recipe/graph composition. Share browser metadata without
   conflating item crafting with physical graph progression. Implement only
   components the runtime consumes; JSON definitions alone are insufficient.
3. Keep selected plan, crafting queue and frame graph state distinct. Reuse
   exact controlled-body/mind-epoch authority, body inventory ledger, timed-work,
   placement/collision and prototype services. Never use carrier inventory.
4. Server owns materials, timings, graph edges, queue execution and placement.
   Client selection/HUD/ghosts are presentation. Validate loaded spatial targets
   and live host identity, with bounded rollback and no forced chunk loading.
5. Reuse disposal block geometry, six-axis orientation, host admission and
   transport. Remove disposal-specific ownership of steel interaction rather
   than cloning that shortcut into a supposedly generic browser.
6. No unrelated mod compatibility, old-save migrations, extensive anti-cheat,
   or generic crash-recovery framework. Preserve attributed SS14 sounds/sprites;
   record uncertain provenance in `docs/audio-unknown-provenance.md`.

## Ordered work units

  1. **Audit and vertical-slice contract:** inspect upstream crafting source/result
    rules and graph branches; map existing local services; choose disposal
    initial shapes, a girder/wall branch and one simple portable crafting recipe.
    Machine frames/boards are deferred. Document unsupported outputs explicitly.
2. **Resolved recipes and graph runtime:** data/default resolution, shared
   starting forms, explicit material/tool/board progression and dismantling.
3. **Sequential item crafting:** single active craft, queued quantity, exact
   ingredient/output ownership, interruption and capacity handling.
4. **Browser and plan state:** searchable unified catalogue, current-plan
   highlighting, selection replacement, optional favourites/recent quick palette,
   real keybind display and dedicated exit.
5. **World interaction and HUD:** material-specific placement override,
   face-derived previews, independent readiness states and disposal integration.
6. **Acceptance:** focused prototype/runtime and live tests, independent review,
   then concise connected-client checks. Update the sprint handoff only when the
   owner declares completion.

## Acceptance evidence

- Browser handles item and structure recipes without two competing menus;
  changing plans never requires exit/re-entry; crafting preserves an active plan.
- Identical item ingredient lists produce the selected output. Nonstackable,
  size-aware inputs work without dragging. Queued crafts are sequential, honor
  each do-after, stop when requirements are unavailable and never duplicate or
  silently spill outputs.
- Plan selection consumes no hand slot. Wrong/insufficient/enough material and
  blocked space produce the specified HUD/preview states, including no ghost
  with unrelated held equipment. Labels use actual configured keys/recipe data.
- Ordinary controls remain functional with construction selected. Initial
  ambiguity is resolved by plan; actual construction branches through explicit
  supplied materials/tools without reopening the browser or final-goal lock-in.
- Aimed-at incomplete construction guidance appears after 0.5 seconds, reflects
  current graph stage and persisted foundation goal, dismisses/reset on looking
  away, and never prevents valid alternate branches.
- Repeated exposed/vertical disposal placement and eligible sealed crossings use
  existing physical block authority/geometry. Floor finishes have no interaction.
- Exact body/mind-epoch and inventory authority, recipe inheritance/defaults,
  live consumers, timed interruption, capacity and placement rollback are tested.
- No erase mode, workbench, crafting grid, inventory blueprint, finished-frame
  item, or arbitrary recipe-pattern differentiation introduced.

## Open details, not settled requirements

- Audit configurable tap/hold G, R rotation and H exit binds and conflicts.
- Establish exact automatic ingredient-source locations and output-capacity
  policy. Do not claim backpack/nearby sourcing is verified SS14 behaviour.
- Specify queue cancellation/interruption, switching selected bodies, depletion
  messaging and what happens across disconnect/reload. Prefer simple current
  requirements over persistent cross-session scheduling.
- Choose the initial favourites set during the UI slice; do not turn this into a
  large catalogue-management system.

## Starting points

- `docs/disposals-pneumatic-sprint/architecture.md`, `parity-audit.md`,
  `validation.md`: physical pipes and temporary construction integration.
- `docs/hands-inventory-program/HANDOFF.md`: exact body ownership and inventory.
- `docs/construction-tools-parity.md`: supported tool composition.
- Upstream: `Content.Client/Construction/UI/ConstructionMenuPresenter.cs`,
  `Content.Server/Construction/ConstructionSystem.Initial.cs` (legacy item path),
  `Resources/Prototypes/Recipes/Construction/Graphs/structures/girder.yml`,
  `Resources/Prototypes/Recipes/Construction/machines.yml`, machine-frame
  prototype and board-consuming systems. Reverify these findings in implementation.
