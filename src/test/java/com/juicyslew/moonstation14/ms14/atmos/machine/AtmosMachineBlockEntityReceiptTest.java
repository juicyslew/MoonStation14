package com.juicyslew.moonstation14.ms14.atmos.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosMachineBlockEntityReceiptTest {
    @Test
    void transientFlowDecisionSequenceWrapsWithoutSaturating() {
        assertEquals(1, AtmosMachineBlockEntity.nextFlowDecisionSequence(Long.MAX_VALUE));
        assertEquals(2, AtmosMachineBlockEntity.nextFlowDecisionSequence(1));
    }

    @Test
    void committedUnpoweredThermalWorkIsSignedDeduplicatedAndNeverPaid() {
        var host = new AtmosMachineBlockEntity(BlockPos.ZERO,
                com.juicyslew.moonstation14.block.ModBlocks.GAS_THERMO_HEATER.get().defaultBlockState());
        host.recordCommittedUnpoweredWork(42, 1.25, 300);
        host.recordCommittedUnpoweredWork(42, 9, 99);
        assertEquals(1.25, host.committedMovedMoles());
        assertEquals(300, host.committedThermalJoules());
        assertEquals(1, host.committedWorkEvents());
        assertEquals(0, host.committedPaidJoules());
        host.recordCommittedUnpoweredWork(43, 0, -75);
        assertEquals(225, host.committedThermalJoules());
        assertEquals(2, host.committedWorkEvents());
        host.recordCommittedUnpoweredWork(44, 2);
        assertEquals(225, host.committedThermalJoules());
        assertTrue(host.committedMovedMoles() > 0);
    }
    @Test
    void committedPlacementReceiptsRoundTripThroughBlockEntityNbt() {
        var state = com.juicyslew.moonstation14.block.ModBlocks.GAS_THERMO_HEATER.get().defaultBlockState();
        var original = new AtmosMachineBlockEntity(BlockPos.ZERO, state);
        CompoundTag saved = new CompoundTag();
        saved.putDouble("CommittedPaidJoules", 240);
        saved.putDouble("CommittedMovedMoles", 12.5);
        saved.putDouble("CommittedThermalJoules", -80);
        saved.putLong("CommittedWorkEvents", 3);
        saved.putLong("LastCommittedEventTick", 91);
        saved.putDouble("AmbientHeatReceiptJoules", -7);
        saved.putDouble("SpaceHeatReceiptJoules", 9);
        original.loadAdditional(saved, null);
        CompoundTag roundTrip = new CompoundTag();
        original.saveAdditional(roundTrip, null);
        var restored = new AtmosMachineBlockEntity(BlockPos.ZERO, state);
        restored.loadAdditional(roundTrip, null);
        assertEquals(240, restored.committedPaidJoules());
        assertEquals(12.5, restored.committedMovedMoles());
        assertEquals(-80, restored.committedThermalJoules());
        assertEquals(3, restored.committedWorkEvents());
        assertEquals(-7, restored.ambientHeatReceiptJoules());
        assertEquals(9, restored.spaceHeatReceiptJoules());
        saved.putDouble("AmbientHeatReceiptJoules", 7);
        original.loadAdditional(saved, null);
        roundTrip = new CompoundTag();
        original.saveAdditional(roundTrip, null);
        restored.loadAdditional(roundTrip, null);
        assertEquals(7, restored.ambientHeatReceiptJoules());
    }
}
