# MoonStation14 Reagent and Status-Effect Handoff

This is an implementation handoff for completing reagent-caused entity and status effects. It is an engineering specification, not a request to rewrite the whole mod. Inspect every touched file and its callers before coding; the inventory below is an audit baseline, not permission to bypass the existing architecture.

## Contents

1. [Scope and decisions](#scope-and-decisions)
2. [Project and reference baseline](#project-and-reference-baseline)
3. [Architecture to preserve](#architecture-to-preserve)
4. [Current audit](#current-audit)
5. [Pinned upstream execution contract](#pinned-upstream-execution-contract)
6. [Prescriptive target design](#prescriptive-target-design)
7. [Ordered milestones and acceptance gates](#ordered-milestones-and-acceptance-gates)
8. [Full 34-effect matrix](#full-34-effect-matrix)
9. [Tests and validation](#tests-and-validation)
10. [Definition of done](#definition-of-done)
11. [Risks and decision gates](#risks-and-decision-gates)

## Scope and decisions

### Required scope

Complete reagent-caused entity/status effects and the minimum metabolism, exposure, status, codec, and test infrastructure needed to make them truthful. Keep unrelated reactions, blocks, items, and general cleanup out of this work. Do not generate notable models or sprites. Request assets or use a trivial placeholder; preserve attribution for anything copied.

This is new development. The explicit user decision is **no backward compatibility, save migration, transitional status codec, or version migration** now or for similar changes unless the user later requests it. Do not spend implementation effort decoding old status data or retaining aliases solely for old saves.

The second explicit user decision is to use a **faithful phased port**. Do not make misleading vanilla approximations for effects whose prerequisite systems do not exist. Implement viable effects faithfully, and mark major absent-system effects explicitly unsupported/deferred.

### Operating rules

- Treat the pinned upstream behavior and the target design below as normative.
- Inspect touched source, codecs, registrations, resource examples, and call sites before changing them.
- Keep DTOs/data records separate from world mutation and attachment mutation.
- Do not claim an effect is complete because its codec decodes; prove behavior or explicitly return tested unsupported.
- No manual Minecraft/client/server launch is part of validation. The project owner will manually test separately; automated checks are required.
- Write material audits under `docs/audits/` so future sessions can resume from a durable record; see the [Milestone 1 runtime audit](docs/audits/2026-09-20-milestone-1-runtime-audit.md) and [schema fidelity audit](docs/audits/2026-09-20-reagent-schema-fidelity-audit.md).

### Owner architecture preference and approval gate

Follow existing MoonStation14 patterns by default. If an existing owner-created pattern appears materially inefficient, unsafe, poorly designed, or likely to block the requested implementation, the implementation agent may and should suggest an architecture change, but must not silently replace the pattern or broaden scope. The suggestion must identify the concrete current pattern/files, explain the observed problem with evidence, propose the smallest viable alternative, describe tradeoffs, migration scope, and compatibility impact, and state whether implementation can proceed without the change. Ask the project owner for approval before performing the architectural rewrite. Ordinary localized fixes that preserve the established architecture do not require a design-approval round trip.

## Project and reference baseline

| Item | Baseline |
|---|---|
| Repo root | `C:\Users\William\Documents\Minecraft Mod Dev Files\MoonStation14` |
| Java | 21 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.224 |
| ModDevGradle | 2.0.141 |
| Gradle wrapper | 9.2.1 |
| Upstream SS14 source | `C:\Users\William\Documents\SS14-dev\space-station-14` |
| Upstream behavior pin | local commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91` |

The upstream checkout is the behavior reference, not a request to literally port SS14 entities or ECS. The implementation must fit Minecraft/NeoForge and the existing MoonStation14 provider and registry patterns.

## Architecture to preserve

- Use system-oriented packages under `src/main/java/com/juicyslew/moonstation14/ms14`, rather than grouping only by Minecraft object type.
- `TraitHandler` pairs a holder with its logic trait.
- `MS14Provider` abstracts ItemStack DataComponents versus Entity/BlockEntity DataAttachments. `MS14Bridges`/`SystemLink` join the two representations. Preserve and extend this pattern; do not bypass it with parallel storage.
- Dynamic datapack registries are `ModReagents` + `ReagentData.CODEC` and `ModStatusEffects` + `StatusEffectData.CODEC`. Resources live in `data/moonstation14/moonstation14/reagent` and `data/moonstation14/moonstation14/status_effect`.
- Reaction recipes use vanilla `RecipeManager` and a custom recipe type.
- Entity reagent/status data currently use synced/persistent attachments. Item forms use persistent/network-synchronized components. Block entities use provider updates and block update packets.
- Metabolism currently runs once per second in `TickHooks`, then recalculates custom damage into vanilla health.
- `EffectData` is a 719-line sealed codec hierarchy with 34 effect variants. `ConditionData` has 8 variants. Jitter is the only end-to-end custom status behavior.

Important files to inspect before implementation include:

`EffectData.java`, `ConditionData.java`, `MetabolismData.java`, `ReagentData.java`, `ReactiveEffectsData.java`, `EffectContext.java`, `TickHooks.java`, `DamageHooks.java`, `MS14Provider.java`, `MS14Bridges.java`, `ReagentSystem.java`, `ReagentAttachment.java`, the `status_effect` package, `CameraJitterHandler.java`, `ModDataAttachments.java`, and `ModDataComponents.java`.

## Current audit

### Data and behavior inventory

- There are 411 reagent JSONs, 98 reaction recipes, and one status-effect JSON. There are no unit tests or GameTests.
- Of the 34 ordinary effect types, `EvenHealthChange`, `HealthChange`, `Vomit`, `Jitter`, `ModifyBleed`, and `ModifyBloodLevel` are narrowly or partially functional. The other 28 are no-op. None is fully faithful end-to-end.
- All 17 plant metabolism types throw `NotImplementedException` and have no executor. Keep botany out of this phase unless the user expands scope.
- 18 reagent prototypes contain 29 reactive-effect instances, but `ReactiveEffectsData.onTouched` is empty and has no call sites. `PuddleBlock` has no entity-contact hook.

### Status and reference inventory

- Status timers do not expire: `TickHooks` decrements a copied map and then writes the unchanged attachment. Zero entries are retained, and unknown registry entries never expire.
- `StatusEffectAttachment` maps `ResourceKey<StatusEffectData>` to `Float` seconds. That cannot represent delayed, pending, permanent, or parameterized statuses.
- Reagent files contain 54 `ModifyStatusEffect` and 19 `GenericStatusEffect` occurrences. They reference 15 new status IDs and 8 legacy keys. The new IDs are: `statuseffectseeingrainbow`, `statuseffectdrowsiness`, `statuseffectbark`, `statuseffectscrambled`, `statuseffectwoozy`, `statuseffectdesoxystamina`, `statuseffectpainnumbness`, `statuseffectstunned`, `statuseffectdrunk`, `statuseffectowo`, `statuseffecthemorrhage`, `statuseffectanticoagulant`, `statuseffectforcedsleeping`, `statuseffectradiationprotection`, and `statuseffectstimulantsstamina`.
- The legacy keys are: `pressureimmunity`, `stutter`, `ratvarianlanguage`, `adrenaline`, `jitter`, `muted`, `temporaryblindness`, and `pacified`.
- Only jitter currently has a status prototype.

### Data fidelity blockers

- 197 reagent files have a parent; 5 are abstract; 97 parented files have no local metabolisms and currently fail to inherit parent effects. The dynamic registry codec does not resolve inheritance.
- Implement deterministic inheritance resolution or flattening before claiming effect coverage. Define multiple-parent semantics and exclude abstract prototypes as concrete reagents.
- Codec/resource drift currently includes: codec expects `metabolismRate` while 28/31 explicit resources use `metabolismrate`; codec expects `ignoreResistances` while resources use `ignoreresistances`; two files use `conditions:null`; `Oxygenate.type` returns `ModifyBleed`; common `probability`, `minscale`, and `scaling` fields are missing; effect-specific fields are missing; `ModifyLungGas` ratios are gases rather than reagent IDs; piercing/pierce aliases differ; and the embedded mannitol ID has a case mismatch.
- Compatibility is not required. Normalize resources and codecs to one canonical schema instead of retaining aliases. Stop if a behavior-bearing field would be silently dropped.

### Execution and semantic defects

- The shared container treats digestion, bloodstream, respiration, and metabolites alike. Each stage calculates from stale original amount; metabolites are not scaled by actually consumed quantity; `MAX_REAGENTS_PROCESSABLE` is unused; effect context lacks source solution, stage, and organ; route-specific targets are impossible; capacity is hard-coded; health is hard-coded to 20; and `Math.random` is used.
- `ReagentCondition` ignores max and uses `Float.MIN_VALUE` incorrectly. `MetabolizerType` ignores inverted and assumes human. Temperature uses constant room temperature and exclusive bounds. Breathing, Internals, Tag, and Hunger always return true. Unsupported condition systems must evaluate raw false and then apply inversion, never permissive true.
- `ModifyBleed` mutates accumulated bloodloss damage instead of bleed rate. `ModifyBloodLevel` also adds bloodloss damage, so positive restoration harms the entity. `HealthChange` ignores resistances; `EvenHealthChange` hard-codes groups; vanilla health sync assumes max health 20.
- Other risks already present: unbounded reaction recursion/cycles, ignored catalyst quantities, mutable attachment maps requiring explicit sync, client jitter null/cast/global-timer issues, no semantic resource validator, and attribution concerns for imported assets/data.

## Pinned upstream execution contract

Use these pinned upstream files as the reference and record deviations explicitly:

- `Content.Shared/EntityEffects/EntityEffect.cs` — base effect data and execution contract.
- `Content.Shared/EntityEffects/SharedEntityEffectsSystem.cs` — common gate order, scaling, conditions, and dispatch.
- `Content.Shared/Metabolism/MetabolizerSystem.cs` — stage context, consumption, and routing.
- `Content.Shared/EntityEffects/Effects/**` — individual effect handlers and their semantics.
- `Content.Shared/EntityEffects/Effects/StatusEffects/BaseStatusEffectEntityEffect.cs` — status operation behavior.
- `Content.Shared/StatusEffectNew/**` — status lifecycle and definitions.
- `Resources/Prototypes/Entities/StatusEffects/{misc,movement,body,speech,damage}.yml` — status prototypes and projections.
- `Resources/Prototypes/status_effects.yml` — status definitions.
- `Resources/Prototypes/Chemistry/{metabolism_stages,metabolizer_types}.yml` — processing stages and profiles.

### Exact common execution order

For each effect: **reject scale below `minScale`; roll probability independently; require all conditions; if `scaling=false`, cap scale to `min(scale, 1)` without raising partial scales; dispatch the typed effect.** Conditions are ANDed. Use the level/entity `RandomSource`, never `Math.random`.

### Context and routing

The body/entity is the target for most effects. `ModifyLungGas` targets the organ/lung. `AdjustReagent` targets the source solution. `ReagentCondition` tests the source solution. `MetabolizerTypeCondition` tests the processing organ/profile. Stage context must carry enough source holder/solution, reagent key, stage, and metabolizer profile/organ information to make those routes explicit.

### Status operations

`ModifyStatusEffect` operations are:

- **Update**: create or replace with the maximum remaining duration.
- **Add**: accumulate duration or create.
- **Remove**: subtract duration, or remove a permanent status.
- **Set**: exact duration or create.

Nullable duration is permanent. Duration scales; delay does not. For pending applications, the earliest pending start wins. `GenericStatusEffect` is obsolete upstream and may only be a fixed compatibility adapter, never reflection or arbitrary component instantiation.

## Prescriptive target design

Keep this design minimal and incremental. Ask the user before replacing established provider/bridge architecture.

### Replacement boundaries

Do not integrate an owner system into Minecraft mechanics that the project intends to replace. Character-owned physiology, damage, and status owners must not rely on vanilla `FoodData` natural regeneration/starvation behavior or competing vanilla potion timers when those mechanics have an intended replacement; SS14 natural health regeneration, if present, is distinct and far slower. Prefer MoonStation14-owned systems and avoid unnecessary vanilla integrations during an unfinished sprint. Some engine bridges are intentional boundaries: health/damage projection and unavoidable crafting or block placement/removal interactions may connect to Minecraft mechanics, but these are explicit interfaces to redesign deliberately, not automatic ports of vanilla behavior. Preserve `MS14Provider`/`MS14Bridges` and the existing entity ownership model while defining those boundaries.

### Effect kernel

- Keep serialized `EffectData` records data-only.
- Add flat common effect envelope/fields (`conditions`, `minScale`, `scaling`, `probability`) into every subtype codec; do not change JSON to a nested common object.
- Add a central server-authoritative `EffectSystem`, `ConditionSystem`, `EffectResult`, and typed handler dispatch under `ms14/effect` or an equivalent system package. DTOs must not mutate attachments/world directly.
- Expand `EffectContext` with `ServerLevel`, target/body, optional actor/source, `EffectCause`, scale, injected `RandomSource`, and optional reagent exposure information: source holder/solution, reagent key, stage, and metabolizer profile/organ.
- Return explicit results such as `APPLIED`, `SKIPPED_SCALE`, `SKIPPED_PROBABILITY`, `SKIPPED_CONDITION`, `SKIPPED_UNSUPPORTED`, and `FAILED`. Known effects whose owning systems are absent warn once and return unsupported. Malformed or unknown effect types fail datapack loading. A target lacking a required capability is a quiet no-op. Reagent consumption remains independent of effect support.

### Status core

- Replace float status values with a `StatusEffectInstance` containing integer ticks, delay, finite/permanent duration, active/pending state, and a typed payload only where application-specific data varies. No legacy float migration codec is needed.
- Preserve `StatusEffectAttachment`/`StatusEffectComponent`, `MS14Bridges.STATUS_EFFECT`, and provider updates. Defensively copy maps, expose read-only queries, centralize mutation, and synchronize status transitions rather than every countdown tick.
- Add `StatusEffectOperation` and a pure `StatusEffectReducer` implementing Update/Add/Remove/Set. Add lifecycle hooks `canApply`, `onApplied`, `onChanged`, `ensureApplied`, and `onRemoved`. `ensureApplied` must be idempotent during entity reload and transient-attribute reconciliation.
- `StatusEffectData` uses its registry key as authoritative identity and defines typed composable behaviors/eligibility, not hard-coded status-ID switches or arbitrary data bags. Initial behaviors may cover markers, transient attributes, safe vanilla-effect projection, and client jitter. Do not literally port SS14 status entities/ECS.
- Map `GenericStatusEffect` known `(key, component)` pairs to canonical status definitions through an explicit allowlist. Unknown aliases warn once and do nothing. Mark it compatibility-only/deprecated while continuing to decode current data.
- Adapt Dedicated Jitter, Drunk, MovementSpeedModifier, ModifyKnockdown, and Electrocute into this status system instead of separate timers. Use deterministic transient attribute IDs. The attachment is authoritative; do not maintain competing vanilla timers.

### Client and exposure boundaries

- Client presentation belongs in client-only packages/classes and must be safe on dedicated servers. Fix jitter player-null handling and derive animation phase without global shared mutable timers.
- Wire reagent touch through `EffectSystem` with `CONTACT` cause, puddle/entity exposure, solution quantity-derived scale, and duplicate/tick-spam protection.
- Do not add broad provider, reagent, or reaction rewrites beyond context/status support required by the milestones.

## Ordered milestones and acceptance gates

1. **Baseline resource/schema validation and inheritance resolution.** All reagent files decode under one canonical schema; resolved children include parent effects; abstracts are excluded as concrete reagents; IDs/references resolve; every effect record round-trips; and Oxygenate round-trip passes. Stop if unresolved fields would be silently dropped.
2. **Common effect kernel and conditions.** Central gate order and deterministic-random tests pass. Implement inclusive min/max and inversion correctly; unsupported conditions are raw-false; no per-effect probability duplication.
3. **Correct metabolism context.** Consumption and scale use actual available quantity; metabolites equal actual removed quantity times ratio; no stale multi-stage over-application; process cap is enforced; stage/source routing is present. A full anatomy simulation is not required, but shared compartments must not be described as faithful physiology.
4. **Status core.** New codec/component/attachment format, reducer, lifecycle, exact tick expiry/removal, delayed/permanent support, no packet spam, and status definition/reference validation.
5. **Viable low-dependency effects.** Faithfully implement `HealthChange`, `EvenHealthChange`, `AdjustReagent`, `SatiateHunger`, `PopupMessage`, `Emote`, `Jitter`, `Drunk`, `ModifyStatusEffect`, allowlisted `GenericStatusEffect`, `Flammable`, `Ignite`, `Extinguish`, `MovementSpeedModifier` where walk/sprint semantics match, `ModifyKnockdown` with explicit degradation if crawling is unavailable, `Electrocute` with shock damage/stun and insulation policy, `EyeDamage` with persistent threshold behavior, and `AdjustAlert` state/presentation without notable assets. Codec-only work is not completion.
6. **Reactive touch wiring.** Use `EffectSystem` with `CONTACT` from puddle/entity exposure; derive scale from solution quantity; prevent every-tick spam and duplicate application; implement supported touch effects and explicitly report deferred ones.
7. **Deferred dependency effects.** Add controlled handlers/results and documentation without invented approximations. Deferred list: `Vomit` full physiology (existing puddle behavior may remain explicitly partial), `ModifyBleed`, `Oxygenate`, `ModifyLungGas`, `ModifyBloodLevel`, `SatiateThirst`, `CleanBloodstream`, `AdjustTemperature`, `ResetNarcolepsy`, `ReduceRotting`, `CauseZombieInfection`, `CureZombieInfection`, `ArtifactDurabilityRestore`, `ArtifactUnlock`, `MakeSentient`, and `Polymorph`. Disable the currently harmful `ModifyBloodLevel`/`ModifyBleed` damage substitutions until real blood state exists.
8. **Verification and limited cleanup.** Remove empty TODO/no-op handlers in favor of explicit support status; confirm no client imports on server paths; run all required checks; and produce a final supported/partial/deferred matrix.

## Full 34-effect matrix

“Current Moon state” describes the audited baseline, not an acceptance claim. “This phase policy” is the required implementation decision.

| Effect | Current Moon state | Upstream semantic summary | This phase policy | Dependency/notes |
|---|---|---|---|---|
| `EvenHealthChange` | Narrow/partial | Evenly distributes negative healing in configured groups. | Implement faithfully and test group distribution. | Health groups and max-health semantics must be explicit. |
| `HealthChange` | Narrow/partial | Scales typed deltas; positive damages, negative heals; respects bypass-resistance flag. | Implement faithfully. | Fix resistance handling and max-health assumptions. |
| `Vomit` | Partial | Empties stomach, adjusts hunger/thirst, slows, and partially purges bloodstream into a vomit puddle. | Keep only explicitly partial behavior until physiology exists; full behavior deferred. | Requires stomach, hunger/thirst, bloodstream, slowdown, and puddle routing. |
| `Jitter` | Only end-to-end custom status | Duration is `time*scale`, with amplitude/frequency and refresh-vs-accumulate behavior. | Adapt to the status core and client-only presentation. | Fix null player/cast/global timer issues. |
| `Drunk` | No-op | Accumulates `boozePower*scale` duration. | Implement as a status behavior. | Use authoritative status instance, not a competing timer. |
| `ModifyBleed` | Harmful partial | Alters future bleed rate, not damage. | Deferred; disable damage substitution and return tested unsupported. | Requires real blood/bleed state. |
| `Oxygenate` | No-op (and type drift) | Changes respirator saturation. | Deferred; canonical codec round-trip only in milestone 1. | Requires respiration/organ model; correct `type` mapping. |
| `ModifyLungGas` | No-op | Changes an organ gas mixture. | Deferred with controlled unsupported result. | Requires organ/lung gas system; ratios must use correct identifiers. |
| `AdjustAlert` | No-op | Shows or clears a timed HUD alert; scale does not alter time. | Implement state/presentation without notable assets. | Requires alert registry/localization and client boundary. |
| `SatiateHunger` | No-op | Applies `factor*scale`; upstream default is 1.5. | Implement if existing hunger capability is sufficient. | Otherwise explicit unsupported, not a vanilla approximation. |
| `ModifyBloodLevel` | Harmful partial | Regulates blood volume toward max or zero. | Deferred; disable current damage substitution. | Requires blood-volume state. |
| `SatiateThirst` | No-op | Applies `factor*scale`. | Deferred unless a real custom thirst system is added in scope. | Do not map to unrelated vanilla food/water. |
| `PopupMessage` | No-op | Randomly selects a localized message with local/PVS semantics. | Implement with injected `RandomSource`. | Requires localization and server/client delivery semantics. |
| `Emote` | No-op | Invokes a registered emote with chat/force controls. | Implement through registered emote dispatch. | Must not use arbitrary command/reflection. |
| `ModifyStatusEffect` | Codec/data references only | Uses Update/Add/Remove/Set status operations. | Implement with reducer and lifecycle hooks. | Supports the 15 new IDs and resolved references. |
| `AdjustReagent` | No-op | Adds/removes `amount*scale` in the source solution. | Implement with source-solution context. | Must be independent of body target and capacity-safe. |
| `CureZombieInfection` | No-op | Manipulates infection/immunity/death-conversion state. | Deferred with explicit unsupported result. | Requires infection/immunity/death-conversion system. |
| `ArtifactDurabilityRestore` | No-op | Restores a fixed durability amount after the minScale gate. | Deferred. | Requires artifact node system. |
| `ArtifactUnlock` | No-op | Unlocks artifact behavior/node state. | Deferred. | Requires artifact node system. |
| `GenericStatusEffect` | Codec/data references only | Legacy compatibility adapter for known status/component pairs. | Keep decoding, allowlist known mappings, deprecate. | Unknown aliases warn once and do nothing; no reflection. |
| `Flammable` | No-op | Adds fractional fire stacks; default multiplier is 0.05. | Implement if fire-stack capability exists. | Use typed target capability and explicit unsupported otherwise. |
| `Ignite` | No-op | Ignites a flammable target. | Implement through the same fire capability. | Do not invent fire behavior for non-flammable targets. |
| `AdjustTemperature` | No-op | Applies heat energy via heat capacity, not direct Celsius. | Deferred. | Requires temperature/heat-capacity system. |
| `Extinguish` | No-op | Reduces fire stacks; upstream default is `-1.5*scale`. | Implement with shared fire state if available. | Keep scale semantics and target capability checks. |
| `MovementSpeedModifier` | No-op | Timed status with walk/sprint modifiers. | Implement in status core only where semantics match. | Resolve walk-vs-sprint mismatch before claiming support. |
| `CleanBloodstream` | No-op | Removes `cleanseRate*scale` from each non-blood, non-excluded reagent. | Deferred. | Requires route-aware bloodstream and exclusion rules. |
| `MakeSentient` | No-op | Grants sentience through mind/ghost-role control. | Deferred. | Requires mind and ghost-role systems. |
| `Polymorph` | No-op | Replaces entity with control, inventory, and revert handling. | Deferred. | Do not approximate with a cosmetic/vanilla effect. |
| `ResetNarcolepsy` | No-op | Sets next incident to now plus `timerReset*scale`. | Deferred. | Requires narcolepsy incident state. |
| `ModifyKnockdown` | No-op | Uses status operations with optional crawling/drop behavior. | Implement status portion; explicitly degrade only when crawling is unavailable. | Drop/crawling capability must be documented in result/tests. |
| `Electrocute` | No-op | Scales shock damage, not duration; considers insulation/conductivity. | Implement with shock damage/stun and an explicit insulation policy. | Requires conductivity/insulation capability; no duration scaling. |
| `EyeDamage` | No-op | Floors `amount*scale` and recalculates blindness thresholds. | Implement persistent threshold behavior. | Requires eye-damage state and threshold projection. |
| `ReduceRotting` | No-op | Subtracts accumulated rot seconds. | Deferred. | Requires rot state and death/decay ownership. |
| `CauseZombieInfection` | No-op | Manipulates infection/immunity/death-conversion state. | Deferred with explicit unsupported result. | Same infection system as cure; no approximation. |

## Tests and validation

### Required tests

Add test dependencies/configuration only as needed. Do not add compatibility fixtures for old status data.

Pure tests must cover:

- `EffectSystem` gate order, independent probability, minScale, scaling, condition short-circuit, and injected deterministic randomness.
- `StatusEffectReducer` Update/Add/Remove/Set, finite/permanent duration, delay, pending start ordering, and typed payload.
- JSON/NBT/network codec round trips for effect/status data and attachments.
- Resource decode, inheritance, reference, abstract exclusion, canonical re-encoding, and semantic validation.
- Damage and reagent consumption/metabolite math, including resistance, actual removed quantity, capacity, and source routing.

GameTests must cover attachment persistence and sync-relevant transitions; add/activate/expire/remove exactly once; delayed status; transient attribute cleanup and rehydration; player death policy; server-only effect execution; missing prototype; unsupported effect; an unaffected entity tick not creating or syncing empty attachments; and dedicated-server classloading safety.

Use golden resource fixtures at minimum: `oxygen.json`, `ambuzolplus.json`, `fresium.json`, `charcoal.json`, `epinephrine.json`, `artifexium.json`, `haloperidol.json`, and `stimulants.json`.

Validate all JSON syntax plus semantic decoding and re-encoding. A build alone does not validate dynamic datapack semantics.

### Commands from repository root

Run the narrowest useful check first, then the broader checks:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
.\gradlew.bat build
# After GameTests are added:
.\gradlew.bat runGameTestServer
```

Do not run the client or server manually. A useful syntax-only JSON sweep, before semantic resource tests exist, is:

```powershell
python -c "import json, pathlib; files=list(pathlib.Path('src/main/resources').rglob('*.json')); [json.load(p.open(encoding='utf-8')) for p in files]; print(f'parsed {len(files)} JSON files')"
```

## Definition of done

- Every current reagent JSON decodes under one canonical documented schema with no silently ignored behavior-bearing field.
- Inheritance/resolution makes intended parent effects observable; multiple-parent semantics and abstract exclusion are tested.
- Every one of the 34 effect variants has either tested faithful behavior or explicit tested unsupported behavior with its dependency documented. No effect silently applies an empty/no-op handler.
- Common gating and context semantics match the pinned upstream contract.
- All referenced statuses resolve. Lifecycle, timers, and operations work and synchronize without per-tick packet spam.
- Existing supported effects no longer have known harmful semantics, especially blood effects.
- Supported reactive effects are reachable through gameplay exposure and duplicate exposure is controlled.
- Tests and build pass; no manual launch is used. The final report names supported, partial, and deferred effects and remaining risks.

## Risks and decision gates

Ask the user before changing the established provider/bridge architecture or choosing an approximation not covered by this handoff. In particular, stop for a decision if faithful behavior requires a new major subsystem rather than a typed unsupported result.

- **Owner architecture gate:** Apply the owner architecture preference above to any proposed change to an existing pattern; obtain approval before an architectural rewrite, while allowing ordinary localized fixes that preserve the established architecture to proceed.

Track these risks explicitly in implementation and final reporting:

- Dynamic registry reload and reference validation, including cleanup when a status definition is removed.
- Mutable attachment state and explicit sync; avoid packet spam with 20+ multiplayer players.
- Walk-versus-sprint speed mismatch, vanilla effect ownership collisions, and transient attribute cleanup after reload/death.
- Data inheritance complexity, reaction recursion/cycles, catalyst quantities, and canonical schema drift.
- Client/server classloading and accidental client imports in server paths.
- Localization and local/PVS message behavior.
- Attribution and licensing: this project is explicitly non-commercial. Faithful reuse of original SS14 sounds and assets is preferred when it materially preserves the original game's recognizable feel, including material licensed CC-BY-NC. For every copied asset, retain exact per-file provenance: upstream path/source, pinned commit when known, author/attribution, exact license, and whether it was modified. Comply with CC-BY, CC-BY-SA, CC-BY-NC, CC0, and other license terms individually; custom or unclear licenses require specific review. Third-party asset licenses remain separate from the mod code's MIT license; MIT does not relicense those assets. Keep imports scoped to implemented behavior rather than broad bulk copying. This policy supersedes the blanket "Do not add assets/use no notable new assets" guidance; the current AdjustAlert HUD remains asset-free by design.

The asset policy above is not a reason to broaden a task: document copied asset provenance and keep imports focused on implemented behavior.
