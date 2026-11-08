# M3A output eligibility and source yield

Reference checkout `C:/Users/William/Documents/SS14-dev/space-station-14` was verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Extractor eligibility

`SeedExtractorHandUseService` resolves its extraction profile from carried `GENETIC_VALUE` when present, before falling back to catalog identity. It denies a carried effective profile with `lifecycle.seedless()` before packet preparation/publication or source consumption. Thus a mutable instance can become seedless without changing baseline species eligibility; no species-specific or reagent checks are added. Ordinary baseline-eligible extraction continues through the existing resolved extractor profile, admission, batch publication, power, revision and authority guards.

## Zero-yield harvest source audit

At the pinned revision, `BotanySystem.Seed.cs:150-176` computes total yield as `max(1, Yield * yieldMod)` only when `Yield > -1`; `Yield == -1` produces zero. `PlantHolderSystem.cs:706-740` calls harvest and then `AfterHarvest` for a ready living plant, so zero product generation still permits source readiness/repeat state transitions. Local Minecraft-only mutation values constrain yield to nonnegative values, so the zero case is relevant to local mutation/lifecycle output semantics and is not asserted as identical to every source representation.

Local harvest must not stage or publish fabricated items for a zero preview. Preserve the exact source plant transition when zero-output semantics are supported, using existing domain commits without invoking an empty output batch (whose guard remains intact). If source readiness/harvest state cannot be committed safely, deny before staging. Positive yields continue through `FreshWorldOutputBatch` and its collision fallback/compensation. This behavior depends on the concurrent evaluator/plant-state implementation and must be validated alongside it; no zero-yield live-service integration result is claimed here.
