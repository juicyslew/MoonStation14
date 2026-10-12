# Pumpkin pie slice parity

Verified against SS14 checkout `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`FoodPiePumpkinSlice` derives from `FoodPieSliceBase` → `FoodInjectableBase`
→ `FoodBase` → `EdibleBase` → `BaseItem`. The base edible defaults provide
5 units per 1-second ingestion and destroy-on-empty; injectable food adds
refillable/injectable solution access. FoodBase adds SpaceGarbage, which the
local runtime does not model and is intentionally not represented by a fake
component. The local profile inherits `food_base` for its FOOD kind, default
dose and delay, and explicitly provides the slice's reagent solution.

The upstream pie slice declares Nutriment 7.5u, Vitamin 3.5u and Sugar 1.25u,
but `maxVol: 7` is smaller than the 12.25u initial contents. Local source
profiles enforce capacity, so this mapping uses 12.25u effective capacity to
preserve every upstream initial reagent rather than silently truncating it.
Existing reagent effects and metabolic rates are unchanged. The slice is a
single, nonstackable item; no whole-pie slicing, bowl, or trash behavior was
added. Pumpkin slice texture attribution is adjacent to the imported asset.
