package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Mob-owned respiration cadence and saturation policy. */
public record RespiratorPolicy(double breathIntervalSeconds, double breathVolumeLiters,
        double maxSaturation, double initialSaturation, double minSaturation,
        double saturationLossPerUpdate, double suffocationThreshold,
        double suffocationDamagePerUpdate, double suffocationRecoveryPerUpdate,
        boolean suffocationIgnoreResistances, double gaspIntervalSeconds, Optional<ResourceLocation> gaspSound) {
    public RespiratorPolicy(double breathIntervalSeconds, double breathVolumeLiters, double maxSaturation,
            double initialSaturation, double minSaturation, double saturationLossPerUpdate,
            double suffocationThreshold, double suffocationDamagePerUpdate,
            double suffocationRecoveryPerUpdate, boolean suffocationIgnoreResistances) {
        this(breathIntervalSeconds, breathVolumeLiters, maxSaturation, initialSaturation, minSaturation,
                saturationLossPerUpdate, suffocationThreshold, suffocationDamagePerUpdate,
                suffocationRecoveryPerUpdate, suffocationIgnoreResistances, 8.0, Optional.empty());
    }
    public static final Codec<RespiratorPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("breath_interval_seconds").forGetter(RespiratorPolicy::breathIntervalSeconds),
            Codec.DOUBLE.fieldOf("breath_volume_liters").forGetter(RespiratorPolicy::breathVolumeLiters),
            Codec.DOUBLE.fieldOf("max_saturation").forGetter(RespiratorPolicy::maxSaturation),
            Codec.DOUBLE.fieldOf("initial_saturation").forGetter(RespiratorPolicy::initialSaturation),
            Codec.DOUBLE.fieldOf("min_saturation").forGetter(RespiratorPolicy::minSaturation),
            Codec.DOUBLE.fieldOf("saturation_loss_per_update").forGetter(RespiratorPolicy::saturationLossPerUpdate),
            Codec.DOUBLE.fieldOf("suffocation_threshold").forGetter(RespiratorPolicy::suffocationThreshold),
            Codec.DOUBLE.fieldOf("suffocation_damage_per_update").forGetter(RespiratorPolicy::suffocationDamagePerUpdate),
            Codec.DOUBLE.fieldOf("suffocation_recovery_per_update").forGetter(RespiratorPolicy::suffocationRecoveryPerUpdate),
            Codec.BOOL.fieldOf("suffocation_ignore_resistances").forGetter(RespiratorPolicy::suffocationIgnoreResistances),
            Codec.DOUBLE.optionalFieldOf("gasp_interval_seconds", 8.0).forGetter(RespiratorPolicy::gaspIntervalSeconds),
            ResourceLocation.CODEC.optionalFieldOf("gasp_sound").forGetter(RespiratorPolicy::gaspSound)
    ).apply(i, RespiratorPolicy::new));
}
