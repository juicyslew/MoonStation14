package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.ms14.equipment.ProtectedFromStepTriggers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Character composition marker for immunity to configured step triggers. */
public record ProtectedFromStepTriggersComponent(ProtectedFromStepTriggers profile) implements CharacterComponent {
    public static final String TYPE = "ProtectedFromStepTriggers";
    public static final Codec<ProtectedFromStepTriggersComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(ProtectedFromStepTriggersComponent::type),
            ProtectedFromStepTriggers.CODEC.optionalFieldOf("profile", ProtectedFromStepTriggers.DEFAULT).forGetter(ProtectedFromStepTriggersComponent::profile)
    ).apply(i, (type, profile) -> { if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type); return new ProtectedFromStepTriggersComponent(profile); }));
    @Override public String type() { return TYPE; }
}
