# Component nomenclature — observed bridge and current entity capability

**Observed naming, not a previously established owner rule.** No explicit owner-approved general naming convention was found in the inspected code and sprint docs. This note does not rename anything; the current entity-only complex-interaction implementation follows the distinction below.

## Observed locally

- `IMS14Component` and `IMS14Attachment` convert to each other. `SystemLink` pairs `DataComponentType<C>` with `AttachmentType<A>`; `MS14Provider` reads/writes the component on an `ItemStack`, the attachment on a NeoForge holder, and snapshots mutable attachments through immutable components. `ModDataComponents` and `ModDataAttachments` register the respective halves. Existing `*Component` records (for example `HandComponent`) are immutable Minecraft item-data payloads in this bridge, while `*Attachment` types (for example `HandAttachment`) hold mutable entity runtime data. This describes the legacy pairing, not a requirement to create both halves for every future feature.
- `CharacterData` is an immutable, codec-backed character **prototype declaration/template** (movement, hands, thermal policy, optional `components`, etc.), not a live body's component store. Its `components: ["complex_interaction"]` declaration seeds a runtime default only once on identity enrollment; the human declares it, the pig does not. This is not a per-action permission list or proof of the body's current state.

## Naming guidance for new capabilities

| Term | Meaning |
|---|---|
| `*Attachment` | NeoForge `AttachmentType<T>`-backed per-entity runtime storage: the local equivalent of an SS14-like entity component. Specify whether it persists/syncs; attaching a value does not inherently imply either. |
| `*Component` | Legacy immutable value type for a Minecraft `DataComponentType<T>` on `ItemStack`s; do not read this suffix alone as an SS14 entity component. For **new** item values prefer the explicit `*ItemData` or `*ItemDataComponent`. |
| `*System` | Operations/behavior over state, not the data holder or prototype declaration. |

For an entity-only capability, define its prototype default in `CharacterData` if needed and its runtime state as an attachment; do **not** add an item-side `DataComponentType` merely to match the legacy `SystemLink` pairing. Here `ComplexInteractionAttachment` is an entity-only codec-backed persistent runtime value, and `ComplexInteractionSystem` bootstraps it once after successful identity enrollment, then exposes explicit runtime `setEnabled` and read-only `enabled`. Absent attachment/marker and present persisted disabled state both deny the capability but are distinct states. The independent persistent initialization marker is written before the attachment: if only one half survives, the read fails closed rather than reseeding from the prototype. An explicit runtime toggle need not agree with the prototype default. In-memory save/load GameTests exercise persistence but do **not** establish disk-restart behavior.

`ComplexInteraction` is a coarse prerequisite for complex manipulation, **not** a flat `INTERACT`/`USE`/`ATTACK` permission, not simple contact, and not a grant for any target or item. Status (including stun), hands/item ownership where required, target/reach and protection/cancellation are separate checks. This naming note does not authorize a world-facing action; no action packet or world effect exists yet.
