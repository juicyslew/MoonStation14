# 2026-09-22 Milestone 5 Electrocute Audit

## Scope and result

`EffectData.Electrocute` is data-only. `ElectrocutionSystem` is the server-side
handler and is registered as a functional/partial effect rather than as a
class-wide explicit unsupported registration.

Admission requires a server-side `LivingEntity` implementing the local
`IStatusEffectTrait`. Nonliving, client-side, and status-incapable targets
return quiet `SKIPPED_UNSUPPORTED` without creating damage or status state.
The fixed `moonstation14:statuseffectstunned` dependency is validated during
resolved reagent/status catalog publication, before runtime dispatch.

## Arithmetic and policy

The handler performs two separate float operations and truncations toward zero:

1. `scaledDamage = truncateTowardZero((float) shockDamage * scale)`
2. `effectiveDamage = truncateTowardZero((float) scaledDamage * siemensCoefficient)`

Scale affects damage only. `electrocuteTime` is converted independently through
the existing nonnegative seconds-to-ticks ceiling conversion. Non-finite,
negative, and out-of-integer-range direct values fail safely.

The local insulation policy supports potentially effective requests only with
`bypassInsulation=true`. The configured Siemens coefficient is used directly;
the handler does not inspect armor, leather, fire immunity, lightning traits,
potions, or generic invulnerability as fake insulation. A positive first-stage
damage and positive coefficient with `bypassInsulation=false` returns
`SKIPPED_UNSUPPORTED` before mutation and emits one policy warning. A zero
first-stage result or zero coefficient is an applied no-op after capability
admission. This compatibility boundary is intentionally more limited than
upstream: bypass suppresses equipment relay upstream but can still encounter
intrinsic insulation, while MoonStation14 has neither intrinsic nor equipment
conductivity state.

## State ownership and deliberate deviations

For coefficient `> 0.5f`, the handler applies
`moonstation14:statuseffectstunned` before one resistible `shock` ledger delta.
Exactly `0.5f` damages without stunning. `refresh=true` uses the status
reducer's `UPDATE`; `refresh=false` uses `ADD`; delay is zero. The reducer owns
maximum/no-shortening and accumulation semantics, and prototype eligibility
remains authoritative. If an absent stunned status is rejected by lifecycle
eligibility, the reduction is treated as unsupported before damage. An
unchanged UPDATE on an already-present longer status is not rejected and still
damages. Zero time skips the status but still damages.

The current stunned prototype is marker-only. The duration state is
authoritative, but physical stun projection is deferred: this implementation
does not claim movement-zero, vanilla potion, crawling, action blocking, or
physical paralysis. It also deliberately omits upstream stutter/jitter,
popup/events, and sound because those are outside this narrow effect boundary.
There is no obsolete duplicate electrocution marker/timer or competing status
owner.

`DamageSystem.applyHealthChange` receives scale `1` and `ignoreResistances=false`;
insulation bypass is not resistance bypass. The existing aggregate Minecraft
mitigation limitation remains: the typed ledger records the resisted aggregate
allocation, not a claim of per-armor-key SS14 fidelity.

## Coverage

Unit coverage exercises two-stage truncation and float precision, damage-only
scaling, the strict coefficient threshold, zero/no-op paths, time-zero damage,
UPDATE/ADD planning, insulation rejection, and invalid direct values.
GameTests cover authoritative shock/stun state, refresh and accumulation,
stimulant-style removal through the same stunned key, mutation-free insulation
rejection, and unsupported nonliving targets.

The effect's codec defaults remain `electrocuteTime=2`, `shockDamage=5`,
`refresh=true`, `bypassInsulation=true`, and `siemensCoefficient=1`.
