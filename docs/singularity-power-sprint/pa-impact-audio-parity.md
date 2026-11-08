# PA impact audio parity

Pinned upstream checkout: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

`Content.Server/Projectiles/ProjectileSystem.cs:81` routes qualified projectile contacts to `GunSystem.PlayImpactSound`; `Content.Server/Weapons/Ranged/Systems/GunSystem.cs:263-293` selects the projectile impact sound (or weapon fallback) independently of whether damage was admitted. The PA particle's generator collision consumes the contact regardless of `receiveParticle` acceptance, so it now emits its configured impact event once before consuming itself. This preserves inert/cooldown/rejected generator impact audio.

Living contacts emit once while the particle is not spent and the target has not been removed; damage admission and post-hit `IsAlive` no longer suppress the cue. Removed targets remain silent. `ParticleAcceleratorParticleEntity`'s ordered contact/overlap/discard flow continues to deduplicate and consume contacts. Singularity event-horizon ingestion is not a projectile impact and remains silent; ordinary wall pass-through remains silent.
