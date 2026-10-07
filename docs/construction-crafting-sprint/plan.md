# Shared crafting and construction sprint

## Status and sequence

**Planned; not authorized for implementation yet.** Finish and commit the
physical disposal-pipe sprint first. The owner subsequently authorized a narrow
interim disposal-family construction mode: the eventual browser key acts as if
Disposal Pipes had been selected. It supplies a non-hand plan, HUD, quick shape
palette and rotation preview, but no full browser. This sprint follows that
commit and replaces the direct family entry with the shared catalogue. Existing
unit and pipe-configuration interfaces are not the shared construction browser.

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
- Search, optional **All / Items / Structures** filters, and a compact entry
  showing output, starting requirements, and whether it is carried or placed.
- Item selection exposes **Craft** and quantity. Structure selection exposes
  **Select plan**, closes the browser and enables world placement previews.
- Selecting another structure replaces the active plan directly. Browsing or
  crafting an item does not silently clear a construction plan.
- A small favourites/recent-plans palette is the proposed fast track for common
  choices. It selects the same plan state, not another mode. A separate shortcut
  key is optional; do not require another bind for ordinary plan switching.
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
- Keep the plan selected for repeated placement until explicitly cleared or
  replaced. Players can otherwise play normally with a plan selected.
- Families may offer a compact palette of related starting objects (e.g.
  disposal straight/bend/junction/trunk). This changes object type without
  reopening the full browser; selection is still explicit, not inferred from
  merely holding steel.
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
- Once a frame exists, explicitly apply materials, parts and tools to advance
  its actual graph stage. Do not auto-assemble a whole machine from a backpack.
- Prefer shared starting forms. A machine frame's board determines its machine
  and later requirements; girders branch by supplied materials and tools.
  A chosen final goal may guide work, but must not lock out valid graph branches.
- Disambiguate initial forms through plan selection when material/context/action
  alone is ambiguous. Change recipes only for meaningful component/material/tool
  distinctions, not arbitrary uniqueness requirements.
- No erase mode or persistent free ghost blueprints. A preview vanishes when
  inapplicable or the plan is cleared; real frames use normal dismantling steps
  and material returns. Players can lay out actual scaffolding before finishing.

### HUD and preview readiness

Keep a compact status visible while a plan is selected, even with the wrong
item in hand or no preview: **Plan: Girder · Hold 2 Steel · [key] Change ·
[key] Exit**. Starting requirements come from resolved recipe data.

| Situation | HUD | Preview |
| --- | --- | --- |
| Empty hand/wrong material | Red: Hold 2 Steel | Hidden |
| Correct material, too little | Yellow: Steel 1/2 | Dim/unavailable at plausible target |
| Enough material, valid target | Green: Steel 2/2 — Ready | Bright usable colour |
| Enough material, blocked target | Material readiness stays green; Placement blocked | Red |

Use text and brightness as well as colour. Material readiness and spatial
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
   rules and graph branches; map existing local services; choose a small recipe
   set including item crafting, a girder/wall branch, machine-frame/board-driven
   work and disposal initial shapes. Document unsupported outputs explicitly.
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
  ambiguity is resolved by plan; actual frames branch through explicit supplied
  materials/boards/tools without reopening the browser or final-goal lock-in.
- Repeated exposed/vertical disposal placement and eligible sealed crossings use
  existing physical block authority/geometry. Floor finishes have no interaction.
- Exact body/mind-epoch and inventory authority, recipe inheritance/defaults,
  live consumers, timed interruption, capacity and placement rollback are tested.
- No erase mode, workbench, crafting grid, inventory blueprint, finished-frame
  item, or arbitrary recipe-pattern differentiation introduced.

## Open details, not settled requirements

- Choose default browser/exit keys after auditing existing binds; no default
  key has been agreed. Decide quick-palette activation without multiplying binds.
- Establish exact automatic ingredient-source locations and output-capacity
  policy. Do not claim backpack/nearby sourcing is verified SS14 behaviour.
- Specify queue cancellation/interruption, switching selected bodies, depletion
  messaging and what happens across disconnect/reload. Prefer simple current
  requirements over persistent cross-session scheduling.
- Decide search/category layout and initial favourites/recent limit during the
  UI slice. Do not turn this into a large catalogue-management system.

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
