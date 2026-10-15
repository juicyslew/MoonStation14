package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyReference;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Persisted recovery hint only; the loaded root ledger remains authoritative for custody. */
public record PortableBodyContainmentRelation(PortableBodyReference reference, UUID rootId,
        BodyContainmentService.ContainedBodyFlags originalFlags) {
    public static final Codec<PortableBodyContainmentRelation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PortableBodyReference.CODEC.fieldOf("reference").forGetter(PortableBodyContainmentRelation::reference),
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("root").forGetter(PortableBodyContainmentRelation::rootId),
            Codec.BOOL.fieldOf("invisible").forGetter(value -> value.originalFlags().invisible()),
            Codec.BOOL.fieldOf("noPhysics").forGetter(value -> value.originalFlags().noPhysics()),
            Codec.BOOL.fieldOf("noGravity").forGetter(value -> value.originalFlags().noGravity())
    ).apply(instance, (reference, root, invisible, noPhysics, noGravity) -> new PortableBodyContainmentRelation(
            reference, root, new BodyContainmentService.ContainedBodyFlags(invisible, noPhysics, noGravity))));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortableBodyContainmentRelation> STREAM_CODEC =
            StreamCodec.of((buffer, value) -> {
                PortableBodyReference.STREAM_CODEC.encode(buffer, value.reference());
                buffer.writeUUID(value.rootId());
                buffer.writeBoolean(value.originalFlags().invisible());
                buffer.writeBoolean(value.originalFlags().noPhysics());
                buffer.writeBoolean(value.originalFlags().noGravity());
            }, buffer -> new PortableBodyContainmentRelation(PortableBodyReference.STREAM_CODEC.decode(buffer),
                    buffer.readUUID(), new BodyContainmentService.ContainedBodyFlags(buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean())));

    public PortableBodyContainmentRelation {
        java.util.Objects.requireNonNull(reference);
        java.util.Objects.requireNonNull(rootId);
        java.util.Objects.requireNonNull(originalFlags);
    }
}
