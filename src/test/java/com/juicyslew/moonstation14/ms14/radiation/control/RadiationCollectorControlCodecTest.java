package com.juicyslew.moonstation14.ms14.radiation.control;

import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RadiationCollectorControlCodecTest {
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    private static BodyBlockUseRequest proof() { return new BodyBlockUseRequest(2, 7, 4, "main", null, new BlockPos(1,2,3), Direction.UP, false); }
    @Test void queryAndEjectRoundTrip() {
        var query = new RadiationCollectorControlRequest(1, RadiationCollectorControlRequest.Operation.QUERY, proof(), null);
        var b = buffer(); RadiationCollectorControlRequest.STREAM_CODEC.encode(b, query); assertEquals(query, RadiationCollectorControlRequest.STREAM_CODEC.decode(b));
        var eject = new RadiationCollectorControlRequest(2, RadiationCollectorControlRequest.Operation.EJECT, proof(),
                new RadiationCollectorControlRequest.EjectExpectation(UUID.randomUUID(), ResourceLocation.withDefaultNamespace("collector"), 8, 9));
        b = buffer(); RadiationCollectorControlRequest.STREAM_CODEC.encode(b, eject); assertEquals(eject, RadiationCollectorControlRequest.STREAM_CODEC.decode(b));
    }
    @Test void snapshotRoundTripsAndRejectsInvalidRequestSequence() {
        var value = new RadiationCollectorControlSnapshot(3, UUID.randomUUID(), 5, ResourceLocation.withDefaultNamespace("overworld"), new BlockPos(1,2,3), UUID.randomUUID(), ResourceLocation.withDefaultNamespace("collector"), 6, 7, true, true);
        var b = buffer(); RadiationCollectorControlSnapshot.STREAM_CODEC.encode(b, value); assertEquals(value, RadiationCollectorControlSnapshot.STREAM_CODEC.decode(b));
        assertThrows(IllegalArgumentException.class, () -> new RadiationCollectorControlSnapshot(0, value.bodyId(), 5, value.dimension(), value.position(), value.placementId(), value.profileId(), 6, 7, true, false));
    }
    @Test void queryCannotCarryEjectExpectation() {
        assertThrows(IllegalArgumentException.class, () -> new RadiationCollectorControlRequest(1, RadiationCollectorControlRequest.Operation.QUERY, proof(), new RadiationCollectorControlRequest.EjectExpectation(UUID.randomUUID(), ResourceLocation.withDefaultNamespace("collector"), 0, 0)));
    }
}
