package com.juicyslew.moonstation14.ms14.radiation.control;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Authoritative collector context snapshot; accepted=false must not open a menu. */
public record RadiationCollectorControlSnapshot(long requestSequence, UUID bodyId, long authorityEpoch,
        ResourceLocation dimension, BlockPos position, UUID placementId, ResourceLocation profileId,
        long slotRevision, long ledgerRevision, boolean ejectAvailable, boolean accepted) implements CustomPacketPayload {
    public static final Type<RadiationCollectorControlSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "radiation_collector_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationCollectorControlSnapshot> STREAM_CODEC = StreamCodec.of((b,s) -> {
        b.writeVarLong(s.requestSequence); b.writeUUID(s.bodyId); b.writeVarLong(s.authorityEpoch); b.writeResourceLocation(s.dimension); b.writeBlockPos(s.position);
        b.writeUUID(s.placementId); b.writeResourceLocation(s.profileId); b.writeVarLong(s.slotRevision); b.writeVarLong(s.ledgerRevision); b.writeBoolean(s.ejectAvailable); b.writeBoolean(s.accepted);
    }, b -> new RadiationCollectorControlSnapshot(b.readVarLong(), b.readUUID(), b.readVarLong(), b.readResourceLocation(), b.readBlockPos(), b.readUUID(), b.readResourceLocation(), b.readVarLong(), b.readVarLong(), b.readBoolean(), b.readBoolean()));
    public RadiationCollectorControlSnapshot {
        if (requestSequence <= 0 || bodyId == null || authorityEpoch <= 0 || dimension == null || position == null || placementId == null || profileId == null || slotRevision < 0 || ledgerRevision < 0)
            throw new IllegalArgumentException("Invalid collector snapshot");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
