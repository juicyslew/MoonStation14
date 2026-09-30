package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Prototype bootstrap declaration only; runtime grants/revocations live in ComplexInteractionAttachment. */
public record ComplexInteractionComponent() implements CharacterComponent {
    public static final String TYPE = "ComplexInteraction";
    public static final Codec<ComplexInteractionComponent> CODEC = Codec.STRING.fieldOf("type").codec()
            .comapFlatMap(type -> TYPE.equals(type) ? DataResult.success(new ComplexInteractionComponent())
                    : DataResult.error(() -> "unknown component type: " + type), ignored -> TYPE);

    @Override public String type() { return TYPE; }
}
