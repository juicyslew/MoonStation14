package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Prototype vocal capability; does not replace the existing generic voice adaptation. */
public record VocalComponent(String screamAction, String screamEmote, ResourceLocation screamSound,
                             float screamPitchVariation, float wilhelmProbability) implements CharacterComponent {
    public static final String TYPE = "Vocal";
    public static final String DEFAULT_ACTION = "moonstation14:scream";
    public static final String DEFAULT_EMOTE = "scream";
    public static final ResourceLocation DEFAULT_SOUND = ResourceLocation.fromNamespaceAndPath("moonstation14", "vocal_human_scream");
    public static final float DEFAULT_PITCH_VARIATION = .125f;
    public static final float DEFAULT_WILHELM_PROBABILITY = .0002f;
    public VocalComponent {
        if (screamAction == null || screamAction.isBlank() || screamEmote == null || screamEmote.isBlank())
            throw new IllegalArgumentException("Vocal action and emote are required");
        Objects.requireNonNull(screamSound, "screamSound");
        if (!Float.isFinite(screamPitchVariation) || screamPitchVariation < 0 || screamPitchVariation > 1
                || !Float.isFinite(wilhelmProbability) || wilhelmProbability < 0 || wilhelmProbability > 1)
            throw new IllegalArgumentException("Vocal values must be finite and between 0 and 1");
    }
    public static final Codec<VocalComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> TYPE),
            Codec.STRING.optionalFieldOf("screamAction", DEFAULT_ACTION).forGetter(VocalComponent::screamAction),
            Codec.STRING.optionalFieldOf("screamEmote", DEFAULT_EMOTE).forGetter(VocalComponent::screamEmote),
            ResourceLocation.CODEC.optionalFieldOf("screamSound", DEFAULT_SOUND).forGetter(VocalComponent::screamSound),
            Codec.floatRange(0f, 1f).optionalFieldOf("screamPitchVariation", DEFAULT_PITCH_VARIATION).forGetter(VocalComponent::screamPitchVariation),
            Codec.floatRange(0f, 1f).optionalFieldOf("wilhelmProbability", DEFAULT_WILHELM_PROBABILITY).forGetter(VocalComponent::wilhelmProbability)
    ).apply(instance, (type, action, emote, sound, pitch, wilhelm) -> new VocalComponent(action, emote, sound, pitch, wilhelm)));

    @Override public String type() { return TYPE; }
}
