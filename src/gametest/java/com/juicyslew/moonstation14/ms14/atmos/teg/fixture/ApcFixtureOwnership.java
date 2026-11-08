package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;

/** Structural APC ownership check; deliberately excludes runtime-projected appearance properties. */
public final class ApcFixtureOwnership {
    private ApcFixtureOwnership() { }
    public static boolean matches(Object expectedEntity,Object currentEntity,Object expectedBlock,Object currentBlock,
                                  PowerDeviceKind expectedKind,PowerDeviceKind currentKind,
                                  Direction expectedFacing,Direction currentFacing) {
        return expectedEntity!=null&&expectedEntity==currentEntity&&expectedBlock!=null&&expectedBlock==currentBlock
                &&expectedKind==PowerDeviceKind.APC&&currentKind==PowerDeviceKind.APC
                &&expectedFacing!=null&&expectedFacing==currentFacing;
    }

    /** Shared fixture boundary over real states; intentionally reads only identity-bearing structure. */
    public static boolean matches(Object expectedEntity,Object currentEntity,BlockState placed,BlockState current) {
        if(placed==null||current==null||!(placed.getBlock() instanceof PowerDeviceBlock expected)
                ||!(current.getBlock() instanceof PowerDeviceBlock actual)) return false;
        return matches(expectedEntity,currentEntity,placed.getBlock(),current.getBlock(),expected.kind(),actual.kind(),
                placed.getValue(PowerDeviceBlock.FACING),current.getValue(PowerDeviceBlock.FACING));
    }
}
