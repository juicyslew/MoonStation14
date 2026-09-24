package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PuddleBlockEntityTest {
    private static final ResourceKey<ReagentData> A = ModReagents.createKey("puddle_flow_a");
    private static final ResourceKey<ReagentData> B = ModReagents.createKey("puddle_flow_b");

    @Test
    void flowAdmissionIsExactNearPuddleCapacity() {
        ReagentAttachment source = new ReagentAttachment(Map.of(A, 1f));
        ReagentAttachment target = new ReagentAttachment(Map.of(B, 200_000f));
        target.removeUnits(B, 1);
        long before = source.totalUnits() + target.totalUnits();

        PuddleBlockEntity.transferFlow(source, target, .01f, 0f, 200_000f);

        assertEquals(20_000_000L, target.totalUnits());
        assertEquals(99L, source.totalUnits());
        assertEquals(before, source.totalUnits() + target.totalUnits());
    }

    @Test
    void fillLevelKeepsEmptyDistinctFromAnyPositiveRepresentableVolume() {
        assertEquals(0, PuddleBlockEntity.fillLevelForVolume(0f));
        assertEquals(1, PuddleBlockEntity.fillLevelForVolume(Float.MIN_VALUE));
        assertEquals(1, PuddleBlockEntity.fillLevelForVolume(0.1f));
        assertEquals(1, PuddleBlockEntity.fillLevelForVolume(Math.nextDown(6f)));
        assertEquals(1, PuddleBlockEntity.fillLevelForVolume(6f));
        assertEquals(2, PuddleBlockEntity.fillLevelForVolume(Math.nextDown(12.67f)));
        assertEquals(2, PuddleBlockEntity.fillLevelForVolume(12.67f));
        assertEquals(3, PuddleBlockEntity.fillLevelForVolume(Math.nextDown(20f)));
        assertEquals(3, PuddleBlockEntity.fillLevelForVolume(20f));
    }

    @Test
    void invalidVolumesUseTheEmptyFillLevel() {
        assertEquals(0, PuddleBlockEntity.fillLevelForVolume(-1f));
        assertEquals(0, PuddleBlockEntity.fillLevelForVolume(Float.NaN));
        assertEquals(0, PuddleBlockEntity.fillLevelForVolume(Float.POSITIVE_INFINITY));
        assertEquals(0, PuddleBlockEntity.fillLevelForVolume(Float.NEGATIVE_INFINITY));
    }
}
