# Tray composition unit

Research pin: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, see `CORE-COMPOSITION.md` for the audited ancestry and upstream source index. `TrayProfile` models only holder polling/cycle timing, initial water/nutrition, warning presence and soil capacity/refill. It deliberately excludes rendering, machines, power and fixtures.

`hydroponicsTray` resolves through `hydroponicsSoil` -> `SmallConstructibleMachine` -> `ConstructibleMachine`; the empty leaf overrides initial water/nutrition to zero. The local normal/empty prototype definitions are composed profile data; the Minecraft adaptation does not pretend to implement unsupported SS14 components.

`PlantMetabolismEvaluator` consumes resolved tray and seed profiles rather than seed-ID special cases. It processes poll callbacks before checking the resolved cycle interval, does not catch up missed cycles, and pauses a planted cycle without advancing `LastCycle` when the environment adapter is unknown. Empty holders still run the weed chance; dead holders run that chance before returning. `PlantInstance.CODEC` is a future persistence value codec, not a chunk/world persistence integration.

The bounded ordinary-cycle behavior follows `PlantHolderSystem.Update` / `AffectGrowth` ordering in the pinned checkout. Deliberate omissions: mutation, pests, kudzu, gas consumption/excretion, and same-seed RNG call-sequence parity. Soil reagent effect dispatch is deliberately supplied by the callback; hardcoded reagent-ID branches are excluded and resolved `PlantMetabolismData` integration is the next unit. Planned harvest yield is a domain result, not item creation. No live world consumer is implemented here.
