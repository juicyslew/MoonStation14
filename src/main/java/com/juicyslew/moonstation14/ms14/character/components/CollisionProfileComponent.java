package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** SS14-style collision mask/layer bitsets for a resolved character. */
public record CollisionProfileComponent(int mask, int layer) implements CharacterComponent {
    public static final String TYPE = "CollisionProfile";
    public static final Codec<CollisionProfileComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").xmap(type -> {
                if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
                return type;
            }, ignored -> TYPE).forGetter(CollisionProfileComponent::type),
            Codec.INT.fieldOf("mask").forGetter(CollisionProfileComponent::mask),
            Codec.INT.fieldOf("layer").forGetter(CollisionProfileComponent::layer)
    ).apply(i, (type, mask, layer) -> new CollisionProfileComponent(mask, layer)));
    @Override public String type() { return TYPE; }
    public boolean collidesWith(CollisionProfileComponent object) {
        return (mask & object.layer) != 0 || (object.mask & layer) != 0;
    }
}
