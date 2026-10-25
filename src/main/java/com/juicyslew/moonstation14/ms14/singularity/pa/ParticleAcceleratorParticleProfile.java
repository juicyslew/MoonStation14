package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyByProfile;

/** Resolved projectile composition for SS14 ParticleAcceleratorParticle (BaseBullet parent defaults included). */
public record ParticleAcceleratorParticleProfile(float radiationDamage, int lifetimeTicks,
        double halfExtent, boolean deleteOnCollide, String impactSound, String sprite,
        ProjectileFlyByProfile flyBy) {
    public static final Codec<ParticleAcceleratorParticleProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0, 100000).fieldOf("radiation_damage").forGetter(ParticleAcceleratorParticleProfile::radiationDamage),
            Codec.intRange(1, 12000).fieldOf("lifetime_ticks").forGetter(ParticleAcceleratorParticleProfile::lifetimeTicks),
            Codec.doubleRange(.01, 16).fieldOf("half_extent").forGetter(ParticleAcceleratorParticleProfile::halfExtent),
            Codec.BOOL.fieldOf("delete_on_collide").forGetter(ParticleAcceleratorParticleProfile::deleteOnCollide),
            Codec.STRING.fieldOf("impact_sound").forGetter(ParticleAcceleratorParticleProfile::impactSound),
            Codec.STRING.fieldOf("sprite").forGetter(ParticleAcceleratorParticleProfile::sprite),
            ProjectileFlyByProfile.CODEC.optionalFieldOf("fly_by", ProjectileFlyByProfile.DEFAULT).forGetter(ParticleAcceleratorParticleProfile::flyBy)
    ).apply(i, ParticleAcceleratorParticleProfile::new));
}
