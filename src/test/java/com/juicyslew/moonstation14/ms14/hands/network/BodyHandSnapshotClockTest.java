package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyHandSnapshotClockTest {
    @Test void streamCodecPreservesServerTickAndAuthorityBinding() {
        var account = UUID.randomUUID();
        var body = UUID.randomUUID();
        var source = new BodyHandStateSnapshot(0, 17, BodyHandStateSnapshot.Reason.OK, account, body,
                42, 9, "left", List.of(new BodyHandStateSnapshot.Hand("left", null, null, 0)),
                List.of(), List.of(), false, null, 987654);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        try {
            BodyHandStateSnapshot.STREAM_CODEC.encode(buffer, source);
            var decoded = BodyHandStateSnapshot.STREAM_CODEC.decode(buffer);
            assertEquals(987654, decoded.serverTick());
            assertEquals(BodyHandStateSnapshot.Reason.OK, decoded.reason());
            assertEquals(account, decoded.accountId());
            assertEquals(body, decoded.bodyId());
            assertEquals(42, decoded.epoch());
        } finally { buffer.release(); }
    }
}
