# Effect and Status Dependency Ledger

This document lists all 34 `EffectData` variants and all 18 local status prototypes as separate inventories, plus deferred reactive puddle contact; effects are not all statuses. Reference upstream commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Status markers and timers are not full behavior. Apply one policy to every effect: implement bounded faithful behavior supported by existing owners now, and defer only the behavior that needs a missing owner. Fire effects follow the same policy; missing protection/equipment, oxygen/atmosphere, temperature/heat-capacity, target-flammability, or burning owners identify fidelity gaps, not a blanket bar on useful fire-effect slices.

## EffectData variants (34)

| Effect | Current state | Missing systems for upstream fidelity |
|---|---|---|
| `EvenHealthChange` | Supported, bounded | Per-type armor/resistance allocation is missing (vanilla currently aggregates damage); death/totem ledger reconciliation only where applicable. |
| `HealthChange` | Supported, bounded | Per-type armor/resistance allocation is missing (vanilla currently aggregates damage); death/totem ledger reconciliation only where applicable. |
| `Vomit` | Partial | Eligible living stomach targets empty their entire stomach solution, including from the live digestion transaction, and spill actual contents. Initialized custom hunger/thirst owners receive -40 even if empty. Status-capable living targets receive the canonical 0.5 `vomiting_slowdown` movement status for the upstream-derived 267 ticks, using non-shortening UPDATE and normal status lifecycle cleanup. Noneligible/dead entities are untouched; shared REAGENT and bloodstream are never purged and no synthetic vomit reagent is added. Missing owners: route-aware bloodstream flush/forensics. Spill egress is non-atomic: failed placement loses already-emptied contents, matching upstream ordering. |
| `Jitter` | Supported | Client observer/presentation parity only, if upstream parity is desired; local jitter is implemented. Finite immediate metabolism UPDATE gets a LOCAL 21-tick floor only while the same reagent has a live ongoing body/stomach reservoir; unlike pinned upstream this is a deliberate cadence bridge. |
| `Drunk` | Supported | Drunken-behavior status projection. |
| `ModifyBleed` | Unsupported | Authoritative blood and bleed-rate state. |
| `Oxygenate` | Unsupported | Respiration/organ model with saturation ownership. |
| `ModifyLungGas` | Unsupported | Lung organ and authoritative gas-mixture ownership. |
| `AdjustAlert` | Supported, bounded | Optional upstream icon/category presentation; unsettled alert death/clone ownership policy is a parity gap, not a blocker to the local effect. Core registry and localized name exist. |
| `SatiateHunger` | Supported, bounded | Character-prototype enrollment and physiology/stomach integration. Passive hunger owner uses temporary `thirst_eligible` player/villager enrollment, staggered one-second-per-20-tick derived decay, threshold alerts, and starving speed projection; no vanilla FoodData/full hunger, configured starvation damage, jetpack exemption, or full physiology. |
| `ModifyBloodLevel` | Unsupported | Authoritative blood volume/max and regulation owner. |
| `SatiateThirst` | Compatibility-limited | Existing opt-in thirst state and decay owner; handler applies factor*scale only to an initialized `thirst_eligible` entity. Character-prototype enrollment and jetpack-flight exemption remain absent. Ingestion now has a separate temporary `thirst_eligible` enrollment and character-owned stomach route. |
| `PopupMessage` | Supported | None known within the accepted registered local/PVS delivery boundary. |
| `Emote` | Supported, bounded | Availability whitelist/prototype policy, species/vocal eligibility, action blocker; chat-only `force` semantics; guidebook projection. |
| `ModifyStatusEffect` | Supported | See status-prototype table for missing per-status projections. Eligible finite immediate metabolism UPDATE gets the LOCAL ongoing-reservoir 21-tick floor; ADD/REMOVE/SET, manual calls, delayed/permanent durations, and exhausted sources retain normal semantics. |
| `AdjustReagent` | Supported in body metabolism; digestion explicitly skipped | Digestion prototypes currently contain no AdjustReagent. Handler capacity is body-trait capacity and is not safe for a 50-unit stomach source. Requires explicit source-capacity-aware context before enabling; source-solution transaction routing otherwise exists. |
| `CureZombieInfection` | Unsupported | Infection, immunity, and death-conversion ownership. |
| `ArtifactDurabilityRestore` | Unsupported | Artifact node and durability ownership. |
| `ArtifactUnlock` | Unsupported | Artifact node and unlock/progression ownership. |
| `GenericStatusEffect` | Compatibility-limited | None known for this effect's intentional fixed allowlist boundary; arbitrary reflection is not promised. Eligible finite immediate metabolism UPDATE gets the LOCAL ongoing-reservoir 21-tick floor. |
| `Flammable` | Supported, bounded (fire stacks) | Target flammability capability and burning-state owner for fuller behavior; protection/equipment, oxygen/atmosphere, and temperature/heat-capacity owners are needed for corresponding burn simulation fidelity. Implement bounded faithful stack behavior where existing capability permits; missing owners defer only their dependent behavior. |
| `Ignite` | Partial (ignition) | Target flammability capability and burning-state owner for fuller behavior; protection/equipment, oxygen/atmosphere, and temperature/heat-capacity owners are needed for corresponding burn simulation fidelity. Implement bounded faithful ignition behavior where existing capability permits; missing owners defer only their dependent behavior. |
| `AdjustTemperature` | Unsupported | Authoritative temperature and heat-capacity/energy-transfer ownership. |
| `Extinguish` | Supported, bounded (wetness) | Target flammability capability and burning-state owner for fuller behavior; protection/equipment, oxygen/atmosphere, and temperature/heat-capacity owners are needed for corresponding burn simulation fidelity. Implement bounded faithful wetness/extinguishing behavior where existing capability permits; missing owners defer only their dependent behavior. |
| `MovementSpeedModifier` | Compatibility-limited | Independent walk and sprint movement projection. Eligible finite immediate metabolism UPDATE gets the LOCAL ongoing-reservoir 21-tick floor. |
| `CleanBloodstream` | Unsupported | Route-aware bloodstream contents and reagent exclusion rules. |
| `MakeSentient` | Unsupported | Mind/sentience and ghost-role control ownership. |
| `Polymorph` | Unsupported | Entity replacement, controller/inventory transfer, and revert ownership. |
| `ResetNarcolepsy` | Unsupported | Authoritative narcolepsy incident/timer state. |
| `ModifyKnockdown` | Partial | Physical control, prone/crawling, action blocking, and held-item dropping. |
| `Electrocute` | Partial | Conductivity/insulation policy and real physical stun projection. |
| `EyeDamage` | Compatibility-limited | Eye anatomy and graded vision/partial-injury projection. |
| `ReduceRotting` | Unsupported | Rot state and death/decay ownership. |
| `CauseZombieInfection` | Unsupported | Infection, immunity, and death-conversion ownership. |

## Local status prototypes (19)

These filenames are status prototypes, not additional `EffectData` variants. The status core already exists; entries below identify behavior projections not supplied by a marker/timer.

| Status ID | Current projection | Missing systems for full behavior |
|---|---|---|
| `statuseffectwoozy` | Marker | Client drunk visual overlay. |
| `statuseffectstunned` | Marker | Input/action stun and physical control projection. |
| `statuseffectstimulantsstamina` | Marker | Stamina-system projection. |
| `statuseffectseeingrainbow` | Marker | Rainbow drug shader/overlay. |
| `statuseffectscrambled` | Marker | Scrambled speech/accent system. |
| `statuseffectradiationprotection` | Marker | Radiation protection/resistance owner. |
| `statuseffectpainnumbness` | Marker | Pain-system projection. |
| `statuseffectowo` | Marker | Speech transformation projection. |
| `statuseffecthemorrhage` | Marker | Blood/bleeding owner and hemorrhage behavior mapping. |
| `statuseffectforcedsleeping` | Marker | Sleep state and forced-sleep behavior owner. |
| `statuseffectdrunk` | Marker | Upstream status combines woozy visual overlay with slurred speech. |
| `statuseffectdrowsiness` | Marker | Sleep/drowsiness behavior owner. |
| `statuseffectdesoxystamina` | Marker | Stamina-system projection. |
| `statuseffectbark` | Marker | Speech transformation projection. |
| `statuseffectanticoagulant` | Marker | Blood/bleeding and anticoagulant behavior owner. |
| `reagentspeedstatuseffect` | Movement-speed multiplier 0.5 | Independent walk/sprint parity only where needed; movement-speed projection already exists (not device reagent speed). |
| `knockdown` | Marker | Input/action blocking and physical knockdown/crawling projection. |
| `jitter` | Local player camera jitter | Optional remote observer presentation; local client jitter works. |
| `vomiting_slowdown` | Movement-speed multiplier 0.5 | Status timer/projection lifecycle is implemented; remaining Vomit fidelity gaps are listed in the effect row. |

Upstream status mappings above reference `Resources/Prototypes/Entities/StatusEffects/misc.yml`, `Resources/Prototypes/Entities/StatusEffects/speech.yml`, `Resources/Prototypes/Entities/StatusEffects/movement.yml` and their client visual, speech, and movement systems at commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Reactive puddle contact (separate from both inventories)

| Current local behavior | Pinned upstream behavior and faithful prerequisites |
|---|---|
| **Pending; no local puddle contact gameplay.** Walking/contact does not consume contents, run reactive effects, or create exposure state. Reactive prototype records are decoded but unreachable from puddles. | Pinned upstream `Content.Server/Fluids/EntitySystems/PuddleSystem.cs` lines 245–267 applies touch only on `SlipEvent` for eligible `ReactiveComponent` targets, excludes `SlidingComponent`, rolls 50%, and splits 15%. Defer contact eligibility until MoonStation has real slippery/slip threshold mechanics, sliding exclusion, the chance roll, and target reactive group/method eligibility. The 15% split is a planned reagent dose only, not a local skin store; no `SURFACE_EXPOSURE` attachment/item component exists. See [superseded/disabled local contact audit](audits/2026-09-23-reactive-touch.md). |

When the user asks to revisit a prerequisite owner, review affected rows, update this working document and the [current support matrix](audits/2026-09-22-milestone-5-current-effect-support-matrix.md). No automated reminder is intended.

## Compartment routing boundary

The stomach digestion pass validates its source against 50 units and its product destination against the living body's 1000-unit capacity. Body overflow is reported as per-attempt excess and is intentionally lost rather than silently reassigned to another compartment. Non-digestion stomach reagent transfer runs after digestion effects in the same scheduled pass: up to 0.25 per key is moved to shared body `REAGENT` at 0.5 efficacy, with capacity-safe proportional admission and no source loss when body capacity is unavailable. This is bounded routing, not full anatomy or blood modeling. Body metabolism runs before stomach routing, so transferred reagents are processed on the next due tick. Upstream `MetabolismExclusionEvent` blood exemptions have no local equivalent and are not reproduced. Generic/non-ingestion transfers continue to use shared `REAGENT`.

## LOCAL status/metabolism cadence bridge

This compatibility adaptation is distinct from pinned upstream commit [`c9df5ef5d675b0d1d226828bddf6b78c28502d91`](https://github.com/space-wizards/space-station-14/commit/c9df5ef5d675b0d1d226828bddf6b78c28502d91), which applies scaled status time and advances it each update. MoonStation's staggered metabolism is every 20 ticks and its status advances before metabolism on the due tick. Therefore qualifying finite, immediate `UPDATE` applications from `METABOLISM` are floored to 21 ticks only when the same reagent is confirmed live after removal in the current solution or in an actual stomach attachment that has an applicable BODY metabolism entry. This covers the stimulant transfer/bloodstream path without reading pre-removal snapshots as continued supply. Unsupported/unresolvable evidence fails closed. A stopped reservoir is not remembered; a final dose can leave a status for at most its ordinary duration. ADD, REMOVE, SET, delayed/permanent, manual calls, non-status effects, and unrelated status operations are unchanged. Focused pure cadence tests exist; a full real-EffectSystem stimulant GameTest remains follow-up validation.

**The 21-tick floor is local UX smoothing, not SS14 parity.** Pinned upstream
human `OrganBaseStomach` runs `Digestion` and `OrganBaseHeart` runs
`Bloodstream`; the default metabolizer interval is one second. Upstream
`Stimulants` has a Bloodstream entry but no Digestion entry, so the generic
Digestion transfer supplies at most 0.25 units at 0.5 efficacy. Fixed-point
quantization gives 0.12 body units, and the default 0.5-unit metabolism rate
makes scale 0.24: a two-second effect becomes 0.48 seconds (about 10 ticks,
ceil), less than the 20-tick organ interval. Refreshing via max(existing expiry,
now + time) does not add durations. Consequently upstream source code does not
guarantee no flicker for this low oral route and predicts possible gaps; it is
not evidence of actual client flicker. Direct bloodstream delivery of at least
0.5 units instead has scale 1 and a 40-tick two-second duration, enough to
overlap a one-second interval in that simple comparison. Actual dose, timing,
organ scheduling, and other systems/modifiers remain relevant. See the
[status cadence audit](audits/2026-09-23-status-cadence.md) for pinned source
links and calculation. Keep the local floor: removing it would discard the
deliberate local smoothing policy, not improve upstream fidelity.
