# Owner correction — real muzzle sources, volumes and supported prone aim

Technical PASS `run_abafad5627dfcc17d1b4da16` was followed by further owner
feedback, not acceptance. This scope supersedes round-three presentation choices.

## Ordered work units

1. **Feedback correctness:** close the Inspector's full-cylinder stale-ammo
   prediction race. Six live rounds must produce six gunshot cues, with empty/dry
   feedback thereafter, including reply/snapshot reordering. Verify actual source
   projectile speed, lifetime, range and collision/removal behavior.
2. **Real muzzle and volumetric effects:** use the final rendered weapon transform
   and authored muzzle binding, not eye/camera offsets. Bullets, beams and flashes
   must have closed 3D geometry visible from all angles, not crossed planes.
   Svalinn fires one logical ray; muzzle/impact visuals must not resemble extra rays.
   Preserve source art and authoritative traces/damage. Reuse the body/hand renderer
   and existing effect owners, with one shared geometry implementation.
3. **Ground controls:** open the existing retained controls window from a world
   gun's right-click entry. Bind it to that exact world source and reuse shared
   manipulation/transfer services. No implicit pickup, new gun menu, or general
   context-menu redesign. The basic world-gun entry is now required in this pass.
4. **Presentation corrections:** cell charge is ordinary-sized, muted hand-detail
   text. Svalinn gets one uniform resolved held/world scale, with recalculated bounds;
   artist geometry, textures, UVs and display transforms remain unchanged.
5. **Supported prone aim:** validated gun wield + harm mode while down forces
   stomach orientation and body-facing toward aim, including stationary look input.
   Use genuinely articulated upper/forearms, two supported elbows, raised chest/head,
   both gun contacts, and a shared physical-eye/camera/shot-origin derivation.
   Rendering only the gun toward aim while the body obscures it is not acceptable.

Ordinary prone, medical incapacitation, non-gun wielding, and turnover controls
outside this narrow aim mode retain their existing owners. The earlier movement-only
prone facing rule and support-for-both-rolls aiming choice are explicitly superseded
for active wielded-gun harm aiming.

## Acceptance boundaries

- Test actual native model/hand transforms for all eight guns, both handings and
  wide/slim rigs. Articulated neutral poses must preserve existing upright visuals.
- Effect meshes need closed-volume, winding, source-alpha/UV and all-angle coverage;
  generate production-driven CPU 3D previews for independent inspection where useful.
- Test full-cylinder client feedback and real world-source UI admission/retention,
  not only isolated helpers or server firing cadence.
- Preserve item-owned ammo/charge, persistent spent-item cleanup, the powered
  recharger, previous ownership/authority fixes, and imported asset provenance.
- Fresh complete serialized readiness remains required. CPU/source evidence is not
  native/GPU visual review or owner acceptance.

## Projectile travel boundary

Resolved gun profiles use `projectile_speed: 40`; firing conversion divides by
20 ticks/second, so a stationary shot travels nominally 2 blocks/tick. Ballistic
and energy projectiles use a 10-second lifetime (200 ticks), giving about 400
blocks of theoretical travel only while every traversed chunk remains loaded and
the path stays unobstructed. This is not a guaranteed range or a fixed distance
cap: unloaded-chunk checks pause movement while age continues, and lifetime
expiry removes the projectile. Retro Laser/Svalinn each fire one 20-unit ray.
Energy Shotgun fires four moving projectiles in its lethal mode or three in its
disabler mode, not seven projectiles or hitscan. A continuously loaded 400-block
user case has not been reproduced. See `PARITY.md` for the pinned upstream mapping.

The short visible travel had a concrete native cause: `Entity` derives render
distance from bounding-box size multiplied by 64, so the typed projectile's `.1`
collider caused a roughly 6.4-block cutoff at unit view scale. Its presentation
override now uses the existing entity tracking range (eight chunks, nominally
128 blocks) and native view scale. The physical collider, source speed, lifetime,
loaded-chunk policy, and collision checks are unchanged.
