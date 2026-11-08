# Genetics architecture and implementation sequence

## Foundation decision

Reuse the resolved immutable `SeedProfile` as the genetic value rather than introduce a parallel map of biological/lifecycle/chemical fields. A carried genetic value pairs species prototype identity with the full effective profile. Species identity and genetic parameter values are distinct: species changes replace selected identity/output/visual fields while retaining other inherited values.

`PlantInstance` remains the server plant/lifecycle owner. Once planted, its effective profile—not a fresh catalog lookup—drives growth, visual identity, harvest and generated chemistry. Plain seed packets use resolved catalog defaults; packets carrying instance genetics take precedence. Harvest and extraction preserve that value through existing world publication and ownership pipelines.

Use existing persistent/network item-component codec patterns for immutable carried genetics. Metadata changes must replace component values through existing hand/slot/source owners; never mutate component contents behind stack equality, revisions or network synchronization. Keep capacity/provenance metadata separate from genetic truth. `ProduceFactory` retains one generation implementation; baseline and genetics-based entry points delegate to it. Remove baseline-potency rejection as a mutation policy, not depletion/source guards.

## First coherent implementation unit

The five already-supported plant/product compositions form the initial live integration slice:

1. Typed carried genetic value and codec; initialization from resolved inherited defaults.
2. Packet planting and tray persistence/evaluation consuming the effective instance profile.
3. Genetics-based produce generation and exact harvested-instance metadata.
4. Extracted packets and replanting preserving changed stats/chemistry through normal ownership/publication.
5. Instance isolation, codec, formula/default regressions and a tagged authenticated transmission fixture.

This is a foundation, **not** a claim that mutation gameplay or the 71-plant catalog is finished. M1/M2 now extend these owners with typed grouped weighted tables, inherited effective chemistry, and live chemical mutation via tray polling. M2 candidate r3 reached `ready_for_owner`; this is bounded historical evidence, not whole-sprint readiness after M3 changes. Do not add species-specific branches to make the five cases pass. Catalog and further mutation work must extend the same owners.

## Copy-on-write and pollen requirement

Upstream shared defaults and explicit `Unique` transitions are documented in [PARITY.md](PARITY.md). Harvested snapshots are suitable for this initial repeated/multi-yield produce path: upstream marks that seed shared and subsequent tray changes clone away from existing fruit.

Pollen is a later live-reference consumer, not a second genotype catalog. A swab captured from an upstream `Unique=true` seed can observe later parent changes; a `Unique=false` seed clones on change, leaving pollen attached to the prior instance. The full sprint must preserve those observable semantics with a narrowly scoped server seed/reference owner. Snapshotting all pollen or declaring all transfers shared would be a departure. Resolve that live owner and its save/load boundary before implementing swabs; do not presume a world-wide genome registry, migration or crash-recovery framework is needed.

## Next units and boundaries

- M3A underway: extend typed seed/profile fields and inheritance with generic numeric/viability/seedless genes, mutation-level/modifier and reagent triggers, and mutation-cycle application to the effective profile. Preserve verified M1/M2 table grouping, chemical inherent/acquired distinction, and zero-divisor semantics. Import explicit source IDs/aliases, not species-name behavior branches.
- Extend botany reagent effect consumers within the existing reagent metadata owner; map catalog/mutation reagents before adding definitions. No parallel botany chemistry engine.
- Add mutation cycle ordering, species/crossing inheritance and actual supported effect consumers, then catalog expansion and special behaviors. Keep boundaries concrete: upstream sentience affects the EXISTING holder and adds ghost-role availability (no mob spawn or movement/inventory); local active-character ownership requires `LivingEntity`, making nonliving takeover an external boundary (`MakeSentientEntityEffectSystem.cs`). Ligneous requires `SharpComponent`, not a `Sharp` tag or arbitrary `CUTTING` quality; local tool profiles need supported sharp semantics (`PlantHolderSystem.cs`). Slippery is persistent produce `Slippery` state plus a real step/contact trigger, not a puddle substitute (`SlipperyComponent/System`). Kudzu spawning retains the tray at the SAME cell, kills the plant, and needs a coexisting anchored vine/growth/spread representation; `PlantRemoveKudzu` clears the seed flag, not existing vines (`SeedPrototype.cs`, `PlantHolderSystem.cs`, `BotanySystem.Seed.cs`, `Resources/Prototypes/Entities/Objects/Misc/kudzu.yml`, `KudzuSystem/SpreaderSystem`). The missing botany vine consumer alone is not grounds for kudzu deferral. These are boundaries, not final support/deferral decisions for whole systems.
- Reuse atmosphere, damage, tools, sound, slip, timed interaction, source ownership and fixture services. Runtime capability—not model existence—determines supported behavior.
- Mutation showcase cases must state their trigger, observable result and transmission path. The catalogue fixture must distinguish supported gameplay, delivered visuals and deferred mechanics instead of representing every entry as working by default.

## Validation and workflow

Authors run focused JVM/compile checks only. Every technically ready coding slice is submitted with a structured gate plan and automatically dispatched to central agent-05; expensive validation is serialized there. Foundation acceptance requires changed live consumer coverage, not just codec/helper passes. Broad final integration needs full suites and owner review. Native registered fixtures use fresh isolated worlds, retain useful failures, and clean passed disposable worlds after evidence capture.
