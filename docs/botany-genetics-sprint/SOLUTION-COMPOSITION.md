# Independent item food solutions

## Scope and source

Current requirement: `FoodCornTrash` has a `food` solution (20u capacity, Cornmeal10) but **no Edible, Produce, Extractable or Seed**. Fake ingestion is not an acceptable representation. Corn, bungo and cherry produce fresh fixed cob/pit outputs; these do not inherit the consumed fruit's chemistry/genetics.

Pinned source: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, `produce.yml:1200–1267,2377–2445,2682–2752`; full food/BaseItem ancestry is audited. `SharedSolutionContainerSystem.cs:1052–1055,1126–1206` initializes each entity's declared solutions on map-init. Solution-manager default capacity2 is dictionary allocation, not fluid volume. `SharpSystem.cs:104–123` spawns butcher outputs without source chemistry transfer.

## Architectural decision

Introduce independent typed **item food-solution composition**, keyed by actual item prototype ID, with capacity/initial contents in cents. Support the existing `food` solution only; this is not a general multi-solution framework. Use existing prototype inheritance, runtime registration and aggregate client/server sync.

- Move initial chemistry out of both ingestion's nested source and Produce's fallback. One authoritative profile replaces both; no long-lived precedence/compatibility path. Ingestion retains consumption policy; Produce retains genetics/visual composition; Extractable retains grind/juice capability.
- Reuse the guarded materializer and generated-produce capacity rules. One shared owner handles source lookup/materialization; item adapters, ingestion, grinding, slicing and fresh fixed outputs delegate rather than copying initializer logic.
- A non-edible source-capable item adapter must not grant drinking, transfer, refill, draining, ingestion or extraction. Reuse `IReagentTrait` defaults. Existing adapters should share the same source owner.
- Fresh fixed outputs initialize their own resolved source before publication through the existing stack builder/batch transaction. They never copy source genes, chemistry or trash. Generated plant outputs retain their specialized generation path.
- Preserve absence versus existing-empty state. Never overwrite an existing reagent component or refill malformed generated-produce provenance. Preserve generated capacities and lazy lookup bypasses.

## Required evidence

Migrate MAIN and TEST definitions without changing effective chemistry, preserving actual parent relationships and canonical item keys. Update codecs, constructors, runtime snapshots/sync, direct consumers and source comparators together; no schema aliases or second initializer.

Migration resolves the **old effective item composition**, not each obsolete field independently: the old resolver preferred ingestion chemistry and only then the Produce fallback. The two TEST Ambrosia Produce `source_solution:null` entries do not erase their authoritative ingestion chemistry. Migrate that chemistry unchanged and remove the obsolete null fallback fields. Duplicate wheat definitions agree (1000-cent capacity/Flour1000) and become one profile. A genuinely absent old source remains absent.

Checks must cover inheritance/defaults, real MAIN lookup/consumer paths, aggregate sync, no-refill/overwrite/absent guards, generated capacities, slicing/ingestion/grinding, and fresh fixed outputs. Extend the existing raw-source comparator using the six captured fruit/cob/pit mappings; explicitly distinguish source solution presence from Edible/Extractable/Produce presence. Unregistered candidates must remain rejected by production output validators, while source composition may be resolved and decoded separately for TEST evidence.

Corn/pit registrations and authentic assets remain separate blocked work. Model/helper tests do not prove live cob acquisition or authenticated pit planting. Implementation status and receipts belong in CURRENT.md.
