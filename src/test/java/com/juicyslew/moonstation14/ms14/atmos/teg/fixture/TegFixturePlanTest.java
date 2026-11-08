package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TegFixturePlanTest {
    @Test void coreSeparatesLvAndHvAndUsesCorrectCirculatorOrientation() {
        BlockPos core = new BlockPos(20, 70, -4);
        var plan = TegFixturePlan.layout(core);
        assertEquals(core.west(), plan.lvReceiver());
        assertEquals(core.east(), plan.hvOutput());
        assertEquals(core.north(), plan.northCirculator());
        assertEquals(Direction.EAST, plan.northFacing());
        assertEquals(core.south(), plan.southCirculator());
        assertEquals(Direction.WEST, plan.southFacing());
    }

    @Test void planDoesNotAllowMisleadingAlteredTopology() {
        BlockPos core = new BlockPos(0, 0, 0);
        var valid=TegFixturePlan.layout(core);
        assertThrows(IllegalArgumentException.class, () -> new TegFixturePlan.Layout(core, Direction.NORTH,
                core.east(), core.east(), core.north(), Direction.EAST, core.south(), Direction.WEST,
                valid.devices(),valid.cables(),valid.cableHosts()));
    }

    @Test void startupAndGenerationLoadAreOnSeparatePowerIslands() {
        var layout=TegFixturePlan.layout(new BlockPos(40,70,40));
        assertTrue(TegFixturePlan.powerTopologyValid(layout));
        var unplugged=layout.cables().stream().filter(n->!(n.host().equals(layout.devices().load().north())
                &&n.face()==Direction.SOUTH&&n.tier()==com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC)).toList();
        var disconnected=new TegFixturePlan.Layout(layout.core(),layout.coreFacing(),layout.lvReceiver(),layout.hvOutput(),
                layout.northCirculator(),layout.northFacing(),layout.southCirculator(),layout.southFacing(),
                layout.devices(),unplugged,layout.cableHosts());
        assertFalse(TegFixturePlan.powerTopologyValid(disconnected),"missing real-load terminal must invalidate the power proof");
    }
}
