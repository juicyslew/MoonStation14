package com.juicyslew.moonstation14.ms14.radiation.control;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Context-verb nomination. Eject values are optimistic concurrency expectations, not authority. */
public record RadiationCollectorControlRequest(long requestSequence, Operation operation,
        BodyBlockUseRequest proof, EjectExpectation ejectExpectation) implements CustomPacketPayload {
    public enum Operation { QUERY, EJECT }
    public record EjectExpectation(java.util.UUID placementId, ResourceLocation profileId,
                                   long slotRevision, long ledgerRevision) {
        public EjectExpectation {
            if (placementId == null || profileId == null || slotRevision < 0 || ledgerRevision < 0)
                throw new IllegalArgumentException("Invalid eject expectation");
        }
    }
    public static final Type<RadiationCollectorControlRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "radiation_collector_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationCollectorControlRequest> STREAM_CODEC = StreamCodec.of((b,r) -> {
        b.writeVarLong(r.requestSequence); b.writeEnum(r.operation); BodyBlockUseRequest.STREAM_CODEC.encode(b,r.proof);
        b.writeBoolean(r.ejectExpectation != null);
        if (r.ejectExpectation != null) { b.writeUUID(r.ejectExpectation.placementId); b.writeResourceLocation(r.ejectExpectation.profileId); b.writeVarLong(r.ejectExpectation.slotRevision); b.writeVarLong(r.ejectExpectation.ledgerRevision); }
    }, b -> new RadiationCollectorControlRequest(b.readVarLong(), b.readEnum(Operation.class), BodyBlockUseRequest.STREAM_CODEC.decode(b),
            b.readBoolean() ? new EjectExpectation(b.readUUID(), b.readResourceLocation(), b.readVarLong(), b.readVarLong()) : null));
    public RadiationCollectorControlRequest {
        if (requestSequence <= 0 || operation == null || proof == null || (operation == Operation.EJECT) != (ejectExpectation != null))
            throw new IllegalArgumentException("Invalid collector control request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
