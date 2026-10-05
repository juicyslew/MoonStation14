package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JugGridSnapshotReagentCodecTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    @Test void jugReagentDataRoundTripsForBodyAndWorldGridEntries() {
        var water = ModReagents.createKey("water");
        var component = new ReagentComponent(Map.of(water, 75f));
        var gridEntry = new BodyHandStateSnapshot.GridEntry("jug-token", "moonstation14:jug", 1, 0, 0, 0, component);
        var body = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 1, 0, "left",
                List.of(new BodyHandStateSnapshot.Hand("left", "bag-token", "moonstation14:bag", 1),
                        new BodyHandStateSnapshot.Hand("right", null, null, 0)), List.of(),
                List.of(new BodyHandStateSnapshot.GridHost("bag-token", "bag", 7, 4, List.of(gridEntry))));
        var bodyBuf = buffer(); BodyHandStateSnapshot.STREAM_CODEC.encode(bodyBuf, body);
        var decodedBody = BodyHandStateSnapshot.STREAM_CODEC.decode(bodyBuf);
        var decodedBodyEntry = decodedBody.grids().getFirst().entries().getFirst();
        assertEquals(component, decodedBodyEntry.reagent());
        assertEquals(7_500L, decodedBodyEntry.reagent().centContents().get(water));
        assertEquals(gridEntry.token(), decodedBodyEntry.token());
        assertEquals(gridEntry.itemId(), decodedBodyEntry.itemId());
        assertEquals(gridEntry.count(), decodedBodyEntry.count());
        assertEquals(gridEntry.x(), decodedBodyEntry.x());
        assertEquals(gridEntry.y(), decodedBodyEntry.y());
        assertEquals(gridEntry.rotation(), decodedBodyEntry.rotation());

        var worldEntry = new BodyWorldGridSnapshot.Entry("jug-token", "moonstation14:jug", 1, 0, 0, 0, component);
        var world = new BodyWorldGridSnapshot(1, 1, BodyWorldGridSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 1, 0, UUID.randomUUID(), "bag", 7, 4,
                List.of(worldEntry), UUID.randomUUID(), 0);
        var worldBuf = buffer(); BodyWorldGridSnapshot.STREAM_CODEC.encode(worldBuf, world);
        var decodedWorldEntry = BodyWorldGridSnapshot.STREAM_CODEC.decode(worldBuf).entries().getFirst();
        assertEquals(component, decodedWorldEntry.reagent());
        assertEquals(7_500L, decodedWorldEntry.reagent().centContents().get(water));
        assertEquals(worldEntry.token(), decodedWorldEntry.token());
        assertEquals(worldEntry.itemId(), decodedWorldEntry.itemId());
        assertEquals(worldEntry.count(), decodedWorldEntry.count());
        assertEquals(worldEntry.x(), decodedWorldEntry.x());
        assertEquals(worldEntry.y(), decodedWorldEntry.y());
        assertEquals(worldEntry.rotation(), decodedWorldEntry.rotation());
    }
}
