package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Typed visual/timing definition for supported melee arc kinds. */
public record MeleeArcProfile(ResourceLocation texture, Animation animation, boolean fadeOut, double lifetimeSeconds, MeleeStrikeProfile strike, ResourceLocation disarmTexture, WeaponPose weaponPose, int disarmFrames, boolean strikeTextureGlyph, int textureFrames, int textureColumns) {
    public MeleeArcProfile(ResourceLocation texture, Animation animation, boolean fadeOut, double lifetimeSeconds, MeleeStrikeProfile strike, ResourceLocation disarmTexture, WeaponPose weaponPose, int disarmFrames, boolean strikeTextureGlyph) { this(texture, animation, fadeOut, lifetimeSeconds, strike, disarmTexture, weaponPose, disarmFrames, strikeTextureGlyph, 1, 1); }
    public MeleeArcProfile(ResourceLocation texture, Animation animation, boolean fadeOut, double lifetimeSeconds, MeleeStrikeProfile strike, ResourceLocation disarmTexture, WeaponPose weaponPose, int disarmFrames) { this(texture, animation, fadeOut, lifetimeSeconds, strike, disarmTexture, weaponPose, disarmFrames, false, 1, 1); }
    public MeleeArcProfile(ResourceLocation texture, Animation animation, boolean fadeOut, double lifetimeSeconds, MeleeStrikeProfile strike, ResourceLocation disarmTexture, WeaponPose weaponPose) { this(texture, animation, fadeOut, lifetimeSeconds, strike, disarmTexture, weaponPose, 3); }
    public MeleeArcProfile(ResourceLocation texture, Animation animation, boolean fadeOut, double lifetimeSeconds, MeleeStrikeProfile strike, ResourceLocation disarmTexture) { this(texture, animation, fadeOut, lifetimeSeconds, strike, disarmTexture, WeaponPose.DEFAULT, 3); }
    public MeleeArcProfile(ResourceLocation texture, Animation animation, boolean fadeOut, double lifetimeSeconds, MeleeStrikeProfile strike) { this(texture, animation, fadeOut, lifetimeSeconds, strike, null, WeaponPose.DEFAULT, 3); }
    public MeleeArcProfile(ResourceLocation texture, Animation animation, boolean fadeOut, double lifetimeSeconds) { this(texture, animation, fadeOut, lifetimeSeconds, null, null, WeaponPose.DEFAULT, 3); }
    public enum Animation { NONE, THRUST, SLASH }

    /** Minecraft presentation adaptation: orientation of the held icon, not attack trajectory. */
    public record WeaponPose(double forwardDegrees, double obliqueDegrees, double scale) {
        public WeaponPose(double forwardDegrees, double obliqueDegrees) { this(forwardDegrees, obliqueDegrees, 1.0); }
        public static final WeaponPose DEFAULT = new WeaponPose(45.0, 20.0, 1.0);
        public static final Codec<WeaponPose> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("forward_degrees", 45.0).forGetter(WeaponPose::forwardDegrees),
                Codec.DOUBLE.optionalFieldOf("oblique_degrees", 20.0).forGetter(WeaponPose::obliqueDegrees),
                Codec.doubleRange(0.1, 4.0).optionalFieldOf("scale", 1.0).forGetter(WeaponPose::scale)
        ).apply(i, WeaponPose::new));
        public WeaponPose {
            if (!Double.isFinite(forwardDegrees) || forwardDegrees < -180 || forwardDegrees > 180)
                throw new IllegalArgumentException("weapon forward angle must be finite and between -180 and 180 degrees");
            if (!Double.isFinite(obliqueDegrees) || obliqueDegrees < 0 || obliqueDegrees > 45)
                throw new IllegalArgumentException("weapon oblique angle must be finite and between 0 and 45 degrees");
            if (!Double.isFinite(scale) || scale < 0.1 || scale > 4.0) throw new IllegalArgumentException("weapon scale must be between 0.1 and 4.0");
        }
    }

    public static final Codec<MeleeArcProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(MeleeArcProfile::texture),
            Codec.STRING.xmap(Animation::valueOf, Enum::name).optionalFieldOf("animation", Animation.NONE).forGetter(MeleeArcProfile::animation),
            Codec.BOOL.optionalFieldOf("fade_out", true).forGetter(MeleeArcProfile::fadeOut),
            Codec.DOUBLE.optionalFieldOf("lifetime_seconds", 2.0).forGetter(MeleeArcProfile::lifetimeSeconds),
            MeleeStrikeProfile.CODEC.optionalFieldOf("strike").forGetter(p -> java.util.Optional.ofNullable(p.strike())),
            ResourceLocation.CODEC.optionalFieldOf("disarm_texture").forGetter(p -> java.util.Optional.ofNullable(p.disarmTexture())),
            WeaponPose.CODEC.optionalFieldOf("weapon_pose", WeaponPose.DEFAULT).forGetter(MeleeArcProfile::weaponPose),
            Codec.intRange(1, 32).optionalFieldOf("disarm_frames", 3).forGetter(MeleeArcProfile::disarmFrames),
            Codec.BOOL.optionalFieldOf("strike_texture_glyph", false).forGetter(MeleeArcProfile::strikeTextureGlyph),
            Codec.intRange(1, 64).optionalFieldOf("texture_frames", 1).forGetter(MeleeArcProfile::textureFrames),
            Codec.intRange(1, 64).optionalFieldOf("texture_columns", 1).forGetter(MeleeArcProfile::textureColumns)
    ).apply(i, (texture, animation, fadeOut, lifetime, strike, disarm, weaponPose, frames, textureGlyph, textureFrames, textureColumns) -> new MeleeArcProfile(texture, animation, fadeOut, lifetime, strike.orElse(null), disarm.orElse(null), weaponPose, frames, textureGlyph, textureFrames, textureColumns)));

    public MeleeArcProfile {
        if (texture == null || animation == null || weaponPose == null) throw new IllegalArgumentException("arc texture, animation, and weapon pose are required");
        if (disarmFrames < 1 || disarmFrames > 32 || textureFrames < 1 || textureFrames > 64 || textureColumns < 1 || textureColumns > textureFrames) throw new IllegalArgumentException("invalid melee texture frame layout");
        if (!Double.isFinite(lifetimeSeconds) || lifetimeSeconds <= 0) throw new IllegalArgumentException("arc lifetime must be finite and positive");
    }
}
