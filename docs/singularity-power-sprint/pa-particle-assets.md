# PA particle asset and composition provenance

Source audited at Space Station 14 revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

* The native `ParticleAcceleratorParticle` leaf selects `Structures/Power/Generation/PA/particle.rsi`, with four unshaded strength states. The imported PNG states and `meta.json` are retained at `assets/moonstation14/textures/entity/pa_particle/`; Minecraft resolves the local `sprite` stem plus the synchronized strength number and `.png`.
* The projectile profile keeps local radiation damage and the selected leaf's 60-tick lifetime, .48 extent, non-deleting contact behavior, and emitter speed. `base_bullet` carries its resolved parent lifetime (200 ticks) and default delete-on-collide true, with a conservative .3 3D envelope for the upstream asymmetric 2D shape. The PA leaf overrides these values. The local typed profile only models radiation damage; upstream physical piercing damage and other unsupported projectile components are not represented or claimed as absent upstream.
* The sound event is `moonstation14:pa_particle_hit`, routed through the existing `ModSounds` registry and `sounds.json` collection. The sample is imported from upstream `Audio/Weapons/Guns/Hits/bullet_hit.ogg`.

The local projectile consumer currently does not implement the upstream bullet's other physical damage/effects; these fields are not invented as unsupported runtime components. The crossed-plane client billboard is shared with the existing emitter bolt renderer, preserving that renderer's texture, scale and light behavior.
