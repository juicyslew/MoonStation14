package com.juicyslew.moonstation14.ms14.atmos.tank;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-authenticated canister action. Client registration is intentionally a separate integration step. */
public record CanisterControlRequest(UUID sessionId, UUID machineId, UUID bodyId, long authorityEpoch,
        ResourceLocation dimension, double x, double y, double z, long sequence,
        long stateRevision, long slotRevision, long ledgerRevision, long handRevision, String handId,
        String handToken, Operation operation,
        boolean valveClosed, double pressureKpa) implements CustomPacketPayload {
    public enum Operation { QUERY, SET_VALVE, SET_PRESSURE, INSERT_ACTIVE, EJECT }
    public static final Type<CanisterControlRequest> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "canister_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CanisterControlRequest> STREAM_CODEC = StreamCodec.of(CanisterWireCodec::writeRequest, CanisterWireCodec::readRequest);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

