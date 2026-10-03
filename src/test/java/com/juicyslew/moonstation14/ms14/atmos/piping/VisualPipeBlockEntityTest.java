package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualPipeBlockEntityTest {
    @Test
    void migrationPreservesLegalMasksAndInvalidMasksFailClosed() {
        VisualPipeBlockEntity entity = new VisualPipeBlockEntity(net.minecraft.core.BlockPos.ZERO,
                com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState());
        entity.loadAdditional(new CompoundTag(), null);
        assertEquals(VisualPipeBlockEntity.PRIMARY, entity.getLayerMask());
        CompoundTag v1 = new CompoundTag();
        v1.putInt("visual_pipe_version", 1);
        for (int mask : new int[]{1, 3, 7}) {
            v1.putInt("visual_pipe_mask", mask);
            entity.loadAdditional(v1, null);
            assertEquals(mask, entity.getLayerMask());
        }
        for (int mask : new int[]{0, 2, 4, 6, 9}) {
            v1.putInt("visual_pipe_mask", mask);
            entity.loadAdditional(v1, null);
            assertEquals(VisualPipeBlockEntity.PRIMARY, entity.getLayerMask());
        }
        CompoundTag v2 = new CompoundTag();
        v2.putInt("visual_pipe_version", 2);
        v2.putInt("visual_pipe_mask", VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY);
        entity.loadAdditional(v2, null);
        assertEquals(VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY, entity.getLayerMask());
        for (int mask : new int[]{2, 4, 6}) {
            v2.putInt("visual_pipe_mask", mask);
            entity.loadAdditional(v2, null);
            assertEquals(mask, entity.getLayerMask());
        }
        v2.putInt("visual_pipe_mask", 0);
        entity.loadAdditional(v2, null);
        assertEquals(VisualPipeBlockEntity.PRIMARY, entity.getLayerMask());
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("visual_pipe_version", 3);
        unknown.putInt("visual_pipe_mask", 7);
        entity.loadAdditional(unknown, null);
        assertEquals(VisualPipeBlockEntity.PRIMARY, entity.getLayerMask());
    }

    @Test
    void identitiesAreInertAndMalformedDataFailsClosed() {
        VisualPipeBlockEntity entity = new VisualPipeBlockEntity(net.minecraft.core.BlockPos.ZERO,
                com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState());
        entity.loadAdditional(new CompoundTag(), null);
        assertTrue(entity.functionalIdentity(VisualPipeBlockEntity.PRIMARY).isEmpty());

        CompoundTag data = new CompoundTag();
        data.putInt("visual_pipe_version", 2);
        data.putInt("visual_pipe_mask", VisualPipeBlockEntity.SECONDARY);
        CompoundTag ids = new CompoundTag();
        ids.putInt("version", 1);
        ListTag lanes = new ListTag();
        CompoundTag lane = new CompoundTag();
        lane.putInt("layer", VisualPipeBlockEntity.SECONDARY);
        lane.putString("id", "123e4567-e89b-12d3-a456-426614174000");
        lanes.add(lane);
        ids.put("lanes", lanes);
        data.put("pipe_functional_ids", ids);
        entity.loadAdditional(data, null);
        assertTrue(entity.functionalIdentity(VisualPipeBlockEntity.PRIMARY).isEmpty());
        assertEquals(java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                entity.functionalIdentity(VisualPipeBlockEntity.SECONDARY).orElseThrow());

        CompoundTag malformed = data.copy();
        ListTag badLanes = malformed.getCompound("pipe_functional_ids").getList("lanes", Tag.TAG_COMPOUND);
        badLanes.getCompound(0).putInt("layer", VisualPipeBlockEntity.PRIMARY);
        entity.loadAdditional(malformed, null);
        assertEquals(VisualPipeBlockEntity.SECONDARY, entity.getLayerMask());
        assertTrue(entity.functionalIdentity(VisualPipeBlockEntity.PRIMARY).isEmpty());
        assertTrue(entity.functionalIdentity(VisualPipeBlockEntity.SECONDARY).isEmpty());
    }

    @Test
    void identitiesRequireValidVisualAndFunctionalSchemas() {
        VisualPipeBlockEntity entity = new VisualPipeBlockEntity(net.minecraft.core.BlockPos.ZERO,
                com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState());
        CompoundTag valid = functionalData(2, VisualPipeBlockEntity.PRIMARY | VisualPipeBlockEntity.SECONDARY);
        entity.loadAdditional(valid, null);
        assertTrue(entity.functionalIdentity(VisualPipeBlockEntity.PRIMARY).isPresent());
        assertTrue(entity.functionalIdentity(VisualPipeBlockEntity.SECONDARY).isPresent());
        CompoundTag saved = new CompoundTag();
        entity.saveAdditional(saved, null);
        assertEquals(2, saved.getCompound("pipe_functional_ids").getList("lanes", Tag.TAG_COMPOUND).size());

        for (CompoundTag invalid : new CompoundTag[]{new CompoundTag(),
                withVisual(functionalData(3, 7), 3, 7),
                wrongFunctionalFieldType(valid.copy()),
                tooManyFunctionalLanes(valid.copy())}) {
            if (!invalid.contains("pipe_functional_ids")) invalid.put("pipe_functional_ids", valid.get("pipe_functional_ids").copy());
            entity.loadAdditional(invalid, null);
            assertTrue(entity.functionalIdentity(VisualPipeBlockEntity.PRIMARY).isEmpty());
            assertTrue(entity.functionalIdentity(VisualPipeBlockEntity.SECONDARY).isEmpty());
        }
        CompoundTag client = entity.getUpdateTag(null);
        assertFalse(client.contains("pipe_functional_ids"));
    }

    private static CompoundTag functionalData(int version, int mask) {
        CompoundTag data = new CompoundTag();
        data.putInt("visual_pipe_version", version);
        data.putInt("visual_pipe_mask", mask);
        CompoundTag ids = new CompoundTag();
        ids.putInt("version", 1);
        ListTag entries = new ListTag();
        for (int layer : new int[]{VisualPipeBlockEntity.PRIMARY, VisualPipeBlockEntity.SECONDARY}) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("layer", layer);
            entry.putString("id", layer == VisualPipeBlockEntity.PRIMARY
                    ? "123e4567-e89b-12d3-a456-426614174000" : "123e4567-e89b-12d3-a456-426614174001");
            entries.add(entry);
        }
        ids.put("lanes", entries);
        data.put("pipe_functional_ids", ids);
        return data;
    }

    private static CompoundTag withVisual(CompoundTag data, int version, int mask) {
        data.putInt("visual_pipe_version", version);
        data.putInt("visual_pipe_mask", mask);
        return data;
    }

    private static CompoundTag wrongFunctionalFieldType(CompoundTag data) {
        data.getCompound("pipe_functional_ids").putString("version", "1");
        return data;
    }

    private static CompoundTag tooManyFunctionalLanes(CompoundTag data) {
        ListTag lanes = data.getCompound("pipe_functional_ids").getList("lanes", Tag.TAG_COMPOUND);
        CompoundTag third = new CompoundTag();
        third.putInt("layer", VisualPipeBlockEntity.TERTIARY);
        third.putString("id", "123e4567-e89b-12d3-a456-426614174002");
        lanes.add(third);
        lanes.add(third.copy());
        return data;
    }
}
