# Blood and Bleeding — Local Architecture Proposal

This document describes the narrow scalar design for the blood/bleeding sprint. It intentionally does not reproduce all of SS14's bloodstream subsystem.

## Ownership and eligibility

`CharacterData` remains the sole owner of per-character tuning and eligibility. Add `Optional<BloodData> blood`, absent by default, to its immutable decoded policy. `CharacterSchemaAudit` owns strict structure and semantic validation, including the nested blood shape and the existing uniqueness/conflict checks for `host_entity_types`. `ModCharacters` remains the resolution/index boundary. Systems receive the resolved policy, rather than asking whether an entity is a player, villager, pig, human, or a Java class.

The host mapping is the existing explicit enrollment mechanism: for example, `minecraft:player` and `minecraft:villager` can map to the human JSON policy while `minecraft:pig` maps to the distinct pig policy. Those examples are prototype entries, not code rules. Alternatively an explicitly persisted character identity may select a policy where the host index is not suitable. An entity with no unambiguous identity/policy, or with a policy whose `blood` is absent, is ineligible and inert. Conflicting prototypes claiming one host fail validation; do not use load order as policy.

Every numeric value that changes behavior comes from a validated blood prototype: initial/max volume, interval, refresh, decay, max bleed, typed damage-to-bleed multipliers, threshold, bloodloss damage/recovery values, and bloodloss cap. The only code constants permitted are structural facts (e.g. seconds-to-ticks conversion, scalar arithmetic bounds, canonical damage-key set) and not gameplay fallback/tuning. A missing field inside a present config is an error, not a default. A missing config object means no behavior.

## Data model

Prototype data is immutable and versioned by the normal dynamic character prototype registry. The initial v1 policy candidate is specified in `Instructions.md`; use one canonical JSON encoding and keep strict unknown-field rejection. Suggested typed representation:

```text
CharacterData(..., Optional<BloodData> blood)
BloodData(
  initialVolume, maxVolume, updateIntervalSeconds,
  bleedDecayPerUpdate, maxBleedRate, damageBleedMultipliers,
  bloodRefreshPerUpdate, bloodlossThresholdFraction,
  bloodlossDamagePerUpdate, bloodlossHealPerUpdate,
  bloodlossDamageCapPerUpdate, bloodlossIgnoreResistances)
```

`bloodloss_ignore_resistances` is a required boolean whenever `blood` is present; human and pig explicitly choose `false` (regular resistible damage). `true` opts into the existing bypass damage source. Healing is negative-only and commits directly to the typed ledger, bounded by extant matching damage, so the resistance decision does not change healing. Damage maps use canonical strings already accepted by `DamageKeys`. `damage_bleed_multipliers` accepts finite signed coefficients bounded by absolute value `1,000,000`; a negative coefficient reduces accumulated bleeding (the human example uses heat as a cauterizing damage-to-bleed coefficient). Bloodloss damage/healing maps remain finite, nonnegative, and bounded at `1,000,000`. The three typed fields `damage_bleed_multipliers`, `bloodloss_damage_per_update`, and `bloodloss_heal_per_update` are required JSON objects; empty maps are deliberate no-op policies, not defaults. The healing map values mean positive heal amounts and the system supplies negative deltas to the existing damage ledger. Scalar volume/rate/amount fields are finite and bounded at `1,000,000`; initial/max volume and interval must be positive, max >= initial, threshold in `[0,1]`, and other scalar amounts nonnegative. Interval seconds convert using the Minecraft server's structural 20 ticks/second and must round to `[1, Integer.MAX_VALUE]` ticks. Gson's parsed JSON object representation collapses repeated object members, so duplicate raw JSON map keys cannot currently be distinguished at this codec boundary; canonical/unknown keys and all parsed entry types/values are validated.

Persist only live mutable state in an entity attachment/component, not a copied prototype tuning snapshot:

```text
BloodState(currentVolume, bleedRate, nextUpdateTickOrRemainder)
```

Values must be finite; volume is bounded by `[0, resolved maxVolume]`, bleed rate by `[0, resolved maxBleedRate]`; cadence bookkeeping is deterministic and persisted only if needed to avoid reload cadence changes. Do not persist prototype-derived initial values as if they were current state. Store state with the existing attachment/component codec and bridge/provider patterns; mutations must be server-authoritative and explicitly synchronized/persisted through `MS14Provider`/`MS14Bridges` conventions. Avoid default attachment construction that writes data merely because an ineligible entity was queried.

## State transitions

### First eligibility / initialization

On a server operation that first admits a blood-configured entity, create current volume = the resolved prototype's `initial_volume` and bleed rate = zero. Initialization is lazy/system-driven, not a command or manual entity setup step, and only occurs once when state is absent and policy is valid. An unconfigured or unresolved entity gets no attachment and no effects. If a persisted state exists, never replace it with initial values on join/reload.

### Prototype reload and state preservation

Resolve current policy on each lifecycle boundary (or use a safe cache invalidated on registry reload). If a valid policy changes, preserve current volume and bleed rate while clamping only to the new configured maxima; do not refill, reset bleed, or rescale saved state implicitly. If the policy is removed, stop ticking and stop accepting blood effects but retain the saved state unchanged so re-adding a policy can resume under explicit reconciliation. If the policy is invalid, registry validation must fail closed; do not run behavior with cached or hardcoded values and do not delete/overwrite saved values. Log/report the validation error at the prototype boundary. Define clone/copy/death behavior explicitly and test it; no hidden reinitialization during cloning.

Stage 2 lifecycle semantics: an existing blood component is copied from the original player to a player clone for both death and non-death clones; death does not mint a fresh state. Clone handling preserves the saved volume and bleed rate, and normal server join reconciliation applies any valid current cap changes. A missing source state remains missing until that join boundary resolves a valid policy. Entities whose bound identity no longer agrees with their host mapping fail closed.

### Post-mitigation typed damage

Integrate at `DamageSystem.observePost` (or a clearly ordered callback receiving its committed typed delta) after mitigation. Use only the positive typed damage actually committed. For each configured non-bloodloss key, `bleed increment = committed positive damage * configured signed multiplier`; sum positive and negative contributions and apply the sum once, clamping the resulting rate to `[0,max_bleed_rate]`. A canceled, rejected, zero, or healing-only transaction adds no bleed. Do not separately react to both the request and its completion, and do not infer from vanilla health delta after ledger projection. If integrating through a second observer, provide transaction identity/deduplication so it cannot process one damage sequence twice.

Damage keys are constrained by `DamageKeys`; they are local typed damage, not a statement that the underlying Minecraft `DamageSource` can faithfully distinguish all types. External sources continue using the existing classifier. Local bloodloss damage is always excluded from the bleed-rate accumulator, including when a policy contains a `bloodloss` multiplier, so system-generated damage cannot feedback into bleeding.

### Periodic update

Use per-entity policy `update_interval_seconds`; convert with the structural server rate of 20 ticks/second using `Math.round`, rejecting results outside `[1, Integer.MAX_VALUE]`. A configured entity's update is due when `floorMod(gameTime + entityId, intervalTicks) == 0`, which staggers entities within that policy's cadence without a global scan or a fixed host-specific scheduler. Runtime host enrollment and state reconciliation retry at most once per 20 server ticks while the published catalog is unchanged. Each entity tick compares the immutable catalog snapshot by identity; a new publication immediately invalidates the cached policy (including cached absence) and re-resolves/reconciles on that tick, failing closed if the policy was removed. The immutable resolved policy is cached between refreshes so configured sub-second update intervals are still honored without per-entity per-tick registry lookup. Refresh retries mapping-only enrollment only when no identity attachment exists, covering join-before-policy/identity publication without creating state for unmapped/ineligible hosts. Already-bound identities, including dangling ones, are never re-enrolled; they are re-resolved against the current catalog on each refresh and can resume when their policy is published. Missed due ticks are not replayed after unload; one update is run at the next due tick, so there are no catch-up loops. Do not reuse a hardcoded 3-second Java scheduler.

1. Resolve eligible policy and current state; skip client and ineligible entities.
2. For alive entities, refresh scalar volume by configured amount up to `max_volume` (no implicit baseline regeneration). Dead entities retain their saved state but are neither initialized nor ticked and receive no health damage or recovery; clone state follows the existing explicit stage-2 copy behavior.
3. Apply volume loss based on the pre-decay bleed rate and elapsed configured interval, bounded at zero; reduce bleed by configured decay, bounded at zero.
4. Compute `fraction = clamp(currentVolume / maxVolume, 0, 1)` and compare against the configured threshold. Below threshold, apply the configured bloodloss typed damage scaled by the documented deficit rule, with total added amount capped by `bloodloss_damage_cap_per_update`. At or above threshold, apply configured recovery through the damage ledger, bounded by configured healing and extant matching damage.
5. Persist/sync only when state changed. Avoid tick packet spam and avoid recursive bloodloss damage being mistaken for new damage-driven bleeding.

Scalar volume units are local abstract volume units. `max_bleed_rate` and `damage_bleed_multipliers` increments are volume lost per configured update (not per second); `bleed_decay_per_update` removes rate units per update. Refresh, bloodloss map values, and cap are typed damage-ledger units per update. Blood fraction is `clamp(current_volume / max_volume, 0, 1)`. Below threshold `t`, scale each configured bloodloss damage amount by `(t - fraction) / t`; this branch is unreachable when `t == 0`. If the sum after scaling exceeds the configured cap, multiply each typed amount by `cap / sum`, preserving proportions. At/above threshold apply the configured recovery map amounts unscaled, subject to extant matching damage in the ledger. These formulae use configured values plus only mathematical bounds; there is no gameplay denominator constant such as upstream's `0.1`.

### Explicit effect behavior

- `ModifyBleed`: with valid resolved blood policy and existing persisted blood state, adjust `bleedRate` by `effect.amount * effect scale`, then clamp to `[0,max_bleed_rate]`; negative reduces/clears rate. It never directly edits damage ledger.
- `ModifyBloodLevel`: with valid resolved blood policy and existing persisted blood state, adjust `currentVolume` by `effect.amount * effect scale`, then clamp to `[0,max_volume]`; negative removes scalar volume. It never converts volume changes into bloodloss damage immediately unless the configured periodic rule later applies.
- Effect scale is applied exactly once. No blood config / unsupported entity / absent persisted state returns `SKIPPED_UNSUPPORTED`, and must not create or initialize state from an effect. Invalid/non-finite amount, scale, or scaled arithmetic returns `FAILED` without partial mutation. An eligible persisted state whose adjusted value is already at the relevant clamp boundary is a successful `APPLIED` no-op.
- Existing reagent prototypes contain these effects; enabling them will make their authored amounts observable. Audit effect fixtures/test representative positive and negative amounts, but do not rewrite unrelated reagent data as part of the architecture change.

## Local SS14 adaptation boundary

Pinned upstream `SharedBloodstreamSystem` (c9df5ef) owns a blood solution, its reference composition and maximum-volume modifier, solution transfer/refresh/regulation, bleed-out into a temporary solution and puddle, blood-loss damage and recovery, metabolism/reaction exclusions, forensics, alerts, and health examination. Its configured `UpdateInterval` is 3 seconds by default and damage bleeding uses positive `DamageChangedEvent` data modified by a prototype modifier set. The upstream effect handlers delegate `ModifyBleed` to `TryModifyBleedAmount(amount * scale)` and `ModifyBloodLevel` to `TryModifyBloodLevel(amount * scale)`.

The local version owns a scalar persisted volume/rate and typed damage ledger. It has no SS14 solution containers or modifier-set prototypes. The human prototype adapts upstream's 300-unit starting level, 3-second update, 1-unit refresh, 10-unit maximum bleed, 0.33 decay, 0.9 low-volume threshold, 0.5 bloodloss damage and 1-unit recovery. Local `max_volume` is deliberately 300 rather than the upstream contained-solution 600 cap; the local bloodloss cap is 0.5 to constrain the scalar model's damage scale. Its typed coefficients follow the upstream `BloodlossHuman` damage-to-bleed intent, with negative heat representing cauterization. These are local scalar-policy adaptations, not direct solution-volume parity. The result must be described as scalar blood-volume/bleeding gameplay, not equivalent solution physiology. Defer blood composition/transfusions/metabolism, forensics/DNA, temporary leaked solution/puddles, crit rolls, sounds/popups/alerts/visuals, oxygenation, organs, and upstream connected acceptance. No assets are in scope.

## Failure policy and validation surface

Fail closed at both decode and runtime: malformed prototype, unknown key/field, missing required nested field, duplicate/conflicting mapping, missing policy, unresolved identity, non-finite math, client call, or unsupported target must not cause a guessed default. Preserve existing saved state on removed/invalid policy; valid cap changes clamp without resetting. Tests must exercise strict JSON decode and semantic audit, canonical damage keys, host conflict rejection, two distinct host policies, lifecycle/reload/clone, persisted state, server-only mutation, event deduplication/post-mitigation semantics, all arithmetic boundaries, bloodloss damage/recovery, effect results and single scale application, and no empty state materialization.

## Relevant local seams

- `component/codec/json/CharacterData.java` and `CharacterSchemaAudit.java`: optional strict typed policy.
- `ms14/character` and `ModCharacters`: policy lookup, identity/host enrollment, collision rejection.
- `component/codec/attachment`, `component/codec/component`, `ModDataAttachments`, `ModDataComponents`: persisted live state; inspect current codec and sync patterns before selecting attachment flags.
- `MS14Provider`, `MS14Bridges`, `SystemLink`: state mutation/persistence/sync boundary.
- `ms14/damage/DamageSystem.java` and `DamageHooks.java`: post-mitigation typed ledger integration and typed bloodloss/recovery.
- `ms14/effect/EffectHandlers.java`, `EffectData.java`, `EffectSystem.java`: replace unsupported handlers only after state APIs are real.
- Existing character resources: `character/human.json` and `character/pig.json` are distinct host mappings available for fixture examples; their numeric policies must be explicit if blood is enabled.

Tests and compilation are required for implementation, but the game must not be run manually. The owner performs connected acceptance. Do not modify unrelated sprint documentation or systems.

## Integration-test coverage and caveats

`BloodGameTests` uses the mapped Pig prototype as the configured non-player host and an unmapped vanilla Zombie as the inert target. It exercises real character enrollment/policy resolution, first initialization, typed `DamageSystem` commits, the regular blood update entry point, and both `EffectSystem` handlers. The persistence assertion encodes/decodes the actual attachment through its NBT codec and checks the exact `current_volume` / `bleed_rate` payload; this verifies the attachment serialization contract, not a complete world-save/restart or dynamic datapack-reload cycle. The typed slash case proves one committed typed transaction yields one configured bleed increment; it does not claim Minecraft's generic vanilla `DamageSource` classifier can distinguish every local damage key. Automated GameTestServer coverage is defined by the Gradle `gameTestServer` run, and must not be replaced by manual game startup.
