package com.juicyslew.moonstation14.ms14.hands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Non-owning inventory key for one live entity. It intentionally carries no entity state. */
public record PortableBodyReference(ResourceLocation dimension, UUID body, UUID token) {
    public static final Codec<PortableBodyReference> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(PortableBodyReference::dimension),
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("body").forGetter(value -> value.body()),
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("token").forGetter(value -> value.token())
    ).apply(instance, PortableBodyReference::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortableBodyReference> STREAM_CODEC =
            StreamCodec.of((buffer, value) -> {
                buffer.writeResourceLocation(value.dimension());
                buffer.writeUUID(value.body());
                buffer.writeUUID(value.token());
            }, buffer -> new PortableBodyReference(buffer.readResourceLocation(), buffer.readUUID(), buffer.readUUID()));

    public PortableBodyReference {
        if (dimension == null || body == null || token == null) throw new IllegalArgumentException("Invalid portable body key");
    }
}
