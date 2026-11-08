package com.juicyslew.moonstation14.ms14.singularity.emitter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyByProfile;
import net.minecraft.resources.ResourceLocation;

/** Supported local subset of SS14 Emitter + PowerConsumer + Gun properties. */
public record EmitterProfile(ResourceLocation id, double onWatts, double offWatts,
                             int burstSize, int burstDelayTicks, int firstDelayMinTicks,
                             int firstDelayMaxTicks, double boltSpeed, int boltLifetimeTicks,
                                 float boltHeatDamage, double boltHalfExtent, ResourceLocation fireSound, ResourceLocation boltImpactSound,
                                 int boltImpactLifetimeTicks,
                                 ProjectileFlyByProfile boltFlyBy) {
    public static final Codec<EmitterProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(EmitterProfile::id),
            Codec.doubleRange(0, 1_000_000).fieldOf("on_watts").forGetter(EmitterProfile::onWatts),
            Codec.doubleRange(0, 1_000_000).fieldOf("off_watts").forGetter(EmitterProfile::offWatts),
            Codec.intRange(1, 16).fieldOf("burst_size").forGetter(EmitterProfile::burstSize),
            Codec.intRange(1, 200).fieldOf("burst_delay_ticks").forGetter(EmitterProfile::burstDelayTicks),
            Codec.intRange(1, 1200).fieldOf("first_delay_min_ticks").forGetter(EmitterProfile::firstDelayMinTicks),
            Codec.intRange(1, 1200).fieldOf("first_delay_max_ticks").forGetter(EmitterProfile::firstDelayMaxTicks),
            Codec.doubleRange(.01, 1000).fieldOf("bolt_speed").forGetter(EmitterProfile::boltSpeed),
            Codec.intRange(1, 1200).fieldOf("bolt_lifetime_ticks").forGetter(EmitterProfile::boltLifetimeTicks),
            Codec.floatRange(0, 10000).fieldOf("bolt_heat_damage").forGetter(EmitterProfile::boltHeatDamage),
            Codec.doubleRange(.01, 2).fieldOf("bolt_half_extent").forGetter(EmitterProfile::boltHalfExtent),
            ResourceLocation.CODEC.optionalFieldOf("fire_sound", ResourceLocation.fromNamespaceAndPath("moonstation14", "emitter/fire")).forGetter(EmitterProfile::fireSound),
            ResourceLocation.CODEC.optionalFieldOf("bolt_impact_sound", ResourceLocation.fromNamespaceAndPath("moonstation14", "pa_particle_hit")).forGetter(EmitterProfile::boltImpactSound),
             Codec.intRange(1, 1200).optionalFieldOf("bolt_impact_lifetime_ticks", 5).forGetter(EmitterProfile::boltImpactLifetimeTicks),
             ProjectileFlyByProfile.CODEC.optionalFieldOf("bolt_fly_by", ProjectileFlyByProfile.DEFAULT).forGetter(EmitterProfile::boltFlyBy)
    ).apply(i, (id, on, off, size, delay, min, max, speed, life, heat, extent, sound, impact, impactLife, flyBy) -> new EmitterProfile(id,on,off,size,delay,min,max,speed,life,heat,extent,sound,impact,impactLife,flyBy)));
    public EmitterProfile(ResourceLocation id, double onWatts, double offWatts, int burstSize, int burstDelayTicks,
                          int firstDelayMinTicks, int firstDelayMaxTicks, double boltSpeed, int boltLifetimeTicks,
                          float boltHeatDamage, double boltHalfExtent, ResourceLocation fireSound, ResourceLocation boltImpactSound) {
        this(id,onWatts,offWatts,burstSize,burstDelayTicks,firstDelayMinTicks,firstDelayMaxTicks,boltSpeed,boltLifetimeTicks,
                 boltHeatDamage,boltHalfExtent,fireSound,boltImpactSound,5,ProjectileFlyByProfile.DEFAULT);
    }
    public EmitterProfile(ResourceLocation id, double onWatts, double offWatts, int burstSize, int burstDelayTicks,
                          int firstDelayMinTicks, int firstDelayMaxTicks, double boltSpeed, int boltLifetimeTicks,
                          float boltHeatDamage, double boltHalfExtent, ResourceLocation fireSound) {
        this(id,onWatts,offWatts,burstSize,burstDelayTicks,firstDelayMinTicks,firstDelayMaxTicks,boltSpeed,boltLifetimeTicks,
                 boltHeatDamage,boltHalfExtent,fireSound,ResourceLocation.fromNamespaceAndPath("moonstation14","pa_particle_hit"),5,ProjectileFlyByProfile.DEFAULT);
    }
    public EmitterProfile(ResourceLocation id, double onWatts, double offWatts, int burstSize, int burstDelayTicks,
                          int firstDelayMinTicks, int firstDelayMaxTicks, double boltSpeed, int boltLifetimeTicks,
                          float boltHeatDamage, double boltHalfExtent) {
        this(id,onWatts,offWatts,burstSize,burstDelayTicks,firstDelayMinTicks,firstDelayMaxTicks,boltSpeed,boltLifetimeTicks,
                  boltHeatDamage,boltHalfExtent,ResourceLocation.fromNamespaceAndPath("moonstation14","emitter/fire"),ResourceLocation.fromNamespaceAndPath("moonstation14","pa_particle_hit"),5,ProjectileFlyByProfile.DEFAULT);
    }
    public static final PrototypeType<EmitterProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "emitter"),
            "moonstation14/emitter", CODEC);
    public EmitterProfile {
        if (id == null || firstDelayMaxTicks < firstDelayMinTicks || boltImpactLifetimeTicks < 1 || boltFlyBy == null) throw new IllegalArgumentException("Invalid emitter profile");
    }
}

