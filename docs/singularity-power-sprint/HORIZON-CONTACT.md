# Singularity horizon body contact

Pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`: root `singularity.yml`
has `EventHorizonComponent` defaults `consumeEntities=true`, `consumePeriod=0.5`
seconds, and `consumeTiles=true`. `SharedSingularitySystem` resolves radius as
`level - 0.5` (levels 1–6: 0.5–5.5); server `EventHorizonSystem` consumes entities
on contact/deletion and ordinary entities provide one unit of energy. Locally,
the resolved `SingularityGameplayProfile.eventHorizon` carries entity settings,
with SS14 scan timing replaced by every-tick contact checks;
`SingularityEntity.tick` consumes living physical bodies using that existing
resolved radius and `feed` energy authority. Gear/radiation protection does not
provide horizon immunity.

This is intentionally narrower than SS14: only lethal living-body contact is
implemented. It sets health to zero and invokes native `LivingEntity.die()` with
the generic-kill source, preserving the existing medical death admission and
confirmed `PlayerCharacterHarnessEntity` death handler. Native `kill()` was not
usable here: its maximum-float damage overflows the finite typed-damage boundary.
Energy is credited only when the native `LivingEntity.dead`
accepted-death flag (read via the existing death-state accessor mixin) is set;
zero health/`isDeadOrDying()` alone is insufficient. Minecraft corpses are retained; safely deleting owned bodies
and their inventory is not implemented. Terrain/tile destruction, items,
recursive containers, and `SinguloFood` special energy are deferred. The 3D
body-AABB/sphere intersection is a Minecraft adaptation; nonphysical and
spectator entities and active native character carriers are excluded. Focused
fixtures verify the actual equipped radiation suit still dies, its native death
callback runs, canceled native death gives no energy, and the owned corpse is
retained. They do not initialize durable online `DEAD_CLAIM`/ghost staging; that
ownership transition remains unverified here. Reference: SS14
`Content.Shared/Singularity/Components/EventHorizonComponent.cs`,
`Content.Shared/Singularity/EntitySystems/SharedSingularitySystem.cs`, and
`Content.Server/Singularity/EntitySystems/EventHorizonSystem.cs`.

## Runtime fixture radius and containment movement

Pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`: root
`singularity.yml` authors collider and consumer fixture radii as 0.35, but that
is not their runtime radius. `SharedSingularitySystem.UpdateSingularityLevel`
(lines 120–130) calls `SetRadius(level - 0.5, false)` then
`UpdateEventHorizonFixture`; `SharedEventHorizonSystem.UpdateEventHorizonFixture`
(lines 149–172) sets **both** named fixtures to the current horizon radius.
`SetRadius` defaults `updateFixture=true` (lines 59–71), and startup also
updates fixtures. Thus runtime collider/consumer radii are 0.5–5.5, including
stage 3 at 2.5 and stage 4 at 3.5. Local field movement correctly uses the
resolved level-scaled horizon radius; upstream does not support a separate
constant physical radius.

The default compact operator fixture puts field generators at X={5,9}, Y={3,7}, Z={7,11};
its face planes were block centers X=5.5/9.5, Y=3.5/7.5, Z=7.5/11.5. Each span
was 4 blocks, leaving 2 blocks of midpoint center clearance. Stage 3+ radii
exceed that compact enclosure's half-width, so it cannot permit movement while
contained at those stages. The centered 8-block
preset is available only as an explicitly inactive wide build, with cube
X={3,11}, Y={1,9}, Z={5,13}; new default builds remain compact and existing
worlds are not migrated. References:
pinned `SharedSingularitySystem.cs:120–130` and
`SharedEventHorizonSystem.cs:59–71,149–172`.

## Native pig contact reproduction

The focused `pig-repro,nativegravity` GameTest fixtures in
`SingularityGravityGameTests` spawn ordinary-AI vanilla pigs and use the
resolved level-3 source profile. They cover a 7.5-block horizontal start, a
pig 7 blocks above the source, and an already-inside-horizon control. The test
source's random walk is stabilized only through its saved walk timer; production
source movement is unchanged. The horizontal case uses a supporting floor over
the approach corridor so falling off a one-block floor cannot masquerade as a
gravity fling. Tests assert accepted native death through the death-state
accessor, and the moving cases require a measured native displacement aligned
with a live source pulse plus a decreasing source distance.

Focused server evidence: `build/validation-logs/singularity-pig-native-20261013-r8.log`
(`pig-repro,nativegravity`, 3 selected / 863 discovered, 3 passed). Horizontal
trajectory began at 7.5 blocks, approached to 3.20 blocks before the accepted
death at game time 157, and recorded maximum native speed 0.144 blocks/tick
(maximum per-tick displacement 0.221). The above-source trajectory reached
2.59 blocks and accepted death at game time 34; max speed was 0.948 blocks/tick.
The inside control died with horizon intersection confirmed. These runs show
successful contact and ordinary finite-speed approach, not a reproduced fling
bug or connected-client behavior. Earlier failed trials had a single floor
block (the horizontal pig walked off and fell away) or checked for a pulse
before its scheduled deadline; those were fixture errors, not evidence of a
consumer exclusion. Existing gravity kernel already has the pinned upstream
`distanceSquared < 1e-5` guard; no gameplay force or death-admission changes
were required.

## Quiet-source gravity pulse scheduling regression

The dormant-pulse fixture exposed a separate, causal clock defect. Before the
fix, a level-3 source with no targets for 600 ticks retained `NextGravityPulse`
at tick 36. The first ordinary pig spawned 30 seconds later at center distance
4 sampled that stale elapsed time and moved 11.269 blocks in one native tick
(peak speed 10.255 blocks/tick), crossing the 2.5-block horizon without a
contact check; at the next trace it remained alive 7.269 blocks away and then
continued outward. Evidence is preserved at
`build/validation-logs/singularity-pig-native-20261013-r9.log`.

Pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`
`Content.Server/Singularity/EntitySystems/GravityWellSystem.cs` updates every
well's due pulse schedule independently of queried targets. Local
`SingularityEntity.tick` now samples the existing source-local
`gravityPulseElapsed` once per server game time after resolving its profile;
native living-body and other gravity consumers reuse that cached sample. This
preserves the existing late-pulse one-period advancement and the upstream
gravity equation, range, minimum-distance guard, and player/movement cadence.
After the fix, a source idle 600 ticks had advanced its next pulse to tick 636
at game time 626. Its first measured native displacement was 0.188 blocks;
maximum displacement was 0.239 blocks/tick and maximum speed 0.152 blocks/tick.
The pig reached horizon intersection and accepted native death at time 657;
its center was 2.799 blocks away with the level-3 horizon radius 2.5 (the
body/sphere touch uses the whole pig AABB). Results:
`build/validation-logs/singularity-pig-native-20261013-r10.log` (1 selected /
864 discovered, 1 passed). This establishes the stale source clock as the
cause of overshoot; it does not establish connected-player or visual behavior.

Post-fix regression validation also reran the combined native gravity and
horizon fixtures, including native-travel single-application and quiet-source
ordering: `build/validation-logs/singularity-pig-native-20261013-r12.log`
(`singularity-gravity,singularity-horizon,dormant-gravity-pulse`, 13 selected /
864 discovered, 13 passed). The 7.5-block normal-AI pig is not required to die;
in this seeded run it approached to 3.13 blocks then completed contact normally
with max speed 0.170 blocks/tick and max per-tick displacement 0.276. The test
instead asserts actual pulse-aligned native movement without a horizon-skipping
step. The first combined run (`r11`) had that old fixture's unconditional
160-tick death expectation; the source clock change correctly stopped the
pre-idle fling, but normal AI wandered it out of range. That assertion was
replaced with bounded pulse-response coverage, not a gameplay change.
