# Bloodstream prototype migration

Character physiology is configured only by a typed `Bloodstream` entry in `components`:

```json
"components": [{"type": "Bloodstream", "reference_solution": {"moonstation14:blood": 300}, "metabolism_exclusions": ["moonstation14:blood"], "...": "all other former blood fields"}]
```

For an existing pack, manually move **all** fields from the top-level `blood` object into one `components` entry, add `"type": "Bloodstream"`, and delete the top-level `blood` key. Do not copy it into both places. Packs retaining `blood` (even `null`) are rejected with a migration error, not silently interpreted. Inheritance permits partial typed-component fragments: first parent supplies each missing field; a child replaces individual fields, including whole nested maps. There is no `remove` tombstone. Absence of the component makes blood physiology inert.

The typed prototype component carries an immutable `BloodstreamPolicy` data value; it is not a separate catalog authority or the entity's mutable persisted BLOOD or BLOODSTREAM reagent contents. Their storage, codec IDs and legacy pig checks remain unchanged. Upstream SS14 `BloodstreamComponent` supplies the conceptual ownership model (blood reference solution, regulation, bleeding, recovery), while existing Minecraft cadence, damage multipliers, reagent IDs, and tuned species values remain local policy. No upstream solution containers, audio, status effects or networked component state are implied by this migration.

## Validation boundary

The final `build --no-daemon` passed; its XML reports **1,230 unit tests, zero failures**, and `compileGameTestJava` passed. The historical isolated GameTest results (209/209 passed and a later 208/209 with one unrelated failure) predate tombstone removal and this migration. Per instruction, no GameTestServer run was made after those changes; post-migration server GameTest acceptance remains unverified.
