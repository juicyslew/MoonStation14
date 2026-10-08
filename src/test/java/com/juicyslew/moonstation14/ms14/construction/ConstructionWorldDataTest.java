package com.juicyslew.moonstation14.ms14.construction;

import com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionWorldDataTest {
    @Test void persistenceRetainsGoalDivergenceAndStepProgress() {
        var original = new ConstructionWorldData.Record(new BlockPos(4, 65, -2), UUID.randomUUID(),
                "moonstation14:steel_wall_girder_block", "moonstation14:steel_wall", "wall", "girder", 1, 0, false);
        var restored = ConstructionWorldData.decodeRecord(ConstructionWorldData.encodeRecord(original));
        assertEquals(original, restored);
        assertFalse(restored.anchored());
        assertFalse(original.complete());
    }
}
