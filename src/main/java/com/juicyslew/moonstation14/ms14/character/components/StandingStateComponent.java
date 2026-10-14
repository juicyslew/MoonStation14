package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Local standing/prone eligibility policy; does not implement standing mechanics. */
public record StandingStateComponent(boolean standingEligible, boolean proneEligible, ResourceLocation downSound) implements CharacterComponent {
    public static final String TYPE = "StandingState";
    public static final ResourceLocation DEFAULT_DOWN_SOUND = ResourceLocation.parse("moonstation14:body_fall");
    public StandingStateComponent(boolean standingEligible, boolean proneEligible) {
        this(standingEligible, proneEligible, DEFAULT_DOWN_SOUND);
    }
    public static final Codec<StandingStateComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(StandingStateComponent::type),
            Codec.BOOL.fieldOf("standing_eligible").forGetter(StandingStateComponent::standingEligible),
            Codec.BOOL.fieldOf("prone_eligible").forGetter(StandingStateComponent::proneEligible),
            ResourceLocation.CODEC.optionalFieldOf("down_sound", DEFAULT_DOWN_SOUND).forGetter(StandingStateComponent::downSound)
    ).apply(instance, (type, standing, prone, downSound) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new StandingStateComponent(standing, prone, downSound);
    }));

    @Override public String type() { return TYPE; }
}

