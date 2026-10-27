package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Typed visual/timing definition for supported melee arc kinds. */
public record MeleeArcProfile(ResourceLocation texture, Animation animation, boolean fadeOut, double lifetimeSeconds) {
    public enum Animation { NONE, THRUST, SLASH }

    public static final Codec<MeleeArcProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(MeleeArcProfile::texture),
            Codec.STRING.xmap(Animation::valueOf, Enum::name).optionalFieldOf("animation", Animation.NONE).forGetter(MeleeArcProfile::animation),
            Codec.BOOL.optionalFieldOf("fade_out", true).forGetter(MeleeArcProfile::fadeOut),
            Codec.DOUBLE.optionalFieldOf("lifetime_seconds", 2.0).forGetter(MeleeArcProfile::lifetimeSeconds)
    ).apply(i, MeleeArcProfile::new));

    public MeleeArcProfile {
        if (texture == null || animation == null) throw new IllegalArgumentException("arc texture and animation are required");
        if (!Double.isFinite(lifetimeSeconds) || lifetimeSeconds <= 0) throw new IllegalArgumentException("arc lifetime must be finite and positive");
    }
}
