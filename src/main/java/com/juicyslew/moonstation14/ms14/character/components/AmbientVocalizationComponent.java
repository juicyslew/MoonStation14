package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Local autonomous-NPC ambience policy, composed independently from speech capability. */
public record AmbientVocalizationComponent(int minimumIntervalTicks, int maximumIntervalTicks,
                                           float volume) implements CharacterComponent {
    public static final String TYPE = "AmbientVocalization";
    public static final AmbientVocalizationComponent DEFAULT = new AmbientVocalizationComponent(160, 400, 0.4f);
    public static final Codec<AmbientVocalizationComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(AmbientVocalizationComponent::type),
            Codec.INT.optionalFieldOf("minimumIntervalTicks", DEFAULT.minimumIntervalTicks)
                    .forGetter(AmbientVocalizationComponent::minimumIntervalTicks),
            Codec.INT.optionalFieldOf("maximumIntervalTicks", DEFAULT.maximumIntervalTicks)
                    .forGetter(AmbientVocalizationComponent::maximumIntervalTicks),
            Codec.FLOAT.optionalFieldOf("volume", DEFAULT.volume).forGetter(AmbientVocalizationComponent::volume)
    ).apply(instance, (type, minimum, maximum, volume) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new AmbientVocalizationComponent(minimum, maximum, volume);
    }));

    public AmbientVocalizationComponent {
        if (minimumIntervalTicks < 1 || maximumIntervalTicks < minimumIntervalTicks
                || !Float.isFinite(volume) || volume <= 0 || volume > 1)
            throw new IllegalArgumentException("invalid ambient vocalization policy");
    }

    @Override public String type() { return TYPE; }
}
