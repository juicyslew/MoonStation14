package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ReagentSystemTest {
    private static final ResourceKey<ReagentData> A = ModReagents.createKey("transfer_a");
    private static final ResourceKey<ReagentData> B = ModReagents.createKey("transfer_b");

    @Test
    void transferToTheSameHolderIsANoOp() {
        Object holder = new Object();
        IReagentTrait trait = () -> 10f;
        TraitHandler<IReagentTrait> handle = new TraitHandler<>(holder, trait);

        assertDoesNotThrow(() -> ReagentSystem.handleTransfer(handle, handle, null, 1f));
    }

    @Test
    void unitTransferRespectsFullAndOneCentFreeCapacityAtPuddleScale() {
        ReagentAttachment source = new ReagentAttachment(Map.of(A, 2f));
        ReagentAttachment full = new ReagentAttachment(Map.of(B, 200_000f));
        assertEquals(0, ReagentAttachment.transferUnits(source, full, 100, 20_000_000));
        assertEquals(200_000f, full.snapshotUnits().get(B) / 100f);

        ReagentAttachment nearFull = new ReagentAttachment(Map.of(B, 200_000f));
        nearFull.specificRemove(B, .01f);
        assertEquals(1, ReagentAttachment.transferUnits(source, nearFull, 100, 20_000_000));
        assertEquals(20_000_000L, nearFull.totalUnits());
        assertEquals(199, source.totalUnits());
    }

    @Test
    void transferSplitsOddCentDeterministicallyAndConservesBothSides() {
        ReagentAttachment source = new ReagentAttachment(Map.of(A, .01f, B, .01f));
        ReagentAttachment target = new ReagentAttachment();
        long before = source.totalUnits() + target.totalUnits();
        assertEquals(1, ReagentAttachment.transferUnits(source, target, 1, 100));
        assertEquals(Map.of(A, 1L), target.snapshotUnits());
        assertEquals(Map.of(B, 1L), source.snapshotUnits());
        assertEquals(before, source.totalUnits() + target.totalUnits());
    }

    @Test
    void repeatedBackAndForthTransfersNeverCreateCents() {
        ReagentAttachment left = new ReagentAttachment(Map.of(A, 12_345.67f, B, .01f));
        ReagentAttachment right = new ReagentAttachment(Map.of(B, 123.45f));
        long expected = left.totalUnits() + right.totalUnits();
        for (int i = 0; i < 500; i++) {
            ReagentAttachment.transferUnits(left, right, 17_003, ReagentUnits.MAX_CENTS);
            ReagentAttachment.transferUnits(right, left, 17_003, ReagentUnits.MAX_CENTS);
            assertEquals(expected, left.totalUnits() + right.totalUnits());
        }
    }
}
