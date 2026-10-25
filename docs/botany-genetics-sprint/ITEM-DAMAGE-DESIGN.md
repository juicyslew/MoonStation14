# Botany item damage: implementation contract

Status: architecture approved for implementation; not implemented or validated by this document. Source audit and unresolved details: [MUTATION-TARGET-COMPOSITION.md](MUTATION-TARGET-COMPOSITION.md). Pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Supported composition and reuse

Extend resolved `LooseObjectProfile` with optional typed item-damage composition, consumed by the existing authoritative `LooseItemEntity` owner. Tomato gets a real inherited profile through its item registration; generic produce and the accepted jug retain their current behavior. Do not add species checks, a second catalog, or a generic combat framework.

Relevant source components are Biological Damageable (Brute/Burn/Toxin/Airloss/Genetic; no modifier set), high-speed self-impact (Blunt 1, minimum .1 source units/sec, factor .5, cooldown two seconds), thrown landing self-damage (Blunt 1), and cumulative destruction at total damage 1. Destruction order is sound → current reagent spill → discard. Use the existing canonical `DamageKeys` and pure `DamageReducer` arithmetic, not the LivingEntity-only `DamageSystem` or character attachment. Store immutable accumulated damage and the impact deadline on the stack so pickup, transfer, save/load and later throws do not reset them. No numeric HUD is introduced.

## Authority and dimensional adaptation

SS14's body velocity is a Vector2 in its horizontal physics plane. Map that plane to Minecraft XZ, converting blocks/tick to blocks/second with 20 ticks/second. Qualifying block self-impact uses the item's own pre-contact horizontal speed and actual horizontal hard contact. Do not use relative velocity, contact-normal speed, jug's gravity-inclusive threshold, vertical floor contact, or ordinary creation as a throw signal.

Only `BodyLooseThrow` marks an explicit throw after successful world publication. Persist an airborne entity's pending landing state; its first actual downward floor contact consumes that state once. Catching/removing the entity ends that flight. Ordinary drops/harvest spawns have no throw landing state. This first-floor-contact projection is a deliberate Minecraft adaptation of SS14's timed LandEvent, not literal timed-flight parity. Independent high-speed self-impact and thrown landing damage may each feed the same item ledger; destruction occurs only once.

## Spill and feedback

Extract shared loaded-surface/spill behavior from the current jug path only when both jug and item destruction consume it. Reuse `ReagentSystem.handleSpillSolution`; do not copy its puddle/chemistry transaction. Preserve jug's existing rejected-remainder policy. Tomato follows the source destruction sequence after a loaded-only spill attempt; do not create recovery storage or retain a supposedly destroyed item to guarantee conservation. Explicitly document unsuccessful/no-surface spill behavior and test the normal supported surface's exact transfer. Never force-load chunks.

Use the actual three-clip source `desecration` destruction collection and existing sound delivery conventions; record unclear attribution. Do not invent impact SoundHit: source tomato omits that required field, and its effective source value remains unresolved.

## Explicit boundaries

The current mover has block contacts, not SS14 hard-fixture entity contacts. Thrown-target DamageOtherOnHit and entity-to-entity self-impact remain unsupported; do not add inert fields claiming them. Item stun eligibility is unverified; no new stun framework is justified. Red-flash feedback requires its own supported rendering route before claiming it. Existing character damage behavior, jug break thresholds, throw speed, pulling and unloaded-motion rules must not change.

## Acceptance

Require resolved composition/default/inheritance tests; canonical damage filtering, scaling/cooldown and cumulative threshold tests; stack codec/stream/copy/pickup continuity; explicit-throw marker save/load and once-only landing; ordinary drops/harvest staying intact; horizontal qualifying contact versus gravity-only contact; current/depleted chemistry spill and no refill; exactly one destruction; unchanged jug partial/rejected-spill behavior; and authored real authenticated throw scenarios. Cheap compile/focused JVM evidence is separate from lead-owned native/readiness, visual/audio review and owner acceptance. No intermediate integration gate is authorized.
