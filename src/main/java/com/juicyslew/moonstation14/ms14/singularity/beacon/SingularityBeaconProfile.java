package com.juicyslew.moonstation14.ms14.singularity.beacon;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Supported beacon composition: APC draw, attraction pulse, and consumed-food credit. */
public record SingularityBeaconProfile(ResourceLocation id, double powerWatts, int attractorPeriodTicks,
                                       double attractorRange, double foodEnergy, double foodFactor) {
    public static final Codec<SingularityBeaconProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(SingularityBeaconProfile::id),
            Codec.doubleRange(0, 100_000).optionalFieldOf("power_watts", 5000d).forGetter(SingularityBeaconProfile::powerWatts),
            Codec.intRange(1, 1200).optionalFieldOf("attractor_period_ticks", 40).forGetter(SingularityBeaconProfile::attractorPeriodTicks),
            Codec.doubleRange(0, 10000).optionalFieldOf("attractor_range", 80d).forGetter(SingularityBeaconProfile::attractorRange),
            Codec.doubleRange(0, 1_000_000).optionalFieldOf("food_energy", 120d).forGetter(SingularityBeaconProfile::foodEnergy),
            Codec.doubleRange(0, 1000).optionalFieldOf("food_factor", 1d).forGetter(SingularityBeaconProfile::foodFactor)
    ).apply(i, SingularityBeaconProfile::new));
    public static final PrototypeType<SingularityBeaconProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_beacon"),
            "moonstation14/singularity_beacon", CODEC);
    public SingularityBeaconProfile {
        if (id == null || !Double.isFinite(powerWatts) || powerWatts < 0 || attractorPeriodTicks < 1
                || !Double.isFinite(attractorRange) || attractorRange < 0
                || !Double.isFinite(foodEnergy) || foodEnergy < 0 || !Double.isFinite(foodFactor) || foodFactor < 0)
            throw new IllegalArgumentException("Invalid singularity beacon profile");
    }
}
