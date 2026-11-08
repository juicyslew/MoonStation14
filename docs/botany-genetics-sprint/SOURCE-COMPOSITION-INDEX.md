# Primary catalog composition index

Reference: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, verified during the referenced audits. This indexes source findings for all **71 primary seed rows**, not 71 installed profiles, complete gameplay parity, accepted art or all downstream output compositions. The production catalog still has five species. CATALOG-REFERENCES.json remains identity inventory, not runtime configuration.

The groups below are disjoint and total 71. Use their source paths to resolve every required value when implementing/importing a row; a prose summary is not an authoritative complete serialized profile.

| Rows | Seeds | Starting audit |
|---|---|---|
| 5 | wheat, tomato, potato, poppy, aloe | `FOUNDATION-COMPOSITION.md`, accepted `../botany-sprint/HANDOFF.md`, baseline core/processing audits |
| 4 | meatwheat, blueTomato, bloodTomato, lily | `MUTATION-TARGET-COMPOSITION.md` |
| 9 | banana, mimana, lemon, lemoon, lime, orange, extradimensionalOrange, apple, goldenApple | `FRUIT-COMPOSITION.md` |
| 7 | carrots, cabbage, eggplant, onion, onionred, pumpkin, watermelon | `VEGETABLE-COMPOSITION.md` |
| 5 | papercane, towercap, steelcap, cotton, pyrotton | `MATERIAL-OUTPUT-COMPOSITION.md` |
| 6 | oat, sugarcane, teaPlant, tobacco, cannabis, rainbowCannabis | `LEAF-GRAIN-COMPOSITION.md` |
| 5 | gatfruit, fakeCapfruit, realCapfruit, nettle, deathNettle | `WEAPON-PRODUCE-COMPOSITION.md` |
| 10 | chanterelle, lingzhi, flyAmanita, ambrosiaVulgaris, ambrosiaDeus, galaxythistle, glasstle, spacemansTrumpet, chili, chilly | `REMAINING-FOOD-COMPOSITION.md`, medicinal section |
| 8 | pineapple, garlic, corn, grape, cocoa, berries, bungo, cherry | same document, ordinary fruit section |
| 6 | rice, soybeans, koibean, pea, laughinPea, worldPea | same document, legume section |
| 5 | killerTomato, bloonion, anomalyBerry, holymelon, bluePumpkin | `SPECIAL-PRODUCT-COMPOSITION.md` |
| 1 | eggy | `EGG-COMPOSITION.md` |

## Cross-cutting implementation invariants

- Generation ensures Produce even on outputs such as FoodEgg, assigns the actual seed reference, applies produce-eligible recorded mutations, replaces the generation solution contents, extends retained capacity by generated amounts, and writes potency Appearance. Do not infer runtime-added composition solely from authored YAML absence.
- Static JUICE, current grind solution, prototype initial contents/capacity, genetic chemistry and secondary output recipes are separate authorities. Killer tomato generates default `food` but grinds `bloodstream`; do not conflate channels.
- Initial supported `food` composition is now an independent typed item profile, not an Edible/Produce field: `SOLUTION-COMPOSITION.md` describes its shared materializer, inheritance and capability boundaries. Nonedible corn cob is a concrete source case; pineapple_slice and lily must resolve their own source-profile IDs through inheritance. Current per-unit evidence is in `CURRENT.md`.
- Species-target lists are `MutationPrototypes`, not applied `Mutations`. Preserve ordered edges and instance identity over packet/product fallback (especially worldPea/PeaSeeds and holymelon/watermelon).
- Utensil-Knife slicing, Sharp knife butchery, Sharp Log cutting, full-eat trash and use-in-hand spawning are distinct consumers/admission rules. Output counts describe entities, not an automatic native stack conversion.
- Source-only item names do not establish food, physics, contact, combat or mob behavior. Restricted ruminant ingestion, dynamic explosive structures, firearms, instruments, head clothing, refinement, timed anomaly loot, smokeable crafting and mob control each retain their own dependency.

## Still unresolved before complete catalog acceptance

Resolve unsupported consumers and downstream output prototypes; verify all seed/default fields against source at emission; complete local item/mob registration and correct resolved runtime consumption; integrate reviewed authored art; extend assets only for genuinely uncovered identities after reuse/non-overlap checks. Existing commissioned item identities do not imply every newly identified slice, pit, shell, clothing, dried/ground ingredient, gun or effect is commissioned or implemented. Existing historical asset-request contexts/hashes remain immutable.

Primary source mapping does not establish full construction/temperature, smoking, reagent physiology, instrument networking, ghost takeover, anomaly execution, crafting or every secondary inheritance chain. Record explicit feature-specific support/deferrals rather than flattening such products into ordinary food. Native/readiness, connected-client/GPU fidelity and owner acceptance remain separate evidence boundaries.
