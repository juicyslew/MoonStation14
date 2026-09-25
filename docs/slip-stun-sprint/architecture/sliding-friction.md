# Sliding Friction Adapter Design (M5)

**Status: bounded M5 implementation is present; owner connected-client verification remains open.** This is a Minecraft physics adapter for the established slippery-source and volatile sliding projections. It does not change reagent schema, puddle contact, slip admission, or reactive Touch behavior. The sprint remains in progress; see the [M3 audit](../audits/m3-puddle-slip-trigger.md) and [M4 audit](../audits/m4-slip-triggered-touch.md) for implementation evidence and open acceptance gates.

## Reference semantics and local inputs

Pinned SS14 `Content.Shared/Slippery/SlidingSystem.cs:18-125` averages friction from contacted sources for an already-sliding entity, then applies the result to both `ModifyFriction(F)` and `ModifyAcceleration(F)`. That is the source behavior to adapt; do not confuse this with a direct Minecraft block-friction replacement. The puddle source projection and `AffectsSliding` behavior are in `Content.Shared/Fluids/SharedPuddleSystem.cs:258-318`.

The canonical reagent prototype has top-level `friction` (default `1.0`) and a separate nested source `slipData`. Preserve source camelCase SS14 field names; there is no Minecraft-specific JSON/schema for this adapter. Local `SlipperySolution.Outcome.friction` is computed from the whole current puddle solution, including its established contribution/default semantics. It is the source value consumed here; this implementation does not redefine that aggregation. The existing minimum source-size threshold still governs slippery activation; it is not replaced by the puddle flow cutoff/capacity.

## Approved Minecraft conversion

The pure conversion is in `ms14/slip/MinecraftSlidingPhysics`. For finite `F >= 0` and vanilla ground momentum retention `r_vanilla`, it defines:

```text
accelerationFactor(F) = F
momentumRetention(r_vanilla, F) = pow(r_vanilla, F)
```

The helper must not access world/entity state. On the real vanilla ground-movement branch, multiply vanilla ground acceleration by `accelerationFactor(F)` and use the converted retention for momentum damping. In particular, Minecraft ground travel derives acceleration using `f_block` approximately as `speed * 0.216 / f_block^3` and retention as `r_vanilla = f_block * 0.91`. Substituting `F` for `f_block` is prohibited: a low `F` such as `0.05` would greatly amplify the cubic acceleration term rather than attenuate acceleration.

The mapping preserves the neutral case and endpoints:

| Input | Acceleration multiplier | Converted momentum retention |
| --- | ---: | ---: |
| `F = 1` | `1` | `r_vanilla` (vanilla baseline) |
| `F = 0` | `0` | `1` (full momentum retention) |
| finite `F > 0` | `F` | `pow(r_vanilla, F)` |

For no active sliding, no qualifying source, or an unknown/unavailable source outcome, use `F = 1`; ordinary movement therefore remains vanilla. The bounded feet-band source scan fails neutral for missing/unbound character identity or unavailable/invalid catalogs and caps its horizontal scan extent. Exclude fluid movement, flying, vehicles, and other non-ground travel branches from this adapter. They are deferred, not silently altered. Reject nonfinite or negative `F` at the pure helper boundary rather than coercing it.

Exponential damping is an explicit Minecraft adapter approximation to SS14's friction-rate behavior. It is not a new JSON unit, a promise of identical timestep semantics, or exact SS14 bit/timestep parity. It uses vanilla retention as the neutral baseline so that `F = 1` does not change normal ground motion.

## Sliding-state and source projection ownership

- Active sliding uses a typed, synchronized, **nonpersistent boolean `SlidingAttachment`**, separate from character identity and timed status data. It is volatile runtime projection, not a new character capability and not a substitute for knockdown/status ownership.
- `MS14Provider` owns attachment mutation and change-only synchronization; `MS14Bridges` owns the relevant game/attachment boundary. The server updates the boolean only on sliding activation/removal transitions, never emits a packet every movement tick.
- The server and client read the synchronized sliding boolean and synchronized puddle solution/catalog data. Each calculates source friction from puddles under the actor's feet: arithmetic mean of current, non-inert `SlipperySolution.Outcome.friction` values. Upstream `SharedPuddleSystem` sets `AffectsSliding` for total solution volume above 15 before testing `slipperyUnits` against its threshold. Thus a non-slippery puddle above 15 units can have the sliding flag and contribute its neutral friction (`F=1`) to the mean alongside Space Lube while the actor is already sliding. This is intentional upstream parity, not an erroneous source to exclude. With zero qualifying sources, use neutral `F = 1`.
- Do not copy source slip/friction data into floor blocks, introduce puddle collision, or make blocks the authority for source chemistry. Contact/source overlap remains the existing puddle adapter's responsibility.
- Leaving slippery contact immediately removes its contribution and makes the conversion neutral (`F = 1`) if no other qualifying puddle overlaps. Sliding state itself remains active until the existing knockdown expiry/reconciliation says Stood, or death/dimension transition clears it; merely leaving the puddle does not end the slide.
- Since the attachment is nonpersistent, server `SlipSystem.onEntityJoin` clears it; clone policy also clears it. Do **not** blindly rehydrate from generic knockdown: knockdown may come from a nonslip source and does not prove active sliding provenance. Reconnect/reload or dimension transfer can therefore end an ongoing slide; this is an explicit known parity gap. Durable restoration would require source provenance, if the owner wants it. The synced marker is transition-updated; client join has no stale persisted marker and awaits authoritative sync.
- `LivingEntitySlidingFrictionMixin` targets `LivingEntity`, so `LocalPlayer` inherits the client adapter as well as server living entities. A client may temporarily lack the local prototype catalog while it is loading; lookup failure is neutral (`F=1`) and calculations can reflect synchronized catalog/solution data when available. Do not guess or cache stale friction. This can produce temporary client/server movement divergence. There is no client runtime validation claimed; connected-client agreement and any presentation behavior remain owner checks.

## Validation and acceptance boundary

1. `MinecraftSlidingPhysicsTest` covers neutral and zero factors, representative finite values, and invalid inputs. `SlidingFrictionGameTests` cover no-contact neutrality, live puddle content updates, arithmetic mean across sources (including Space Lube plus a non-slippery neutral source), grounded Villager/FakePlayer acceleration and retention paths where movement is available, leaving puddle contact, marker removal, and friction capture across a puddle exit. Its harness explicitly excludes all-zero FakePlayer direct-travel comparisons rather than treating them as evidence. `PuddleSlipGameTests` also cover server slip activation, pre-event sliding state, subsequent slip while sliding, and expiry clearing the marker. These are focused unit/server tests, not proof of connected-client movement or synchronization.
2. The owner must do the manual connected-client test in the checklist: compare grounded movement/input over the same lube mixture on puddle and ordinary floor, include a second non-slippery puddle while already sliding, verify leaving contact restores baseline when no other source remains, and check reconnect/dimension behavior for stale markers. Do not report client integration as proven by dedicated-server GameTests.
