# Mouse renderer notes

The mouse now uses the accepted R2 authored model from the sibling `Asset Building Workspace`, replacing the interim cuboid. See `docs/mouse-model/RENDERING.md` and that workspace's `MODEL_HANDOFF.md` for model/material bindings. Native model and animation classes live under `ms14/mob/mouse/client`; `MouseMobRenderer` registers and consumes their layer. Render bounds include ears/tail (approximately 0.475 × 0.379 × 0.537 blocks); the existing 0.4 × 0.3 collider and eye height remain unchanged. Default white is selected; brown/gray assets are retained without inventing a color-variant component.

Imported RSI sheets remain intact and attributed but are unused by world rendering. R2 texture/code provenance is retained in the imported `ATTRIBUTION.md`. Manual visual check: spawn the mouse and check the white authored model, grounded placement, movement, and critical/dead poses/materials.

The authored pose/animation API replaces directional world sprites. Synced MobState drives critical/dead presentation; optional advanced animation bindings remain deferred. No first-person viewmodel was introduced.
