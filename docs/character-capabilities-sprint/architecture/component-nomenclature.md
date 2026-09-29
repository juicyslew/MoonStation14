# Component nomenclature — observed bridge and current entity capability

**Observed naming, not a previously established owner rule.** No explicit owner-approved general naming convention was found in the inspected code and sprint docs. This note does not rename anything; the current entity-only complex-interaction implementation follows the distinction below.

## Observed locally

- `IMS14Component` and `IMS14Attachment` convert to each other. `SystemLink` pairs `DataComponentType<C>` with `AttachmentType<A>`; `MS14Provider` reads/writes the component on an `ItemStack`, the attachment on a NeoForge holder, and snapshots mutable attachments through immutable components. `ModDataComponents` and `ModDataAttachments` register the respective halves. Legacy `HandComponent`, `BloodComponent` and `LungComponent` are immutable Minecraft `ItemStack` DataComponent payloads in this bridge, while their `*Attachment` counterparts hold live entity state. This describes the legacy pairing, not a requirement to create both halves for every future feature.
- `CharacterData` is an immutable, codec-backed character **prototype declaration/template** (movement, hands, thermal policy, optional `components`, etc.), not a live body's component store. The human declares typed descriptors, e.g. `components: [{"type":"Barotrauma","damage":{"types":{"blunt":0.5,"heat":0.1}},"maxDamage":200.0},{"type":"ComplexInteraction"}]`; the pig does not declare `ComplexInteraction`. That descriptor seeds a runtime default only once on identity enrollment. It is not a per-action permission list or proof of the body's current state.
- `CharacterComponent` in `ms14/character/components` denotes a **prototype descriptor** in the registry (including `BarotraumaComponent` and `ComplexInteractionComponent`), despite the `*Component` suffix. It is neither an `ItemStack` DataComponent payload nor the live entity attachment; the registry decodes typed prototype declarations, while the attachment stores runtime capability state.

## Naming guidance for new capabilities

| Term | Meaning |
|---|---|
| `*Attachment` | NeoForge `AttachmentType<T>`-backed per-entity runtime storage: the local equivalent of an SS14-like entity component. Specify whether it persists/syncs; attaching a value does not inherently imply either. |
| `*Component` | Context-dependent suffix: legacy immutable Minecraft `ItemStack` DataComponent payloads, but `ms14/character/components` types are registry prototype descriptors, not live entity state. For **new** item values prefer the explicit `*ItemData` or `*ItemDataComponent`. |
| `*System` | Operations/behavior over state, not the data holder or prototype declaration. |

Proposed owner rule for **new** entity-only capabilities, not a historical owner-approved convention: define a prototype descriptor in `CharacterData` if a default is needed and runtime state as an attachment; do **not** add an item-side `DataComponentType` merely to match the legacy `SystemLink` pairing. Here `ComplexInteractionAttachment` is an entity-only codec-backed persistent runtime value, and `ComplexInteractionSystem` bootstraps it once after successful identity enrollment, then exposes explicit runtime `setEnabled` and read-only `enabled`. Absent attachment/marker and present persisted disabled state both deny the capability but are distinct states. The independent persistent initialization marker is written before the attachment: if only one half survives, the read fails closed rather than reseeding from the prototype. An explicit runtime toggle need not agree with the prototype default. In-memory save/load GameTests exercise persistence but do **not** establish disk-restart behavior.

`ComplexInteraction` is a coarse prerequisite for complex manipulation, **not** a flat `INTERACT`/`USE`/`ATTACK` permission, not simple contact, and not a grant for any target or item. Status (including stun), hands/item ownership where required, target/reach and protection/cancellation are separate checks. This naming note does not authorize a world-facing action; no action packet or world effect exists yet.
