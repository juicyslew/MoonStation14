package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VentilationFixtureSavedDataTest {
    @Test void roundTripPreservesInitializationAndExactIdentityGuard() {
        var data = new VentilationFixtureSavedData();
        var origin = new BlockPos(4, 5, 6);
        var scrubber = UUID.randomUUID(); var vent = UUID.randomUUID(); var alarm = UUID.randomUUID();
        assertTrue(data.recordBuilt(origin, scrubber, vent, alarm));
        assertFalse(data.markInitialized(origin, scrubber, vent, UUID.randomUUID()));
        assertFalse(data.entry(origin).orElseThrow().initialized());
        assertTrue(data.markInitialized(origin, scrubber, vent, alarm));
        assertFalse(data.markInitialized(origin, scrubber, vent, alarm));
        var saved = data.save(new CompoundTag(), null);
        assertEquals(net.minecraft.nbt.Tag.TAG_LIST, saved.getTagType("fixtures"));
        var loaded = VentilationFixtureSavedData.load(saved, null);
        assertEquals(data.entry(origin), loaded.entry(origin));
        assertEquals(scrubber, loaded.entry(origin).orElseThrow().scrubberUUID());
    }

    @Test void rejectsDuplicateOriginsIdsAndSeventeenthFixture() {
        var data = new VentilationFixtureSavedData();
        UUID[] ids = new UUID[3];
        for (int i = 0; i < VentilationFixtureSavedData.MAX_FIXTURES; i++) {
            ids[0] = UUID.randomUUID(); ids[1] = UUID.randomUUID(); ids[2] = UUID.randomUUID();
            assertTrue(data.recordBuilt(new BlockPos(i, 0, 0), ids[0], ids[1], ids[2]));
        }
        assertFalse(data.recordBuilt(new BlockPos(0, 0, 0), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        assertFalse(data.recordBuilt(new BlockPos(20, 0, 0), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        var corrupt = data.save(new CompoundTag(), null);
        var list = (net.minecraft.nbt.ListTag) corrupt.get("fixtures");
        list.add(list.get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> VentilationFixtureSavedData.load(corrupt, null));
    }
}
