package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Bounded request for a point-in-time aimed world grid observation. */
public record BodyWorldGridQuery(long epoch, long sequence, UUID target, long handRevision, UUID session) implements CustomPacketPayload {
    public static final Type<BodyWorldGridQuery> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_grid_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldGridQuery> STREAM_CODEC = StreamCodec.of(
            (b, q) -> { b.writeVarLong(q.epoch); b.writeVarLong(q.sequence); b.writeUUID(q.target); b.writeVarLong(q.handRevision); b.writeBoolean(q.session != null); if (q.session != null) b.writeUUID(q.session); },
            b -> {
                long epoch = b.readVarLong(), sequence = b.readVarLong(); UUID target = b.readUUID();
                long revision = b.readVarLong(); int flag = b.readUnsignedByte();
                if (flag > 1) throw new IllegalArgumentException("Invalid session flag");
                UUID session = flag == 1 ? b.readUUID() : null;
                if (b.isReadable()) throw new IllegalArgumentException("Trailing world grid query data");
                return new BodyWorldGridQuery(epoch, sequence, target, revision, session);
            });
    public BodyWorldGridQuery(long epoch, long sequence, UUID target, long handRevision) {
        this(epoch, sequence, target, handRevision, null);
    }
    public BodyWorldGridQuery {
        if (epoch <= 0 || sequence <= 0 || target == null || handRevision < 0) throw new IllegalArgumentException("Invalid world grid query");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

