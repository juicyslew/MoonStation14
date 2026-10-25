package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Policy for granting observer capability when a dead body moves. */
public record GhostOnMoveComponent(boolean mustBeDead, boolean canReturn) implements CharacterComponent {
    public static final String TYPE = "GhostOnMove";
    private record Fields(String type, boolean mustBeDead, boolean canReturn) { }
    private static final Codec<Fields> FIELDS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(Fields::type),
            Codec.BOOL.optionalFieldOf("must_be_dead", true).forGetter(Fields::mustBeDead),
            Codec.BOOL.optionalFieldOf("can_return", true).forGetter(Fields::canReturn)
    ).apply(instance, Fields::new));
    public static final Codec<GhostOnMoveComponent> CODEC = FIELDS_CODEC.flatXmap(fields ->
            TYPE.equals(fields.type())
                    ? DataResult.success(new GhostOnMoveComponent(fields.mustBeDead(), fields.canReturn()))
                    : DataResult.error(() -> "unknown component type: " + fields.type()),
            component -> DataResult.success(new Fields(component.type(), component.mustBeDead(), component.canReturn())));

    @Override public String type() { return TYPE; }
}

