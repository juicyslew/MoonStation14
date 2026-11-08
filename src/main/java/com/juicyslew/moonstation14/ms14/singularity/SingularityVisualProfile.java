package com.juicyslew.moonstation14.ms14.singularity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Resolved art/distortion parameters only; it is not a simulation or gameplay authority. */
public record SingularityVisualProfile(int level, float spriteScale, float intensity, float falloff,
                                       float cutoffWorldUnits, int frameWidthPixels, List<Double> frameDelaysSeconds,
                                        float lensStrength, float foregroundRampSpriteHalfExtents) {
    public static final float PIXELS_PER_METER = 32.0F;
    public SingularityVisualProfile(int level,float spriteScale,float intensity,float falloff,float cutoffWorldUnits,int frameWidthPixels) {
        this(level,spriteScale,intensity,falloff,cutoffWorldUnits,frameWidthPixels,java.util.Collections.nCopies(8,.1),1f,1f);
    }
    public SingularityVisualProfile(int level,float spriteScale,float intensity,float falloff,float cutoffWorldUnits,int frameWidthPixels,List<Double> delays) {
        this(level,spriteScale,intensity,falloff,cutoffWorldUnits,frameWidthPixels,delays,1f,1f);
    }
    public static final Codec<SingularityVisualProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("level").forGetter(SingularityVisualProfile::level),
            Codec.FLOAT.fieldOf("spriteScale").forGetter(SingularityVisualProfile::spriteScale),
            Codec.FLOAT.fieldOf("intensity").forGetter(SingularityVisualProfile::intensity),
            Codec.FLOAT.fieldOf("falloff").forGetter(SingularityVisualProfile::falloff),
            Codec.FLOAT.fieldOf("cutoffWorldUnits").forGetter(SingularityVisualProfile::cutoffWorldUnits),
            Codec.INT.fieldOf("frameWidthPixels").forGetter(SingularityVisualProfile::frameWidthPixels),
            Codec.DOUBLE.listOf().optionalFieldOf("frameDelaysSeconds", java.util.Collections.nCopies(8,.1))
                     .forGetter(SingularityVisualProfile::frameDelaysSeconds),
             Codec.FLOAT.optionalFieldOf("lensStrength",1f).forGetter(SingularityVisualProfile::lensStrength),
             Codec.FLOAT.optionalFieldOf("foregroundRampSpriteHalfExtents",1f).forGetter(SingularityVisualProfile::foregroundRampSpriteHalfExtents)
    ).apply(i, SingularityVisualProfile::new));

    public void validate() {
        if (level < 1 || level > 6 || !Float.isFinite(spriteScale) || spriteScale <= 0
                || !Float.isFinite(intensity) || intensity <= 0 || !Float.isFinite(falloff) || falloff <= 0
                || !Float.isFinite(cutoffWorldUnits) || cutoffWorldUnits <= 0 || frameWidthPixels <= 0
                || frameDelaysSeconds == null || frameDelaysSeconds.isEmpty()
                || frameDelaysSeconds.stream().anyMatch(delay -> delay == null || !Double.isFinite(delay) || delay <= 0)
                || !Float.isFinite(lensStrength) || lensStrength < 0
                || !Float.isFinite(foregroundRampSpriteHalfExtents) || foregroundRampSpriteHalfExtents <= 0)
            throw new IllegalArgumentException("invalid singularity visual profile");
    }
    /** SS14 EyeManager.PixelsPerMeter is 32; this retains source-frame size and profile scale. */
    public float spriteHalfExtent() { return frameWidthPixels / (2.0F * PIXELS_PER_METER) * spriteScale; }
    public float lensCutoffWorldUnits() { return cutoffWorldUnits; }
    public float foregroundRampWorldUnits() { return foregroundRampSpriteHalfExtents * spriteHalfExtent(); }
    public int frameIndex(double elapsedSeconds) {
        return SingularityFrameTiming.frameAt(elapsedSeconds, frameDelaysSeconds.stream().mapToDouble(Double::doubleValue).toArray());
    }
    public ResourceLocation frameTexture(double elapsedSeconds) {
        int frame=frameIndex(elapsedSeconds);
        return ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/singularity/animated/level_"
                + level + "/frame_" + String.format(java.util.Locale.ROOT,"%02d",frame) + ".png");
    }
}

