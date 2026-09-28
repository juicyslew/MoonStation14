package com.juicyslew.moonstation14.ms14.power.device;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PowerDeviceRulesTest {
    @Test void portsRotateAndOnlyContactOppositeAdjacentCableFaceAtMatchingTier() {
        var ports = PowerDeviceKind.HV_MV_SUBSTATION.ports(new BlockPos(4, 5, 6), Direction.EAST);
        assertEquals(2, ports.size());
        assertEquals(CableTier.HV, ports.get(0).tier());
        assertEquals(CableTier.MV, ports.get(1).tier());
        assertTrue(PowerTopology.deviceAdjacent(ports.get(0), new CableFaceNode(new BlockPos(5, 5, 6), Direction.WEST, CableTier.HV)));
        assertFalse(PowerTopology.deviceAdjacent(ports.get(0), new CableFaceNode(new BlockPos(5, 5, 6), Direction.WEST, CableTier.MV)));
        assertFalse(PowerTopology.deviceAdjacent(ports.get(0), new CableFaceNode(new BlockPos(4, 5, 6), Direction.WEST, CableTier.HV)));
        assertFalse(PowerTopology.deviceAdjacent(ports.get(0), new CableFaceNode(new BlockPos(5, 5, 7), Direction.WEST, CableTier.HV)));
    }

    @Test void apcPersistenceClampsEnergyAndPreservesBreaker() {
        var restored = PowerDeviceRules.loadApc(PowerDeviceRules.saveApc(123.5, false));
        assertEquals(123.5, restored.energyJoules());
        assertFalse(restored.breakerClosed());
        assertEquals(0.0, PowerDeviceRules.loadApc(tag(Double.NaN)).energyJoules());
        assertEquals(0.0, PowerDeviceRules.loadApc(tag(Double.POSITIVE_INFINITY)).energyJoules());
        assertEquals(0.0, PowerDeviceRules.loadApc(tag(-1)).energyJoules());
        assertEquals(PowerDeviceRules.APC_CAPACITY_JOULES,
                PowerDeviceRules.loadApc(tag(PowerDeviceRules.APC_CAPACITY_JOULES * 4)).energyJoules());
        assertEquals(PowerDeviceRules.APC_INITIAL_JOULES, PowerDeviceRules.loadApc(new CompoundTag()).energyJoules());
    }

    @Test void breakerRequiresServerAuthorizationAndValidInRangeDistance() {
        assertTrue(PowerDeviceRules.canToggle(true, true, 64));
        assertFalse(PowerDeviceRules.canToggle(false, true, 1));
        assertFalse(PowerDeviceRules.canToggle(true, false, 1));
        assertFalse(PowerDeviceRules.canToggle(true, true, 64.01));
        assertFalse(PowerDeviceRules.canToggle(true, true, Double.NaN));
    }

    private static CompoundTag tag(double energy) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("BatteryJoules", energy);
        return tag;
    }
}
