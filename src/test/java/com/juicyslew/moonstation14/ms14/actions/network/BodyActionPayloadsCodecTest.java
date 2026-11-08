package com.juicyslew.moonstation14.ms14.actions.network;

import com.juicyslew.moonstation14.ms14.actions.*;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BodyActionPayloadsCodecTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, T> codec, T value) {
        var buf = buffer(); codec.encode(buf, value); return codec.decode(buf);
    }

    @Test void queryAndActivationRoundTrip() {
        var query = roundTrip(BodyActionPayloads.Query.STREAM_CODEC, new BodyActionPayloads.Query());
        assertEquals(BodyActionPayloads.Query.TYPE, query.type());
        var request = new BodyActionRequest(UUID.randomUUID(), 3, 12, ResourceLocation.parse("moonstation14:toggle"), "provider", 7);
        assertEquals(request, roundTrip(BodyActionPayloads.Activate.STREAM_CODEC, new BodyActionPayloads.Activate(request)).request());
        assertEquals("moonstation14:body_action_activate", BodyActionPayloads.Activate.TYPE.id().toString());
    }

    @Test void snapshotClearAndPopulatedSnapshotRoundTrip() {
        assertEquals(Optional.empty(), roundTrip(BodyActionPayloads.Snapshot.STREAM_CODEC, new BodyActionPayloads.Snapshot(Optional.empty())).snapshot());
        var snapshot = sampleSnapshot();
        assertEquals(snapshot, roundTrip(BodyActionPayloads.Snapshot.STREAM_CODEC, new BodyActionPayloads.Snapshot(Optional.of(snapshot))).snapshot().orElseThrow());
        assertEquals("moonstation14:body_action_query", BodyActionPayloads.Query.TYPE.id().toString());
        assertEquals("moonstation14:body_action_snapshot", BodyActionPayloads.Snapshot.TYPE.id().toString());
    }

    @Test void acceptedAndDeniedResultRoundTrip() {
        var ok = new BodyActionResult(BodyActionResult.Status.ACCEPTED, "", 8, Optional.of(sampleSnapshot()));
        var denied = new BodyActionResult(BodyActionResult.Status.DENIED, "not available", 9, Optional.empty());
        assertEquals(ok, roundTrip(BodyActionPayloads.Result.STREAM_CODEC, new BodyActionPayloads.Result(ok)).result());
        assertEquals(denied, roundTrip(BodyActionPayloads.Result.STREAM_CODEC, new BodyActionPayloads.Result(denied)).result());
        assertEquals("moonstation14:body_action_result", BodyActionPayloads.Result.TYPE.id().toString());
    }

    @Test void oversizedSnapshotActionCountIsRejectedBeforeEntriesAreRead() {
        var buf = buffer();
        buf.writeBoolean(true); buf.writeUUID(UUID.randomUUID()); buf.writeVarLong(1); buf.writeVarLong(1); buf.writeVarLong(0);
        buf.writeVarInt(257);
        assertThrows(IllegalArgumentException.class, () -> BodyActionPayloads.Snapshot.STREAM_CODEC.decode(buf));
    }

    private static BodyActionSnapshot sampleSnapshot() {
        return new BodyActionSnapshot(UUID.randomUUID(), 4, 15, 100,
                List.of(new BodyActionSnapshot.Entry(ResourceLocation.parse("moonstation14:instant"), "provider-a", "Instant", ResourceLocation.parse("minecraft:stone"), Optional.empty(), ActionPrototype.Kind.INSTANT, true, false, 104, 5, "", 2),
                        new BodyActionSnapshot.Entry(ResourceLocation.parse("moonstation14:toggle"), "provider-b", "Toggle", ResourceLocation.parse("minecraft:iron_ingot"), Optional.of(ResourceLocation.parse("minecraft:gold_ingot")), ActionPrototype.Kind.TOGGLE, false, true, 120, 20, "cooldown", 7)));
    }
}
