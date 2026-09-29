# Blood Volume and Bleeding Sprint — Implementation Handoff

This is a bounded engineering handoff for adding prototype-owned scalar blood volume and bleeding state to MoonStation14, then enabling the existing `ModifyBleed` and `ModifyBloodLevel` reagent effects. It is not authorization to complete the full SS14 bloodstream/forensics feature set.

## Hard requirements

- Every per-character/entity eligibility decision and every numeric/gameplay tuning value is controlled by JSON prototypes. There are no hardcoded player, villager, human, or other species enrollments; do not branch on vanilla entity classes/types to opt entities in.
- Reuse the existing character policy identity and `host_entity_types` mapping (`CharacterData` / `ModCharacters`) or a separately documented explicit character-identity mapping. The system operates on any `LivingEntity` whose resolved character policy has blood configuration. Enrollment is data, not code.
- No Java-only fallback/default for blood volume, bleed rate, intervals, damage multipliers, thresholds, damage/healing, or caps. Missing blood policy means inert. A configured entity obtains all initial values from its resolved JSON prototype; live saved state is not silently initialized to a built-in value.
- Keep implementation to blood schema, persisted state/lifecycle, damage-driven bleeding and blood tick, reagent effects, and their tests. Do not edit unrelated sprint documents or systems, perform general reagent cleanup, or add forensics/puddles, oxygenation, crits, visual/alert presentation, or connected gameplay acceptance.
- Do not launch or manually test the game. Automated checks only; the owner performs any manual acceptance separately.

## Local baseline and reference

- Local root: `src/main/java/com/juicyslew/moonstation14` and `src/main/resources/data/moonstation14/moonstation14`.
- `CharacterData` has optional `thermal`, `movement`, and `host_entity_types`; `CharacterSchemaAudit` rejects unknown fields and audits semantic constraints. Extend that established strict codec/audit pattern for optional blood policy.
- `ModCharacters` resolves character policy by prototype and host mapping. Preserve that resolution and collision validation; do not add hardcoded host classification.
- Persistent entity values use the existing component/attachment representation and `MS14Provider` / `MS14Bridges` / `SystemLink` conventions. Preserve explicit update/sync semantics.
- `DamageSystem.observePost` is the post-mitigation typed damage boundary. A completed damage sequence is recorded once; don't infer bleeding from requested/pre-mitigation damage or observe both the transaction and its projection.
- `EffectHandlers` presently registers `ModifyBleed` and `ModifyBloodLevel` as unsupported specifically because authoritative state is absent. Remove that status only when the complete behavior and tests exist.
- Upstream reference checkout is pinned at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. `SharedBloodstreamSystem` ticks on a component-configured interval (upstream baseline is 3 seconds), refreshes blood, ticks/decays bleed, and applies bloodloss damage or recovery based on blood fraction. `OnDamageChanged` uses only positive post-change typed damage and a prototype modifier set. Upstream `ModifyBleed` changes bleed amount by `amount * scale`; `ModifyBloodLevel` regulates toward a bounded target. These semantics are reference, not a requirement to copy ECS, contained solutions, damage modifiers, statuses, or puddles.
- This local implementation is intentionally a scalar approximation: it does not represent a reagent solution, blood composition, forensic DNA, leaked blood, or blood puddles. State the deviation; never call it a faithful full bloodstream port.

## Proposed optional JSON policy (version 1)

Add optional root field `blood` to a character prototype. Unknown nested or root fields remain errors. Suggested canonical names and meanings (the implementation may refine names before schema lands, but document any divergence and keep one canonical form):

| Field | Meaning / constraints |
|---|---|
| `initial_volume` | Volume on the first eligible initialization only; finite and `> 0`. |
| `max_volume` | Absolute scalar cap; finite and `>= initial_volume`. |
| `update_interval_seconds` | Positive finite interval for refresh, bleed tick/decay, and bloodloss/recovery evaluation. Convert deterministically to server ticks; reject a positive duration that rounds to zero or overflows. |
| `bleed_decay_per_update` | Nonnegative amount removed from active bleed rate per update. |
| `max_bleed_rate` | Nonnegative cap for active bleed rate. |
| `damage_bleed_multipliers` | Required canonical map from supported local typed damage key to finite signed multiplier (absolute value at most 1,000,000); negative heat can reduce bleeding. An explicit empty object is valid. Unknown keys are rejected; Gson collapses duplicate raw JSON object members before this codec can detect them. |
| `blood_refresh_per_update` | Nonnegative volume restored per update, limited by `max_volume`. |
| `bloodloss_threshold_fraction` | Finite fraction in `[0, 1]`; below it use the low-blood damage policy, at/above it use recovery policy. |
| `bloodloss_damage_per_update` | Nonnegative typed damage delta (prefer an explicit damage-key map so the type is data-owned) before cap/scaling by blood fraction. |
| `bloodloss_heal_per_update` | Nonnegative typed healing delta/map when not below threshold. Apply only up to configured recovery amount and existing damage, with no vanilla natural-regeneration dependency. |
| `bloodloss_damage_cap_per_update` | Nonnegative maximum total bloodloss damage applied in one update. |
| `bloodloss_ignore_resistances` | Required boolean in a present `blood` object: `false` routes bloodloss through regular resistible typed damage; `true` selects the existing bypass damage source. Healing is a negative-only typed-ledger commit and does not consult resistances. |

Define exact formulae in the implementation and test boundary values. Candidate semantics: fraction is `clamp(current_volume / max_volume, 0, 1)`; refresh adds at most configured refresh and stops at max; bleed removes volume proportional to active rate and elapsed configured interval, then clamps active bleed at zero after configured decay; bloodloss contribution below threshold is configured damage scaled by a documented function of deficit/fraction, then capped; recovery is configured healing scaled by configured policy and cannot over-heal. The multiplier map turns positive post-mitigation typed damage into bleed-rate increment exactly once. `ModifyBleed` is `current_rate + effect.amount * effect_scale`, clamped to `[0, max_bleed_rate]`; `ModifyBloodLevel` adjusts volume by `effect.amount * effect_scale`, clamped to `[0,max_volume]`, with negative meaning removal. Avoid embedding any constants in these formulas except mathematical bounds and unit conversion.

All maps accept only local `DamageKeys` canonical values (`blunt`, `piercing`, `slash`, `heat`, `cold`, `shock`, `asphyxiation`, `bloodloss`, `caustic`, `poison`, `radiation`, `cellular`, `holy`) or a narrower explicitly documented subset. Validate keys against `DamageKeys`; do not invent aliases or ignore unsupported keys. Do not reuse SS14's modifier-set IDs, which don't exist locally.

The schema audit rejects unknown fields, missing required fields within a present `blood` object, non-numbers, NaN/infinity, negative values where forbidden, invalid ranges/order, noncanonical damage keys, and inconsistent values (including initial > max). Gson collapses repeated raw JSON object keys before audit, so this boundary cannot reject duplicates in raw map input. Error messages should identify the JSON path; prototype loading supplies character context. Missing entire `blood` object is valid and means no blood simulation, no initialization, and no blood reagent effect mutation.

## Ordered implementation stages and gates

1. **Prototype schema and host eligibility.** Add optional typed blood config and strict audit. Keep the existing human character configured and add a second distinct configured host fixture (pig is an existing separate mapped host suitable for proving the policy is not player-only; configure via its character JSON, not a Java check). Verify two mapped entity types can use different values and an unconfigured LivingEntity stays inert. Validate absent policy, missing required field, unknown field/key, wrong type, non-finite/out-of-range values, duplicate mapping, and conflicting host mappings. Existing conflicting `host_entity_types` assignments must fail loading rather than select one nondeterministically. No host enrollment constants.
2. **Persisted state and lifecycle.** Store scalar current volume and active bleed rate as persisted, synchronized entity state behind the established provider/bridge style. Initialize a missing state exactly once from the resolved blood prototype, without materializing state for unconfigured entities or requiring a manual setup command. Reload must retain saved scalar values; clone/copy/death policy must be explicit, tested, and not silently reset to prototype initial values. Reconcile saved state against a changed prototype by clamping volume and bleed to the new caps only; preserve remaining state, and do not refill/reinitialize. If policy disappears or becomes invalid on reload, fail closed: disable blood behavior, preserve stored values unchanged, and report validation failure for invalid data. Never fall back to hidden defaults.
3. **Typed damage, blood update, and bloodloss.** Connect exactly once to completed post-mitigation typed positive damage. Resolve the entity's policy via character identity/host mapping, apply configured signed per-type multipliers, and clamp accumulated bleed. Negative typed healing must not create bleeding. Each configured interval, apply configured blood refresh, bleed volume loss and configured bleed decay, then threshold bloodloss typed damage or configured recovery. Use the local damage ledger and canonical keys; bloodloss damage is always excluded from bleed accumulation, even if a policy lists a `bloodloss` multiplier. Ensure damage event ordering does not double count bloodloss writes. Server authoritative only.
4. **Reagent effects.** Replace unsupported handlers only for eligible configured LivingEntities. `ModifyBleed` changes bleed-rate state (not bloodloss damage); `ModifyBloodLevel` changes scalar volume (not damage). Apply reagent scale once. Unsupported/missing-policy target returns controlled `SKIPPED_UNSUPPORTED` without creating state. Cap/clamp using the target's character prototype values. Preserve explicit effect result behavior.
5. **Automated validation and closeout.** Add pure reducer/schema/codec tests and entity/system lifecycle/damage/effect tests: arbitrary host resolution; missing policy inert; two differing prototypes; no eager empty state; reload/clone; config reload clamp/preserve; invalid/missing/removed policy fail closed; post-mitigation damage and rejection/cancellation exactly once; all type multipliers; positive-only damage; interval and boundary math; caps/clamps; bloodloss typed damage/recovery; reagent scaling exactly once; client no-mutation; and save/sync behavior. Validate prototype resource decoding/references and duplicate host mappings. Run targeted compilation/tests and required checks in the implementation session. No manual game launch.

## Out of scope / explicit risks

- Full SS14 blood-solution container, reagent composition/transfer, solution metabolism, DNA/forensics, spilled blood, puddles, wound visuals/alerts/sounds, blood pressure, organ/bone marrow, critical-hit rolls, oxygenation/respiration, and connected gameplay acceptance are deferred.
- Upstream's 3-second tick is a reference cadence only. In this mod the cadence and every effect magnitude are prototype values; do not bake 3 seconds or other gameplay tuning into Java.
- Scalar volume is not SS14's multi-reagent usable-blood fraction. Any meaning/units must be local and consistent. Avoid claiming parity.
- Dynamic prototype reload and persisted entity state can disagree; implement reconciliation as above and test it. Invalid new configuration must not destroy saved state.
- A character identity/mapping missing at runtime, conflicting host mapping, unsupported host, client-side call, or missing policy must not guess a humanoid default.
- No assets/manual testing are included. Report unresolved integration and acceptance risks rather than implying they passed.

## Completion report

Report exact changed files, automated commands/results, the host fixtures used, schema/reconciliation behavior, remaining risks/deviations, and confirm no manual game was run. Do not commit, reset, or discard unrelated work. Do not touch other sprint docs or unrelated systems.
