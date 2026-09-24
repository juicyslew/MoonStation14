# Milestone 7: bounded deferred-effect completion record

**Audit date:** 2026-09-23
**Upstream reference:** `c9df5ef5d675b0d1d226828bddf6b78c28502d91`
**Scope:** Milestone 7's controlled unsupported policy and bounded dependency-owned slices; not a claim that all deferred effects now have upstream behavior.

## Policy and classification

Milestone 7 replaces harmful or invented approximations with explicit unsupported registrations and useful missing-owner reasons. It also permits bounded behavior where an existing owner supports it, without claiming missing physiology. The current exhaustive dispatcher classification remains 20 functional/partial registrations and 14 explicitly unsupported registrations across 34 `EffectData` variants. `Vomit` is partial, not unsupported. The local ledger separately records 34 effect variants and 19 status prototypes; statuses are not extra effects.

The 14 explicitly unsupported effects and their missing owners are:

| Effect | Missing owner system(s) |
|---|---|
| `ModifyBleed` | Authoritative blood and bleed-rate state |
| `Oxygenate` | Respiration/organ model with saturation ownership |
| `ModifyLungGas` | Lung organ and authoritative gas-mixture ownership |
| `ModifyBloodLevel` | Authoritative blood volume/max and regulation |
| `CleanBloodstream` | Route-aware bloodstream contents and reagent exclusion rules |
| `AdjustTemperature` | Authoritative temperature and heat-capacity/energy transfer |
| `ResetNarcolepsy` | Authoritative narcolepsy incident/timer state |
| `ReduceRotting` | Rot state and death/decay ownership |
| `CauseZombieInfection` | Infection, immunity, and death-conversion ownership |
| `CureZombieInfection` | Infection, immunity, and death-conversion ownership |
| `ArtifactDurabilityRestore` | Artifact node and durability ownership |
| `ArtifactUnlock` | Artifact node and unlock/progression ownership |
| `MakeSentient` | Mind/sentience and ghost-role control ownership |
| `Polymorph` | Entity replacement, controller/inventory transfer, and revert ownership |

The support-matrix test asserts these exact 14 classes are explicitly registered unsupported, that they are disjoint from the 20 handlers, and that each dispatches without accessing a context. No blood damage substitution remains enabled.

## Bounded behavior and explicit limits

- **Vomit remains partial.** Eligible living stomach targets have their full stomach solution emptied, including the live digestion transaction source, before spill is attempted. Initialized custom hunger and thirst owners receive `-40`, including for an empty stomach. Status-capable living targets receive `vomiting_slowdown` at 0.5 movement speed for 267 ticks; non-shortening `UPDATE` and ordinary status lifecycle cleanup apply. There is no route-aware bloodstream purge or forensics behavior, no synthetic vomit reagent, and failed spill placement loses contents already emptied. Death/clone transport still follows default attachment policy; no Vomit-specific death/clone guarantee is made.
- **SatiateThirst is a bounded implemented slice.** It applies `factor * scale` only to an already initialized thirst state on an explicitly eligible entity; it does not initialize state or project/mutate vanilla `FoodData`. Enrollment currently uses the temporary `thirst_eligible` player-and-villager tag, not character prototypes; the jetpack-flight exemption is absent. See the [thirst owner audit](2026-09-23-thirst-owner.md).
- **Stomach/ingestion is bounded, not full physiology.** Bottle sips and player puddle drinking use a 50-unit stomach. Digestion uses a separate digestion-only route into the 1000-unit shared body reagent destination; product overflow is intentionally lost. Reagents without digestion entries transfer afterward at up to 0.25 units per key and 0.5 efficacy, with capacity-safe admission. The temporary player-and-villager tag is an opt-in surrogate; enrollment on playable character prototypes remains pending. Literal Minecraft interaction callback plumbing has not been tested by the isolated GameTests. See the [stomach ingestion audit](2026-09-23-stomach-ingestion.md).
- **Reactive puddle touch is intentionally disabled and deferred.** The previous M6 direct-contact implementation/claim is superseded; walking into puddles has no consumption or effects. Revisit only when real slip eligibility/threshold, sliding exclusion, chance roll, and reactive-target behavior exist. This does not change the 20 handler / 14 unsupported effect matrix counts. See the [reactive touch audit](2026-09-23-reactive-touch.md).
- **Fire follows the same bounded policy.** Existing fire-stack, ignition-state, and wetness slices remain available where local capability permits. Missing protection/equipment, oxygen/atmosphere, temperature/heat-capacity, target-flammability, and burning owners defer only their dependent fidelity; there is no blanket fire restriction and no claim of full burn simulation.

These slices do not assert broader anatomy, bloodstream, metabolism, character-prototype enrollment, or full vanilla death/clone behavior. For example, default player death/clone attachment behavior is not a custom transport contract, and ingestion callback wiring remains an integration gap.

## Completion boundary and validation

**Milestone 7's bounded policy is complete; the deferred effects are not all behaviorally implemented.** Fourteen effects remain explicitly unsupported as listed above. This record does not establish the project's global Definition of Done: Milestone 8 and project-wide completion remain pending.

Independent validation completed: ` .\gradlew.bat test compileJava compileGametestJava build` completed with `BUILD SUCCESSFUL`; ` .\gradlew.bat runGameTestServer` completed with all 64 required tests passing in the latest `run/logs/latest.log`. That run also logged one known unrelated jug loot-table parse error and an expected test-generated missing-status error; neither prevented the required tests from passing. Python parsed all 600 JSON files under `src/main/resources`, and `git diff --check` passed with existing line-ending warnings. These results validate the bounded Milestone 7 work, not the remaining 14 unsupported effects or the project's global Definition of Done. Milestone 8 remains pending, including broader integration verification and the final supported/partial/deferred matrix. The upstream commit is the pinned reference above; no new upstream behavior is inferred beyond the referenced audits and local implementation.
