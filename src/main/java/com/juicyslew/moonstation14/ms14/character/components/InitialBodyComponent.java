package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.ms14.organ.OrganCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;

/** Initial organ prototype IDs by upstream organ category on the mob. */
public record InitialBodyComponent(Map<OrganCategory, ResourceLocation> organs) implements CharacterComponent {
    public static final String TYPE = "InitialBody";
    public static final Codec<InitialBodyComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(InitialBodyComponent::type),
            Codec.unboundedMap(OrganCategory.CODEC, ResourceLocation.CODEC).fieldOf("organs")
                    .forGetter(InitialBodyComponent::organs)
    ).apply(instance, (type, organs) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new InitialBodyComponent(organs);
    }));

    public InitialBodyComponent { organs = Map.copyOf(Objects.requireNonNull(organs, "organs")); }
    @Override public String type() { return TYPE; }
}
