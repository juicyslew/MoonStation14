package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApcFixtureOwnershipTest {
    @Test void projectedAppearanceDoesNotChangeStructuralOwnership() {
        Object be=new Object();var block=org.mockito.Mockito.mock(PowerDeviceBlock.class);
        org.mockito.Mockito.when(block.kind()).thenReturn(PowerDeviceKind.APC);
        BlockState placed=state(block,PowerDeviceBlock.Display.LACK,false,0);
        BlockState projected=state(block,PowerDeviceBlock.Display.CHARGING,true,12);
        assertNotSame(placed,projected);assertNotEquals(placed,projected);
        assertTrue(ApcFixtureOwnership.matches(be,be,placed,projected));
    }
    @Test void replacementEntityBlockKindOrFacingIsRejected() {
        Object be=new Object();var block=org.mockito.Mockito.mock(PowerDeviceBlock.class);var replacement=org.mockito.Mockito.mock(PowerDeviceBlock.class);
        org.mockito.Mockito.when(block.kind()).thenReturn(PowerDeviceKind.APC);org.mockito.Mockito.when(replacement.kind()).thenReturn(PowerDeviceKind.APC);
        BlockState placed=state(block,PowerDeviceBlock.Display.LACK,false,0);
        assertFalse(ApcFixtureOwnership.matches(be,new Object(),placed,placed));
        assertFalse(ApcFixtureOwnership.matches(be,be,placed,state(replacement,PowerDeviceBlock.Display.LACK,false,0)));
        org.mockito.Mockito.when(replacement.kind()).thenReturn(PowerDeviceKind.HV_SOURCE);
        assertFalse(ApcFixtureOwnership.matches(be,be,placed,state(replacement,PowerDeviceBlock.Display.LACK,false,0)));
        assertFalse(ApcFixtureOwnership.matches(be,be,placed,state(block,PowerDeviceBlock.Display.LACK,false,0,Direction.NORTH)));
    }

    private static BlockState state(PowerDeviceBlock block,PowerDeviceBlock.Display display,boolean glow,int emission) {
        return state(block,display,glow,emission,Direction.EAST);
    }
    private static BlockState state(PowerDeviceBlock block,PowerDeviceBlock.Display display,boolean glow,int emission,Direction facing) {
        BlockState state=org.mockito.Mockito.mock(BlockState.class);
        org.mockito.Mockito.when(state.getBlock()).thenReturn(block);
        org.mockito.Mockito.when(state.getValue(PowerDeviceBlock.FACING)).thenReturn(facing);
        org.mockito.Mockito.when(state.getValue(PowerDeviceBlock.DISPLAY)).thenReturn(display);
        org.mockito.Mockito.when(state.getValue(PowerDeviceBlock.GLOW)).thenReturn(glow);
        org.mockito.Mockito.when(state.getValue(PowerDeviceBlock.EMISSION)).thenReturn(emission);
        return state;
    }
}
