package com.juicyslew.moonstation14.ms14.slip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Validated response values shared by slippery source compositions and the live response owner. */
public record SlipResponseData(double stunSeconds, double knockdownSeconds, double launchVelocityMultiplier,
                               double frictionFactor, double frictionDurationSeconds, double staminaDamage,
                                boolean affectsSliding, boolean requiresContact, boolean superSlippery,
                                ResourceLocation soundEvent, double soundVolumeDb) {
    public static final ResourceLocation DEFAULT_SOUND = ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/effects/slip");
    public static final SlipResponseData DEFAULT = new SlipResponseData(.5d, 1.5d, 1.5d, .5d,
            .5d, 25d, false, true, false, DEFAULT_SOUND, 0d);
    public static final MapCodec<SlipResponseData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("stun_seconds", .5d).forGetter(SlipResponseData::stunSeconds),
            Codec.DOUBLE.optionalFieldOf("knockdown_seconds", 1.5d).forGetter(SlipResponseData::knockdownSeconds),
            Codec.DOUBLE.optionalFieldOf("launch_velocity_multiplier", 1.5d).forGetter(SlipResponseData::launchVelocityMultiplier),
            Codec.DOUBLE.optionalFieldOf("friction_factor", .5d).forGetter(SlipResponseData::frictionFactor),
            Codec.DOUBLE.optionalFieldOf("friction_duration_seconds", .5d).forGetter(SlipResponseData::frictionDurationSeconds),
            Codec.DOUBLE.optionalFieldOf("stamina_damage", 25d).forGetter(SlipResponseData::staminaDamage),
            Codec.BOOL.optionalFieldOf("affects_sliding", false).forGetter(SlipResponseData::affectsSliding),
            Codec.BOOL.optionalFieldOf("requires_contact", true).forGetter(SlipResponseData::requiresContact),
            Codec.BOOL.optionalFieldOf("super_slippery", false).forGetter(SlipResponseData::superSlippery),
            ResourceLocation.CODEC.optionalFieldOf("sound_event", DEFAULT_SOUND).forGetter(SlipResponseData::soundEvent),
            Codec.DOUBLE.optionalFieldOf("sound_volume_db", 0d).forGetter(SlipResponseData::soundVolumeDb)
    ).apply(instance, SlipResponseData::new));

    public SlipResponseData {
        validate(stunSeconds, "stunSeconds"); validate(knockdownSeconds, "knockdownSeconds");
        validate(launchVelocityMultiplier, "launchVelocityMultiplier"); validate(frictionFactor, "frictionFactor");
        validate(frictionDurationSeconds, "frictionDurationSeconds"); validate(staminaDamage, "staminaDamage");
        if (soundEvent == null || !Double.isFinite(soundVolumeDb) || soundVolumeDb < -120d || soundVolumeDb > 24d)
            throw new IllegalArgumentException("invalid slip sound cue");
    }

    public SlipResponseData(double stunSeconds, double knockdownSeconds, double launchVelocityMultiplier, double frictionFactor, double frictionDurationSeconds, double staminaDamage, boolean affectsSliding, boolean requiresContact, boolean superSlippery) {
        this(stunSeconds, knockdownSeconds, launchVelocityMultiplier, frictionFactor, frictionDurationSeconds, staminaDamage, affectsSliding, requiresContact, superSlippery, DEFAULT_SOUND, 0d);
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) throw new IllegalArgumentException(name + " must be finite and non-negative");
    }

    public static SlipResponseData puddle(double stun, double knockdown, double launch, double friction, boolean superSlippery) {
        return new SlipResponseData(stun, knockdown, launch, friction, 0d, 0d, true, true, superSlippery);
    }
}
