package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Cosmetic-only appearance and landing cue for an ejected spent casing. */
public record SpentCartridgePresentation(ResourceLocation sprite, PositionalSoundCue landingCue,
                                         float initialSpeed, float gravity, float damping,
                                         float lifetimeSeconds) {
    public static final Codec<SpentCartridgePresentation> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("sprite").forGetter(SpentCartridgePresentation::sprite),
            PositionalSoundCue.codec(-1f, 0f).fieldOf("landing_cue").forGetter(SpentCartridgePresentation::landingCue),
            Codec.FLOAT.optionalFieldOf("initial_speed", 0.16f).forGetter(SpentCartridgePresentation::initialSpeed),
            Codec.FLOAT.optionalFieldOf("gravity", 0.035f).forGetter(SpentCartridgePresentation::gravity),
            Codec.FLOAT.optionalFieldOf("damping", 0.72f).forGetter(SpentCartridgePresentation::damping),
            Codec.FLOAT.optionalFieldOf("lifetime_seconds", 1.5f).forGetter(SpentCartridgePresentation::lifetimeSeconds)
    ).apply(i, SpentCartridgePresentation::new));

    public SpentCartridgePresentation {
        if (sprite == null || landingCue == null || !Float.isFinite(initialSpeed) || initialSpeed < 0f
                || !Float.isFinite(gravity) || gravity < 0f || !Float.isFinite(damping) || damping < 0f || damping > 1f
                || !Float.isFinite(lifetimeSeconds) || lifetimeSeconds <= 0f)
            throw new IllegalArgumentException("invalid spent cartridge presentation");
    }
}
