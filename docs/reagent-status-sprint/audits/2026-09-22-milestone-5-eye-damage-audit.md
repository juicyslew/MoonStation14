# 2026-09-22 Milestone 5 Eye-Damage Audit

## Decision

`EyeDamage` is a compatibility-limited supported effect. A server-side
`LivingEntity` owns one immutable, validated integer attachment in `[0,9]`.
Absent state is zero; the canonical mutation path removes zero and the
attachment serializer omits empty state. A raw attachment API can still be
directly given an empty value. Blindness is derived solely from `damage >= 9`;
no duplicate blind flag is stored.

The effect uses the admitted common scale and `floor(amount * scale)`,
including the deliberate asymmetry `1 * .5 -> 0`, `-1 * .5 -> -1`, and
`-7 * .5 -> -4`. Runtime totals use widened, checked arithmetic before
clamping. Invalid non-finite or out-of-range intermediate arithmetic fails.
Zero delta is an applied no-op, including when the attachment is absent.

## Presentation and ownership boundary

The client presentation reads the attachment on the current camera entity
(with synchronization configured, but wire transport not tested here). At the
threshold it tightens existing viewport fog to an approximate vanilla
blindness distance. It never installs or removes
`MobEffects.BLINDNESS` or `DARKNESS`: vanilla effect ownership would compete
with other sources and would make this character-owned state non-authoritative.
The fog helper only makes an existing environment more restrictive. No
rendered-pixel test is claimed.

The attachment intentionally does not opt into `copyOnDeath`. This is expected
to leave a newly cloned player body without eye damage, but clone execution is
not tested here. Same-entity in-memory transitions are covered; save/reload,
wire transport, and death-clone execution are not claimed. The temporary
status death shim was not changed.

## Deliberate upstream deviations and limitations

This follows the pinned SS14 threshold semantics without copying upstream
implementation debt: there is no duplicated mutable `IsBlind`, no mutable
per-character minimum/maximum, unchanged clamped values do not dirty or
resynchronize, and arithmetic is guarded against overflow and non-finite
values.

This milestone does not claim partial blur for damage `1..8`, eye anatomy or
organs, eyelid actions, glasses/correction, PVS or examination restrictions,
sprint or critical-hit side effects, or a broad camera/vision system. It also
does not claim the full SS14 blindness, fog, or character model behavior.

## Verification

Focused reducer tests cover constructor, persistent and stream codecs, invalid
values, float-precision floor asymmetry, zero/no-op behavior, clamping,
threshold healing, checked arithmetic, and fog tightening/composition.
GameTests cover server dispatch,
threshold transitions, cleanup, supported no-op non-materialization, and quiet
unsupported nonliving targets. The support matrix is exactly 16 functional and
19 explicit unsupported effect variants.
