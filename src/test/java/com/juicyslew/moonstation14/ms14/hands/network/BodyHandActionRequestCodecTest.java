package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyHandActionRequestCodecTest {
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    @Test void framedAndWorldThrowTargetsRoundTrip() {
        for (var target : new CapturedHandTarget[]{CapturedHandTarget.world(new Vec3(1.25, -2, 3)),
                CapturedHandTarget.frame(new Vec3(4, 5, 6), UUID.randomUUID(), new BlockPos(7, 8, 9))}) {
            var request = BodyHandActionRequest.throwItem("left", 1, 2, 3, "token", target);
            var buf = buffer(); BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf)); assertFalse(buf.isReadable());
        }
    }
    @Test void rejectsMissingTruncatedInvalidAndTrailingTargetPayloads() {
        var request = BodyHandActionRequest.throwItem("left", 1, 2, 3, "token", CapturedHandTarget.world(new Vec3(1, 2, 3)));
        var complete = buffer(); BodyHandActionRequest.STREAM_CODEC.encode(complete, request);
        byte[] bytes = new byte[complete.readableBytes()]; complete.getBytes(complete.readerIndex(), bytes);
        var missing = buffer(); missing.writeBytes(bytes, 0, bytes.length - 25);
        assertThrows(RuntimeException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(missing));
        var truncated = buffer(); truncated.writeBytes(bytes, 0, bytes.length - 1);
        assertThrows(RuntimeException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(truncated));
        var nan = buffer(); nan.writeBytes(bytes); nan.setDouble(nan.readerIndex() + 15, Double.NaN);
        assertThrows(RuntimeException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(nan));
        var extra = buffer(); extra.writeBytes(bytes); extra.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(extra));
    }
    @Test void onlyThrowMayHaveTarget() {
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(BodyHandActionRequest.Action.USE_HELD,
                "left", 1, 1, 0, null, "token", null, -1, -1, -1, -1, -1, -1, null, null, null, null,
                CapturedHandTarget.world(Vec3.ZERO)));
    }
    @Test void dropTargetIsOptionalAndRoundTripsWorldAndFrameCoordinates() {
        for (var target : new CapturedHandTarget[]{null, CapturedHandTarget.world(new Vec3(1, 2, 3)),
                CapturedHandTarget.frame(new Vec3(.5, 1, .5), UUID.randomUUID(), new BlockPos(0, 1, 0))}) {
            var request = new BodyHandActionRequest(BodyHandActionRequest.Action.DROP, "left", 1, 1, 0,
                    null, "token", null, -1, -1, -1, -1, -1, -1, null, null, null, null, target);
            var buf = buffer(); BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf)); assertFalse(buf.isReadable());
        }
        var plain = new BodyHandActionRequest(BodyHandActionRequest.Action.DROP, "left", 1, 1, 0, null, "token");
        var buf = buffer(); BodyHandActionRequest.STREAM_CODEC.encode(buf, plain);
        assertEquals(plain, BodyHandActionRequest.STREAM_CODEC.decode(buf));
    }
}
