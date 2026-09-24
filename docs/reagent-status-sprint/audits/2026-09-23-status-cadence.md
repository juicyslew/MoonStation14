# Stimulant status cadence integration audit

## Scope

`StimulantCadenceGameTests.stimulantStatusesStayContinuousOnlyWithAnOngoingReservoir`
uses the real `stimulants` reagent and resolved status prototypes against a
villager. It runs `TickHooks.runDueActivities` for reagent metabolism and
manually advances `StatusEffectSystem` on that same entity to avoid adding
duplicate natural status ticks during the countdown assertions.

## Verified behavior

- A metabolism pass requests at most 25 stomach cents; fixed-point efficacy
  floors this to 12 body cents (0.12 units), intentionally losing the other
  13 source cents rather than rounding to 0.125. The newly transferred body
  dose is not processed until the next pass.
- A subsequent body pass processes its 0.12 dose once, while the existing
  stomach reservoir contributes only one additional 0.12 body dose. The
  bloodstream `reagentspeedstatuseffect` and `jitter` UPDATEs are both floored
  to 21 ticks while the ongoing stomach reservoir remains.
- Both effects remain active for all 20 intervening status ticks. The 1.25
  transient speed projection remains present, and the metabolism refresh
  updates the existing projection rather than removing and recreating it.
- With the reservoir removed and only a final 0.125 body dose available, both
  statuses use their ordinary 10-tick duration (scale 0.25), remain through
  tick 9, and expire on tick 10. Thus 21 ticks is scoped to ongoing-source
  finite immediate UPDATEs; it is not a global minimum for direct/manual or
  otherwise isolated stimulant effects.

## Why the local 21-tick floor exists (not upstream parity)

The floor is a deliberate **local UX smoothing policy**, retained because the
local metabolism cadence is staggered at 20 ticks and the reported local
ongoing-reservoir experience should not show gaps. It was requested to smooth
that local cadence. It is not a rule copied from SS14, and the floor must not be
presented as proof that pinned upstream never flickers.

At pinned upstream commit
[`c9df5ef5d675b0d1d226828bddf6b78c28502d91`](https://github.com/space-wizards/space-station-14/commit/c9df5ef5d675b0d1d226828bddf6b78c28502d91),
[`base_organs.yml`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Prototypes/Body/base_organs.yml)
defines `OrganBaseStomach` with `Metabolizer` stage `Digestion` and
`OrganBaseHeart` with stage `Bloodstream`. Both inherit the one-second
`UpdateInterval` default from
[`MetabolizerComponent.cs`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/Metabolism/MetabolizerComponent.cs).
That component's default Digestion route transfers at most 0.25 units at
efficacy 0.5. In
[`MetabolizerSystem.cs`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/Metabolism/MetabolizerSystem.cs),
when a reagent has no entry for the current stage, the transfer branch uses
that generic rate/efficacy. Pinned
[`narcotics.yml`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Prototypes/Reagents/narcotics.yml)
gives `Stimulants` a `Bloodstream` entry only—there is no `Digestion` entry.

For a low oral-route amount, the fixed-point transfer is 0.25 × 0.5 = 0.125,
quantized to 0.12 units. The generic fallback supplies it to the body; the
Bloodstream metabolism entry's default
[`ReagentPrototype.ReagentEffectsEntry.MetabolismRate`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/Chemistry/Reagent/ReagentPrototype.cs)
is 0.5, so the effect scale is 0.12 / 0.5 = 0.24. A two-second status duration
therefore scales to 0.48 seconds, or about 10 ticks (ceil), while the organ
metabolism interval is 20 ticks. The legacy
[`JitterEntityEffectSystem`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/EntityEffects/Effects/StatusEffects/JitterEntityEffectSystem.cs)
uses `Jitter.Refresh = true`; the old status system's refresh behavior does not
shorten a longer expiry. The new
[`MovementSpeedModifierEntityEffectSystem`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/EntityEffects/Effects/StatusEffects/MovementSpeedModifierEntityEffectSystem.cs)
routes UPDATE to
[`MovementModStatusSystem.TryUpdateMovementSpeedModDuration`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/Movement/Systems/MovementModStatusSystem.cs),
which delegates to the new status system's update-duration behavior rather
than accumulating time. These refresh rules do not add durations. Thus the
source alone does **not** guarantee continuous status between low-dose
oral updates under human defaults; possible oral flicker is a code-level
prediction, not a claim about observed upstream play.

This analysis does not establish what happens for every actual ingestion or
organ scheduling phase. A direct bloodstream injection delivering at least
0.5 units has scale 1 and a two-second (40-tick) status duration, which can
overlap a one-second update; real dose, timing, effects, modifiers, and other
systems matter. No change to the local floor or reagent math is implied.

The upstream source does not confirm or refute the reported SS14 client
anecdote. Establishing actual gameplay flicker would require observing that
client path with its real dose and scheduling; the calculation above is not a
client reproduction.

## Validation

- `./gradlew.bat compileGametestJava` — passed.
- `./gradlew.bat runGameTestServer -Pms14GameTestDir=build/gametest-run` —
  passed; latest game-test log reports all 77 required tests passed.

Packet emission for unchanged status UPDATEs is not directly counted by this
test. It verifies state continuity, expiration, and projection identity; packet
suppression remains covered only insofar as the production status reducer and
provider avoid unchanged updates.
