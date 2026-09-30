package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Local standing/prone eligibility policy; does not implement standing mechanics. */
public record StandingStateComponent(boolean standingEligible, boolean proneEligible) implements CharacterComponent {
    public static final String TYPE = "StandingState";
    public static final Codec<StandingStateComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(StandingStateComponent::type),
            Codec.BOOL.fieldOf("standing_eligible").forGetter(StandingStateComponent::standingEligible),
            Codec.BOOL.fieldOf("prone_eligible").forGetter(StandingStateComponent::proneEligible)
    ).apply(instance, (type, standing, prone) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new StandingStateComponent(standing, prone);
    }));

    @Override public String type() { return TYPE; }
}
