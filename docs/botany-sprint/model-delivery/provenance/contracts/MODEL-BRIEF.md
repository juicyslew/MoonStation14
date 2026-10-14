# Basic botany model brief

**Status: Modeler work underway; awaiting delivery.** Coordinate with the sprint owner and existing renderer. Asset delivery and acceptance have not occurred. This brief names required logical states, not a final geometry, scale, origin, attachment, or rendering contract; coordinate and confirm those with the actual render owner before production. The technical integration contract remains unresolved; do not treat assets as accepted or infer that a new commission is needed.

## Required visual coverage

- One hydroponics tray/base suitable for an empty cell and a plant render attachment.
- Five ordinary plants keyed by stable prototype visual identifiers: wheat (6 declared stages), tomato (6), potato (4), poppy (3), aloe (5), plus shared empty, harvestable, and dead logical states.
- Provide identifiable empty, dead, and ripe/harvestable examples for integration review. Stage counts come from upstream seed declarations; they do **not** promise a unique mesh for every stage. The sprint owner must confirm authoritative appearance-stage mapping from upstream before stage assets are finalized.
- Required tools/machine visuals may reuse existing attributed assets where suitable. Grinder and seed-extractor asset ownership is unassigned; do not assume the five-plant modeler must build new machines. Confirm reuse or commissioning with the owner.

Keep stable state keys in a manifest mapping prototype visual key → plant/state asset. Avoid per-item-ID switches or duplicated mappings. Empty/dead/harvestable presentation must follow authoritative simulation status, never drive growth or harvest eligibility. Models must not supply collision/hitboxes. Preserve existing SS14 asset attribution/provenance priority; retain suitable sprite/item icons where needed, especially packet/produce/tool UI icons.

Before delivery, agree tray/base origin and orientation, cell scale, plant attachment offset, draw ordering, and shared-render-owner integration with the code owner; do not invent numeric geometry in this brief. Final acceptance requires assets integrated in the production renderer and independent visual review, not merely passing prototype/runtime tests. Placeholder assets can support development but do not close the visual gate.
