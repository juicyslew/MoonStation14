# Disposal unit access

For current testing, obtain **Disposal Unit** from MoonStation **Blocks**.
Put it in the controlled body's active hand, aim at a block face with an empty
loaded cell beside it, and Use. Placement consumes one item and creates the real
unit; body/entity collision and placement permissions still apply.

The entry trunk must occupy the cell **directly below** the unit, routing
sideways or DOWN, not UP into it. Placement does not create a trunk or hidden
floor layer. Use the [powered fixture](fixture.md) to avoid assembling this
manually.

The catalog BlockItem is Minecraft test access, not an upstream SS14 portable crafted item. SS14's disposal unit is built through staged construction (steel, cable, plastic, tools); this implementation does **not** add an instant crafting recipe or shortcut whole-unit steel recipe. A player-facing staged construction/crafting flow remains deferred to the shared construction-browser/queue work.

Direct self-entry is bound to **K** (0.5 s, power-independent); this is a local input adaptation of SS14's contextual climb-inside verb. Its authoritative do-after clock advances once per server post-tick, not per-player physical tick, using the captured body's level game time. Online actors' menus refresh on this same server clock. See [connected checks](interaction-checks.md) for its outstanding authenticated-client roof/camera verification; unit tests do not prove connected-client K input.
