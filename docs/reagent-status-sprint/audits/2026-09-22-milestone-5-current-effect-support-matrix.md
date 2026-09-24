# 2026-09-22 Milestone 5 Current Effect Support Matrix

**Audit date:** 2026-09-22  
**Scope:** Current effect registration and dispatch classification only.

## Verdict

Milestone 5 is **not complete**. The dispatcher now distinguishes intentional
unsupported/deferred effects from an accidentally missing registration, but the
matrix below records several effects that still require owning capabilities.
HealthChange and EvenHealthChange passed the corrected typed-health event
bridge validation. Minecraft still supplies mitigation for the aggregate
vanilla damage event; it does not expose per-typed-key armor/resistance
allocation, so typed allocation preserves request proportions rather than
claiming per-key mitigation fidelity.

## Current matrix

Each of the 34 `EffectData` record variants appears exactly once.

| Effect | Status | Blocker, policy, or current boundary |
|---|---|---|
| `EvenHealthChange` | supported | Uses aggregate healing with deterministic canonical residue handling. |
| `HealthChange` | supported | Uses final Post damage, separate resistible/bypass vanilla sources, absorption, cooldown bypass, baseline import, and vanilla death handling. |
| `Vomit` | partial | Eligible living stomach targets empty the full stomach solution and spill actual contents; digestion uses its live transaction. Initialized custom hunger/thirst owners receive -40 even for an empty stomach. Status-capable living targets receive a 0.5 movement slowdown for 267 ticks (80/6 seconds), refreshed via non-shortening UPDATE. Unsupported/dead targets leave state untouched. Bloodstream flush, forensics, and atomic spill egress remain deferred; failed placement loses emptied contents. |
| `Jitter` | supported | Uses the authoritative status attachment and bounded jitter payload. LOCAL compatibility cadence floor extends finite immediate metabolism UPDATEs to 21 ticks only with a confirmed same-reagent live body/stomach reservoir; this is distinct from pinned upstream scaled-time behavior. |
| `Drunk` | supported | Uses the authoritative status attachment. |
| `ModifyBleed` | explicit unsupported/deferred | No authoritative blood/bleed state; damage substitution is disabled. |
| `Oxygenate` | explicit unsupported/deferred | No authoritative respiration or organ saturation state. |
| `ModifyLungGas` | explicit unsupported/deferred | No authoritative lung gas mixture state. |
| `AdjustAlert` | supported | Character-owned alert state with absolute tick deadlines, category replacement and compact local-player HUD; prototype metadata/localization required. |
| `SatiateHunger` | supported | Character-owned custom scalar (0..200, default 150); no vanilla FoodData projection or passive physiology. |
| `ModifyBloodLevel` | explicit unsupported/deferred | No authoritative blood-volume state; damage substitution is disabled. |
| `SatiateThirst` | compatibility-limited | Applies `factor * scale` to an already initialized, explicitly thirst-enrolled target; projects thirst-only alerts and a 0.75 walk/sprint modifier. No stomach/ingestion or vanilla FoodData behavior. |
| `PopupMessage` | supported | Accepted decision: registered message keys and the configured local/PVS boundary are handled. |
| `Emote` | supported | Closed lowercase allowlist of nine IDs; combined SS14 human sound collections, nearby voice-source sound and optional tracker-broadcast localized chat. Living entities are admitted; no vocal/silicon/species capability model exists. `force` cannot bypass living-target admission and currently has no effect; `showinguidebook` is metadata only. See [Emote audit](2026-09-23-emote.md). |
| `ModifyStatusEffect` | supported | Uses typed status operations and the status reducer/lifecycle. |
| `AdjustReagent` | supported | Uses explicitly routed source-solution transaction state. |
| `CureZombieInfection` | explicit unsupported/deferred | Infection, immunity, and death-conversion state is unavailable. |
| `ArtifactDurabilityRestore` | explicit unsupported/deferred | Artifact node and durability systems are unavailable. |
| `ArtifactUnlock` | explicit unsupported/deferred | Artifact node and unlock systems are unavailable. |
| `GenericStatusEffect` | compatibility-limited | Compatibility adapter is restricted to an explicit allowlist; unknown aliases do not apply. |
| `Flammable` | supported | For non-fire-immune `LivingEntity` character targets, mutates authoritative fire stacks and supports negative-stack wetness recovery. Other targets are quiet unsupported. |
| `Ignite` | partial/state-supported | For non-fire-immune `LivingEntity` character targets, sets authoritative `ignited` state only when stacks are positive; burning simulation is deferred. Other targets are quiet unsupported. |
| `AdjustTemperature` | explicit unsupported/deferred | No authoritative temperature and heat-capacity state. |
| `Extinguish` | supported | For non-fire-immune `LivingEntity` character targets, clears authoritative ignition and applies stack/wetness semantics. Other targets are quiet unsupported. |
| `MovementSpeedModifier` | compatibility-limited | Accepted decision: only equal walk/sprint pairs are applied; unequal pairs return unsupported. Finite immediate metabolism UPDATE uses the documented LOCAL ongoing-reservoir 21-tick floor. |
| `CleanBloodstream` | explicit unsupported/deferred | Route-aware bloodstream state and reagent exclusion rules are unavailable. |
| `MakeSentient` | explicit unsupported/deferred | Mind and ghost-role control systems are unavailable. |
| `Polymorph` | explicit unsupported/deferred | Entity replacement, control, inventory, and revert systems are unavailable. |
| `ResetNarcolepsy` | explicit unsupported/deferred | Authoritative narcolepsy incident state is unavailable. |
| `ModifyKnockdown` | compatibility-limited/partial | Uses the separate authoritative `moonstation14:knockdown` status and local delayed/permanent reducer semantics. Physical prone/crawling, collision, action blocking, paralysis, and held-item dropping are not projected. Nonzero UPDATE/ADD/SET requests with `crawling` or `drop` are rejected without mutation; REMOVE ignores those flags and still reduces/removes knockdown. |
| `Electrocute` | compatibility-limited/partial | Server-side living status-capable targets use the configured coefficient, two-stage damage truncation, resistible typed `shock` damage, and the authoritative `statuseffectstunned` marker. `bypassInsulation=false` is explicitly unsupported because no local conductivity model exists. Physical stun projection and SS14 presentation remain deferred. |
| `EyeDamage` | compatibility-limited/supported | Persists character-owned integer damage in `[0,9]`; threshold `9` derives blindness presentation as client fog. Nonliving targets are unsupported. Partial eye injury presentation and full SS14 vision/anatomy behavior remain deferred. |
| `ReduceRotting` | explicit unsupported/deferred | Authoritative rot and death-decay state is unavailable. |
| `CauseZombieInfection` | explicit unsupported/deferred | Infection, immunity, and death-conversion state is unavailable. |

The twenty functional/partial/supported registrations are `EvenHealthChange`,
`HealthChange`, `Vomit`, `Jitter`, `MovementSpeedModifier`, `Drunk`,
`ModifyStatusEffect`, `GenericStatusEffect`, `AdjustReagent`, and
`PopupMessage`, `Flammable`, `Ignite`, `Extinguish`, `ModifyKnockdown`,
`EyeDamage`, `Electrocute`, `AdjustAlert`, `SatiateHunger`, `Emote`, and `SatiateThirst`. The remaining 14 are explicit unsupported registrations.
`Vomit` is deliberately not classified as unsupported because its existing
behavior is explicitly partial.

`ModifyKnockdown` is deliberately partial rather than a claim of physical
knockdown support. Its timer/status state is independent from
`statuseffectstunned`, so paired stimulant removals affect their own status only.
The handler intentionally uses the local status reducer for delayed, permanent,
and operation semantics instead of reproducing upstream component-timer quirks
around ignored delay or null duration. Crawling and dropping remain unavailable
physical capabilities and are rejected for non-removal operations; a scaled-zero
operation remains an applied no-op for a supported status target, while
capability admission still returns unsupported for non-status targets.

The fire handlers intentionally do not apply vanilla fire ticks or burn damage.
They also do not provide fire visuals, fire spread, equipment/clothing ignition,
or the final SS14 burning simulation. `Flammable` and `Extinguish` are supported
only for the authoritative stack, ignition, and wetness state defined by this
milestone; `Ignite` is partial because the actual burning behavior remains
deferred.

This is a narrow Minecraft compatibility boundary, not unrestricted SS14
`FlammableComponent` support: only non-fire-immune `LivingEntity` character
targets currently expose this capability. Nonliving targets and Minecraft
fire-immune living targets return `SKIPPED_UNSUPPORTED` without materializing
state.

## Verification and risks

- `EffectDispatcherSupportMatrixTest` reflects the 34 record variants, asserts
  the exact 20/14 class sets, checks nonblank reasons, rejects duplicate and
  cross-category registrations, and dispatches every unsupported class with a
  null context. Explicit and unclassified paths each warn once per exact type.
- `EffectKernelTest` retains common gate ordering, probability, scale, and
  existing dispatcher behavior coverage.
- The main remaining risk is semantic: supported/partial handlers still depend
  on the health, client presentation, fire, and status capabilities noted
  above. This document is a current support audit, not a completion claim.
- Vanilla death-protection/totem ledger reconciliation remains intentionally
  limited; a totem can save an entity while the typed ledger still records the
  lethal Post damage. Full respawn/death architecture remains out of scope.

## Player death status policy

Custom status state is **death-cleared**. The registered NeoForge
`PlayerEvent.Clone` hook applies this policy only when `isWasDeath()` is true;
it clears the respawned player’s finite, pending, and permanent statuses,
including dangling definitions. Defined statuses receive `onRemoved`, while
missing definitions receive `onInvalidated`. Lifecycle projection cleanup is
completed before one attachment removal/synchronization, followed by one
derived-activity reconciliation. Reagent, damage, and other unrelated
attachments are not cleared. Ghost and inhabit-existing-entity respawn
behavior remains out of scope.

Alert state is independently synchronized and persisted. Player death/clone
behavior is currently the NeoForge attachment default (no copy-on-death policy
is declared); clearing or copying alerts across death is intentionally not
claimed. Category replacement is deterministic for alerts with matching
prototype categories. Existing stored alert references whose prototype is
missing cause the attempted adjustment to fail safely without mutation.

The deterministic lifecycle tests cover all status state shapes, callback
ordering/counts, dangling definitions, and empty no-op clearing. The
GameTest uses NeoForge’s server-side mock `ServerPlayer` and the narrow policy
seam; the harness does not drive the full vanilla death/clone transport flow.
It seeds active movement, pending, and permanent statuses and verifies that
the status attachment, transient movement modifier, and status activity are
absent after the policy runs.
