package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Fully composed control profile consumed by the assembly resolver. */
public record ParticleAcceleratorControlProfile(Map<String, ParticleAcceleratorPartProfile> parts,
                                                 int controlPowerAp,
                                                 List<ParticleAcceleratorStrengthProfile> strengths,
                                                 ParticleAcceleratorStrength defaultStrength,
                                                 ParticleAcceleratorStrength maximumStrength,
                                                 int waveSeconds,
                                                 double particleSpeed,
                                                 ResourceLocation particleProfile) {
    public static final Codec<ParticleAcceleratorControlProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, ParticleAcceleratorPartProfile.CODEC).fieldOf("parts").forGetter(ParticleAcceleratorControlProfile::parts),
            Codec.intRange(0,100000).fieldOf("control_power_ap").forGetter(ParticleAcceleratorControlProfile::controlPowerAp),
            ParticleAcceleratorStrengthProfile.CODEC.listOf().fieldOf("strengths").forGetter(ParticleAcceleratorControlProfile::strengths),
            ParticleAcceleratorStrength.CODEC.fieldOf("default_strength").forGetter(ParticleAcceleratorControlProfile::defaultStrength),
            ParticleAcceleratorStrength.CODEC.fieldOf("maximum_strength").forGetter(ParticleAcceleratorControlProfile::maximumStrength),
            Codec.intRange(1,3600).fieldOf("wave_seconds").forGetter(ParticleAcceleratorControlProfile::waveSeconds),
            Codec.doubleRange(0.01,1000).fieldOf("particle_speed").forGetter(ParticleAcceleratorControlProfile::particleSpeed),
            ResourceLocation.CODEC.optionalFieldOf("particle_profile", ModParticleAcceleratorParticles.DEFAULT_ID).forGetter(ParticleAcceleratorControlProfile::particleProfile)
    ).apply(i, ParticleAcceleratorControlProfile::new));
    public ParticleAcceleratorControlProfile {
        parts = Map.copyOf(parts);
        strengths = List.copyOf(strengths);
        if (parts.isEmpty() || controlPowerAp < 0 || defaultStrength == null || maximumStrength == null
                || strengths.size() != ParticleAcceleratorStrength.values().length || waveSeconds <= 0
                || particleSpeed <= 0 || particleProfile == null)
            throw new IllegalArgumentException("Invalid PA control profile");
        var indexed = new java.util.EnumMap<ParticleAcceleratorStrength, ParticleAcceleratorStrengthProfile>(ParticleAcceleratorStrength.class);
        for (var strength : strengths) if (indexed.put(strength.strength(), strength) != null) throw new IllegalArgumentException("Duplicate PA strength");
        if (indexed.size() != ParticleAcceleratorStrength.values().length || defaultStrength.level() > maximumStrength.level())
            throw new IllegalArgumentException("Incomplete or invalid PA strength table");
    }

    public ParticleAcceleratorStrengthProfile strength(ParticleAcceleratorStrength strength) {
        return strengths.stream().filter(value -> value.strength() == strength).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing PA strength " + strength));
    }
}

