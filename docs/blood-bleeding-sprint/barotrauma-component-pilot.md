# Barotrauma component pilot

## What is configured

The character prototype, not the entity's saved data, owns pressure-hazard policy. For example, a character JSON prototype may include:

```json
"components": [{"type":"Barotrauma","damage":{"types":{"blunt":0.5,"heat":0.1}},"maxDamage":200}]
```

This is a fragment of the surrounding character JSON object, not a standalone document. Presence of this registered component opts the character into the pressure hazard; absence means no pressure damage. `damage.types` supplies typed base damage and `maxDamage` is a pre-hit ceiling on currently accumulated damage matching those type keys. A permitted full hit may cross the ceiling; subsequent hits stop until damage is healed below it. Human and pig prototypes carry their own damage entries. Shared atmosphere hazard rules belong instead to `BarotraumaAtmospherePolicy`: low pressure at or below 20 kPa multiplies base damage by 4; high pressure starts at 550 kPa, with a pressure-derived scale capped at 4; cadence is 20 ticks. Do not duplicate these shared thresholds in each species prototype.

`CharacterComponentRegistry` dispatches the discriminator to a typed codec (`BarotraumaComponent` and `BloodstreamComponent` are registered), rather than treating arbitrary JSON objects as runtime components. `CharacterSchemaAudit` rejects unknown component types, unrecognized fields or damage keys, malformed/nonfinite values, more than 16 components, and duplicate component types; the `CharacterData` constructor also enforces distinct registered types. The direct `BarotraumaComponent.CODEC` audits both decode and encode, so using that codec outside `CharacterData` does not bypass strict field, damage-key, value, or null checks; invalid in-memory values also fail encoding. Adding a new type requires registering its codec **and** supplying its schema audit. The former top-level `pressure` field is rejected, not retained as a second source of authority. Existing data packs using `pressure` must be migrated to `components` before loading; there is no automatic data-pack conversion or entity-save migration for this policy. Existing saved character identity and mutable damage are separate from the prototype policy.

## Runtime boundary

`TickHooks` visits living server entities. `BarotraumaSystem` requires an alive host and an enabled `AtmosphereService`, then resolves the entity's saved character identity against the current catalog and verifies that the prototype still owns the entity's actual host type. An absent, stale, or wrongly mapped identity fails closed; merely listing a component somewhere in a catalog is not sufficient. On the staggered due tick, `(gameTime + entityId) mod 20 == 0`, it samples atmosphere at eye position. Missing/unknown/disabled samples do nothing; **unknown is not vacuum**. A valid known vacuum can count as low pressure.

`PressureExposure` is a pure typed reducer: it computes pressure-derived damage and checks the pre-hit matching-damage ceiling without creating entity state. Before that check, `DamageSystem.existingOrVanillaBaseline` reads the existing typed ledger or, if none exists, derives a temporary blunt baseline from missing vanilla health (5 typed damage per missing health). This precheck is read-only: an ineligible hit, including a baseline already at/above the ceiling, neither imports that baseline nor creates a damage attachment. For an eligible hit, `DamageSystem.applyHealthChange` is the server-side commit boundary; it imports the missing-health baseline once if needed and applies the full pressure hit. The local bridge converts 5 typed damage to 1 Minecraft health; this is not SS14's entire damage/critical/death model. The pressure policy is **not** a NeoForge per-entity component or attachment: no separate Barotrauma configuration is persisted or network-synced per actor. Mutable damage remains in the existing entity damage store and uses its existing update/synchronization path. Static prototype/catalog configuration must not be confused with synchronized mutable damage; any client-facing display of these rules needs a deliberate catalog-sync contract, not an assumption that server datapack policy is available or current on clients.

## Why this is closer to SS14, and where it is not

SS14's Barotrauma component makes hazard participation explicit and lets different species supply different damage and caps while the atmosphere system owns shared hazard constants. This pilot similarly uses typed component presence as the sole pressure-policy authority instead of a dedicated `pressure` field with parallel defaults. It is **not ECS parity**: `CharacterData` remains a monolithic record for blood, lungs, thermal and other policy blocks; `TickHooks` still scans living entities and asks for a catalog-resolved policy instead of iterating a component-indexed entity set. Equipment `protectionSlots`, protective gear evaluation, and pressure alerts are still missing. Global atmosphere is disabled by default, so this pilot alone does not turn scheduled pressure hazards on in a normal world.

There is no per-entity Barotrauma config object/packet to maintain, trading entity memory and config-sync traffic for shared catalog storage and repeat lookup on due ticks. The living-entity tick hook, identity/host resolution, catalog lookups, atmosphere sampling, temporary maps/sets and damage routing still cost CPU and may allocate. Catalog reloads can change eligibility, invalidate assumptions, and require careful candidate validation and client catalog coordination; no component membership index exists to make scanning free. If profiling shows pressure lookup is significant, benchmark representative actor counts with atmosphere enabled (including allocations, tick time, packets, and reload behavior), then consider a catalog-generation-aware host/component index or cached policy with invalidation and strict host checks. This is a possible optimization, **not** a measured performance improvement; an index adds memory and stale-cache/reload risks. Do not replace host verification with a loose cache key.

## Verification and remaining acceptance

The earlier pilot's isolated GameTest run completed **193 tests with one unrelated failure**, `onlymarkedvillagerlosesvanillatravelownership`; an even earlier run passed 191/191. Historical component inheritance runs are summarized below. Isolated GameTests do not establish globally enabled scheduled atmosphere integration, manual/live gameplay acceptance, loaded-datapack reload behavior, client catalog synchronization, or 20-player CPU/memory/network performance. In particular, watch for duplicate policy authority (`pressure` versus `components`), confusing catalog membership with an entity attachment, wrong host mapping, interpreting unavailable samples as vacuum, confusing static config sync with mutable damage sync, and strict rejection of old data packs.
# Character component inheritance

`ModCharacters.CHARACTER_TYPE` alone installs `CharacterComponentMergeStrategy`.
This is a character-only type-keyed policy, not a change to the generic resolver
or reagent inheritance. In parent declaration order, repeated registered types
compose **top-level fields**: the first parent wins conflicting fields and later
parents supply missing ones. For example, a first parent with only Barotrauma
`damage` and a second with only `maxDamage` yield one complete component. A
child entry overrides conflicting parent fields; child-listed types appear before
inherited-only types, which retain parent order. Absent and empty child arrays
both inherit: a missing child component does not suppress its parent's component.
There is no component removal state, matching upstream RobustToolbox's
`EntityPrototype.cs`. To omit Barotrauma, use an unrelated prototype or
restructure the parent chain into component-bearing and component-free siblings.
`remove` is unsupported and rejected as an unknown component field, even on
abstract parents. Every raw component fragment is audited before composition:
unknown fields/damage keys, malformed values, and explicit nulls fail even if
an override would hide them.
Missing top-level fields remain valid in fragments; the final concrete component
must still contain both `damage` and `maxDamage`.

Nested properties are atomic replacements: specifying `damage` replaces its
whole object, and `damage.types` does not retain omitted old damage keys.
This treats the repository's `DamageSpecifier`-shaped value as one field, not
as a map of inheritable damage keys. Omitting `damage`
altogether may retain the inherited value (e.g. a maxDamage-only patch);
providing malformed or incomplete `damage` fails raw-fragment validation even
on an abstract parent or when a child would override the component.
Raw arrays reject malformed entries and duplicate or unregistered types. Abstract component parents need no
`slip_data`; the resolved concrete character must pass the normal audit.
An already-bound pig GameTest uses an isolated prototype manager to stage and
commit snapshots that gain an inherited Barotrauma component, switch to a
component-free parent, then change its damage value. Staging does not publish a candidate; after each
commit the pig reads the selected snapshot, with old snapshots remaining stable.
A rejected candidate leaves the published snapshot unchanged. This exercises
the snapshot/runtime boundary, **not** acceptance of an actual datapack reload.

Verified upstream reference: RobustToolbox
`ComponentRegistrySerializer.cs` lines 193–241 composes matching registered
types and appends parent-only components; `SerializationManager.Composition.cs`
lines 179–208 fills missing fields and respects explicit child fields; and
`EntityPrototype.cs` lines 151–156 declares components with
`AlwaysPushInheritance`. Nested `damage` and Bloodstream map atomicity reflect
the local component schema rather than a claim of general upstream nested-map
behavior. Mixed Barotrauma/Bloodstream inheritance tests now exercise child-first
ordering, parent order, same-type field filling, and nested-map replacement.

Before tombstone removal and the Bloodstream migration, a corrected isolated
GameTest run passed **209/209**; a later historical run completed **208/209**
with one unrelated failure, `negativestacksdryoneperdueintervalandcleanup`.
Neither run verifies the final migrated state. The final `build --no-daemon`
passed; its XML reports **1,230 unit tests, zero failures**, and
`compileGameTestJava` passed. Per instruction, no GameTestServer run was made
after tombstone removal and Bloodstream migration, so post-migration server
GameTest acceptance remains unverified. Fragment validation and staged-snapshot
tests exercise candidate rejection, not a loaded-datapack reload.
