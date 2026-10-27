package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Immutable shared representation of a positional sound event and its gain/pitch variation. */
public record PositionalSoundCue(ResourceLocation event, float volumeDb, float pitchVariation) {
    public PositionalSoundCue(ResourceLocation event, float volumeDb) { this(event, volumeDb, 0f); }
    public static Codec<PositionalSoundCue> codec(float defaultVolumeDb, float defaultVariation) {
        return RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("event").forGetter(PositionalSoundCue::event),
                Codec.FLOAT.optionalFieldOf("volume_db", defaultVolumeDb).forGetter(PositionalSoundCue::volumeDb),
                Codec.FLOAT.optionalFieldOf("pitch_variation", defaultVariation).forGetter(PositionalSoundCue::pitchVariation)
        ).apply(i, PositionalSoundCue::new));
    }
    public PositionalSoundCue {
        if (event == null || !Float.isFinite(volumeDb) || !Float.isFinite(pitchVariation) || pitchVariation < 0f || pitchVariation > 1f)
            throw new IllegalArgumentException("invalid positional sound cue");
    }
    public float volume() { return (float) Math.pow(10, volumeDb / 20); }
    public PositionalSoundCue withVolume(float value) { return new PositionalSoundCue(event, value, pitchVariation); }
    public PositionalSoundCue withVariation(float value) { return new PositionalSoundCue(event, volumeDb, value); }
}
