# Butchery composition prerequisite

Source audit: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (verified). This is a data prerequisite only: no production bindings, UI, timed runtime, or outputs/assets are introduced.

## Source contract

`ButcherableComponent` (`Content.Shared/Nutrition/Components/ButcherableComponent.cs:11–33`) requires ordered `spawned` entries and defaults `butcherDelay` to 8 seconds and `Type` to `Knife`. Each `EntitySpawnEntry` defaults `Amount=1`, `MaxAmount=1`, and `SpawnProbability=1` (`Content.Shared/Storage/EntitySpawnEntry.cs:26–68`); `SharpSystem` passes those entries to `EntitySpawnCollection.GetSpawns` when a timed butcher operation completes. The local supported entry is deliberately deterministic: registered item output plus count (one entity per count), preserving entry order. Probability, groups, ranges, entity prototypes, and stack quantities are not modeled. Local butchering type is explicitly `KNIFE`, the only supported source type; no implicit Sharp/tool-type conflation is made.

`SharpComponent` has `ButcherDelayModifier=1` (`Content.Shared/Kitchen/Components/SharpComponent.cs:12–28`). `SharpSystem` uses the product of component delay and the Sharp modifier for a break-on-damage/move do-after, with hand requirements when appropriate (`Content.Server/Kitchen/EntitySystems/SharpSystem.cs:35–82`). The local data schema preserves that modifier on the existing optional `ToolProfile.sharp` marker; it does not implement the do-after. Sharp admission is separate from `SliceableFoodSystem`'s Knife utensil admission. SharpSystem also exposes an interaction verb, which is distinct from the after-interact path; this prerequisite does not implement either entry path.

## Local data boundary

`ItemProcessingProfile.butcherable` is optional/inheritable. It contains ordered `outputs` (`output`, `count`, count default 1), `delay_seconds` (default 8), and `type` (default `knife`). Output IDs must resolve to registered non-air items; counts are bounded positive separate entity counts, and delays/modifiers must be finite and positive. `ToolProfile.sharp` retains its marker semantics and defaults `butcherDelayModifier` to 1. No butcher output inherits source chemistry or appends ingestion trash: those are distinct consumers. No production item profiles are installed by this prerequisite.

The next server timed consumer can use the resolved APIs `ModItemProcessingProfiles.serverLookup(sourceItem).butcherable()` and `ModToolProfiles.serverCatalog().get(toolItem).sharp().butcherDelayModifier()`. The resolved butcher record exposes `outputs(): List<ButcherOutput>`, each with `output(): ResourceLocation` and `count(): int`, plus `delaySeconds()` and `type()`. It must spawn each count as independent items and preserve the configured order. Upstream revalidation/admission and timed session policy remain to be designed in that consumer.

Existing `Log` and `SliceableFood` composition stay in the same item-processing profile; their independent output validation/runtime paths are unchanged. No output is inferred from the FRUIT/VEGETABLE/MATERIAL audits or slicing composition.

## References

- `Content.Shared/Nutrition/Components/ButcherableComponent.cs:11–33`
- `Content.Shared/Kitchen/Components/SharpComponent.cs:12–28`
- `Content.Shared/Storage/EntitySpawnEntry.cs:26–68,101–158`
- `Content.Server/Kitchen/EntitySystems/SharpSystem.cs:35–82,106–177`
- Related local boundaries: `FRUIT-COMPOSITION.md`, `VEGETABLE-COMPOSITION.md`, `MATERIAL-OUTPUT-COMPOSITION.md`, `SLICING-COMPOSITION.md`.
