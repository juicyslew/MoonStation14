package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FunctionalDisposalUnitPlacementTest {
    @Test void placementPreflightRequiresGenuinelyEmptyAirCell() {
        var air = Blocks.AIR.defaultBlockState();
        assertTrue(FunctionalDisposalUnitPlacement.destinationAvailable(air, false, true));
        assertFalse(FunctionalDisposalUnitPlacement.destinationAvailable(Blocks.STONE.defaultBlockState(), false, true));
        assertFalse(FunctionalDisposalUnitPlacement.destinationAvailable(air, true, true));
        assertFalse(FunctionalDisposalUnitPlacement.destinationAvailable(air, false, false));
    }

    @Test void actualRegisteredMachineDefaultRemainsAnchoredAndUnrotated() {
        var state = ModBlocks.DISPOSAL_UNIT.get().defaultBlockState();
        assertTrue(state.getValue(DisposalUnitBlock.ANCHORED));
        assertFalse(state.getProperties().stream().anyMatch(property -> property.getName().contains("facing")));
    }
}
