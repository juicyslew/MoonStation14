# Emitter mapping (pinned upstream audit)

Audited `SS14-dev/space-station-14` at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Resources/Prototypes/Entities/Structures/Power/Generation/Singularity/emitter.yml`
declares `Emitter` with parent `SmallConstructibleMachine`; its ancestry supplies
the constructible-machine and base-machine composition. Relevant explicit values
are `Lock.locked: false`, `Transform.anchored: true`, `PowerConsumer.voltage:
Medium`, and the MV cable node. `Emitter`'s power consumer and burst scheduler,
and `Gun`'s projectile launch path consume those values. The linked `On`, `Off`,
and `Toggle` ports are upstream behavior; local empty-hand toggle is a deliberate
initial interaction adaptation, not device-link parity.

The associated `EmitterBolt` is in
`Resources/Prototypes/Entities/Objects/Weapons/Guns/Projectiles/projectiles.yml`
and inherits `BaseBullet`. The local typed profile represents supported demand,
burst, velocity, lifetime, hitbox, and heat-damage fields; unsupported SS14 ECS
components are not mirrored. The native emitter block/entity and MV demand
adapter consume this resolved profile: it requests 600 W on and 1 W off, starts
requested-off and anchored, and reports `firingPowered` only when anchored,
requested-on, and freshly allocated exactly 600 W. Empty-hand interaction toggles
requested state. A power interruption clears pending cadence; restored power
starts a new delay. The server bolt uses bounded loaded-only swept movement and
the existing typed damage path. Movable unanchoring/wrench behavior and device
links remain deliberately deferred. `ProjectileLoadedSweep` owns the shared
loaded-only block-ray boundary intended for the pending PA projectile adapter;
the implementation deliberately does not reuse loose-item movement physics.

`EmitterBolt` also inherits `BaseBullet`'s `Projectile.impactEffect: BulletImpactEffect`
without override. The separate upstream prototype in `Projectiles/impacts.yml`
is an unshaded `impact_bullet` sprite effect despawned after 0.25 seconds;
`projectiles_tg.rsi/meta.json` defines five 0.05-second frames. The local server
emits a native particle exactly at qualified authoritative contact (living,
colliding block, or field generator) alongside existing impact audio. Its particle
options carry the resolved profile lifetime (default five ticks) through the
native particle protocol. The client provider's particle atlas lists five
pixel-exact 32px source-frame crops; each impact independently advances one
frame per game tick, looping after frame 4 regardless of despawn lifetime, then
expires at its resolved lifetime. This presentation
does not change damage, charge, or bolt-consumption behavior. Source attribution
and image hash are retained beside the imported texture.

The focused profile values requested by the implementation handoff (600 W on,
1 W off, randomized 4–10 second first-shot delay, three-shot burst with two-second spacing,
40 units/s and three-second bolt lifetime) are local adaptation values, not
asserted values from the upstream Singularity Emitter prototype. Profile data is
under `data/moonstation14/moonstation14/emitter/`.

Upstream's `EmitterSystem.Fire` spawns the configured bolt and delegates launch
to `GunSystem.Shoot`; `EmitterBolt` inherits `BaseBullet`, whose gun projectile
speed is consumed as world-distance per second by the projectile launch/physics
path. Locally, the profile retains 40 blocks/second while Minecraft entity
motion is blocks/tick, so the emitter converts exactly once using the shared
`DisposalsEjection.blocksPerSecondToBlocksPerTick` conversion (40 / 20 = 2).

The local MV port is on the side opposite the emitter's firing direction. This is
a Minecraft surface-cable adaptation: a muzzle-side cable would occupy the bolt's
launch path. It does not claim an upstream spatial-port orientation; upstream
composition remains the `PowerConsumer` medium-voltage requirement and `Rotatable`
ancestry documented above.
