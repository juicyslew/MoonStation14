# Apple, banana and citrus composition

Source-only audit at verified SS14 pin `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; not installed catalog data or asset acceptance. No pear seed/packet/product exists in the audited 71-row catalog; do not invent one.

| Source seed | Packet / product | Ordered mutation targets | Fixed JUICE output |
|---|---|---|---|
| apple | AppleSeeds / FoodApple | goldenApple | 10u JuiceApple |
| banana | BananaSeeds / FoodBanana | mimana | 10u JuiceBanana |
| lemon | LemonSeeds / FoodLemon | lemoon, lime, orange | 10u JuiceLemon |
| lime | LimeSeeds / FoodLime | orange, lemon | 10u JuiceLime |
| orange | OrangeSeeds / FoodOrange | extradimensionalOrange, lemon, lime | 10u JuiceOrange |

All five repeat harvests with six growth stages. Apple: lifespan 55, maturation/production 6, yield 3, potency 10, ideal light 6. Banana: lifespan 50, maturation/production 6, yield 2, default potency 1, light 9, water .60 and heat 298. Citrus: lifespan 55, maturation/production 6, yield 3, potency 10, light 8. Resolve every omitted value from `SeedPrototype` before emitting local profiles; source IDs and Minecraft resource paths are distinct namespaces, not interchangeable strings.

Genetic chemistry (minimum–maximum / potency divisor): apple Nutriment 1–10/10 and Vitamin 1–4/25; banana Vitamin 1–4/25 and Nutriment 1–2/50; citrus Nutriment 1–5/20 and Vitamin 1–4/25. Harvest clears starting product contents and generates chemicals from the inherited seed; static JUICE output is separate and is not potency/depletion-scaled. These leaves can reuse the implemented resolved juice-solution consumer when their authentic products become available.

## Resolved product boundaries

`FoodProduceBase → FoodInjectableBase → FoodBase → EdibleBase → BaseItem` supplies ordinary Edible admission, food solution injection/refill, Produce/potency appearance and named `food` grinding. Products set their own seed identity and visuals. Lime and orange inherit FoodLemon's starting solution. Starting contents: apple Nutriment 10u + Vitamin 4u; banana Nutriment 4u + Vitamin 2u; lemon/lime/orange Nutriment 5u + Vitamin 4u. Do not equate starting content totals with an independently verified MaxVolume field without checking the resolved solution definition.

Packets inherit `SeedBase → [BaseItem, BasePaperLabelable]`, including conditional paper-label visuals; product composition is not packet composition.

**Banana is not just another ordinary-food registration.** Fully eating it spawns `TrashBananaPeel` through `Edible.trash`. Knife butchering independently produces a peel. Ingestion trash is emitted only on full consumption, not each default 5u bite. The peel has its own dynamic physics, Slippery, StepTrigger intersection ratio .2, non-hard slip fixture, 4u PulpedBananaPeel solution, special-digestion eligibility and grinding. Peel art alone does not implement contact behavior; do not substitute generic trash or silently omit either output route. See the direct mutation-target audit below for goldenApple, mimana, lemoon and extradimensionalOrange.

## Source indexes

- `Resources/Prototypes/Hydroponics/seeds.yml` 82–108,203–230,258–315,737–762; `Content.Server/Botany/SeedPrototype.cs` for defaults.
- `Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml` bases 4–44, banana/peel 328–369 and 420–474, citrus 649–779, apple 1088–1123. Species packet leaves: `Entities/Objects/Specific/Hydroponics/seeds.yml`.
- `Content.Server/Botany/Systems/BotanySystem.Produce.cs` 12–39; shared grinder system 351–401.
- `Content.Shared/Nutrition/Components/EdibleComponent.cs` 18–40,64–74; `.../EntitySystems/IngestionSystem.cs` 501–527 and `IngestionSystem.API.cs` 143–164 for bite/trash behavior.

Existing A/C asset briefs index commissioned identities; this audit neither changes their historical contexts/hashes nor establishes delivery, product registration, every special consumer or a working mutation graph.

## Direct mutation targets

All four targets below directly inherit FoodProduceBase, not their similarly named ordinary-fruit products. Their seed `mutationPrototypes` target lists are omitted/empty; this is distinct from `Mutations`, the applied effect records. Packets retain the shared SeedBase ancestry.

| Seed / product | Initial food contents; capacity | Fixed JUICE |
|---|---|---|
| goldenApple / FoodGoldenApple | Nutriment 10u, Vitamin 4u, DoctorsDelight 13u: **27u contents, 30u capacity** | JuiceApple 10u + Gold 10u |
| mimana / FoodMimana | MuteToxin 5u + Vitamin 2u; 7u | JuiceBanana 10u + Nothing 5u |
| lemoon / FoodLemoon | Vitamin 4u + Milk 16u; 20u | Sake 10u |
| extradimensionalOrange / FoodExtradimensionalOrange | Haloperidol 5u + Nutriment 5u + Vitamin 4u; 14u | JuiceOrange 10u |

Golden apple seed chemistry adds DoctorsDelight 3–13/10 to apple's chemistry. Mimana generates MuteToxin 1–5/20 and Nutriment 1–2/50, **not** its prototype starting Vitamin. Lemoon generates Vitamin 1–4/25 and Milk 8–20/5. Extradimensional orange generates Haloperidol 1–5/20, Nutriment 1–5/20 and Vitamin 1–4/25. Resolve complete seed defaults before profile emission.

Mimana explicitly defines both full-eat trash and knife-butchering output to TrashMimanaPeel; neither is inherited from FoodBanana. Extradimensional orange authors Sprite scale .5 and PotencyVisuals bounds .5..1; these are not multiplicative duplicate factors. No teleportation consumer is established by its name. Other inherited food/grind/injection behavior remains shared.

Extradimensional orange now has TEST-only Produce/ingestion/independent food-solution/Extractable profiles. Focused candidate/source/identity/Produce checks pass14tests/4suites, zero failures/errors/skips; post-check source review passed. The appearance consumer check uses registered wheat as an explicitly labeled temporary surrogate and restores its exact prior client catalog; it is not a live orange binding. Five full fruit/peel mappings independently match parsed pinned source and form a68-record parent-closed reference union. Banana/mimana/peel capture does not implement their contact behavior. See CURRENT for artifacts and remaining art/native boundaries.

Source: seed YAML 111–135,233–256,318–345,765–794; produce YAML 373–418,688–722,781–822,1125–1165. The source `SeedPrototype.cs` 237–246 distinguishes applied effects from species-target IDs.

## Full-consumption item-output implementation and boundary

The generic item-only `Edible.trash` path is implemented by extending the resolved `IngestionProfile` and the existing `BodyLiquidInteraction` transaction, reached by `BodyTimedIngestionService` / `TimedIngestionAccess`; no parallel food/output owner was added. Trash IDs remain an ordered list, so repeated entries stage independent entities. Output references validate against the registered item registry during prototype candidate validation. The current five-species and expanded fruit catalogs have no production trash bindings because their corresponding art/content work is not installed; this support does not make banana or mimana accessible.

Only an actually source-removing final dose stages the configured item outputs; partial bites and cancelled sessions emit none. Existing dose planning, live-hand/world-source commit, stomach update, and their compensation remain in the ingestion owner and are reused. `FreshWorldOutputBatch` stages all trash before source/stomach commit. Held sources adapt source coordinates to the owning body's feet; loose food uses its exact world position. Trusted pickup is attempted only when ingestion began with a held source, using fresh hand revisions for each output; failure leaves that output world-owned. World-eaten sources never auto-pick up trash. No parent genetics/reagents are copied. This item-only consumer does not implement anomaly effects, mobs, guns, knife butchering, or Slippery peel contact.

The shared batch now has an explicit result-returning commit seam while retaining existing `Runnable` callers. SUCCESS retains outputs, DENIED compensates staged outputs even when source-proof revision has advanced, and RECOVERY_REQUIRED preserves potentially committed outputs. Focused native fixtures cover authenticated held/world timed ingestion, partial/final consumption, cancellation, pickup fallback, and publication/commit compensation; they are compiled author evidence only and have not been executed. Those fixtures do not establish art-backed fruit availability, owner review, or full-catalog acceptance.
