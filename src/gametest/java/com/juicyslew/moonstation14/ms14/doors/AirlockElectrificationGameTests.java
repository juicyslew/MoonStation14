package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.minecraft.gametest.framework.GameTestAssertException;

/** Live root consumer checks without fabricating a player/actor authority. */
@GameTestTags({"airlock"})
@PrefixGameTestTemplate(false)
public final class AirlockElectrificationGameTests {
    private AirlockElectrificationGameTests() {}

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_electrification",timeoutTicks=100)
    public static void resolvedElectrificationDrivesRootPowerWireTransitions(GameTestHelper helper) {
        BlockPos relative=new BlockPos(3,1,3),root=helper.absolutePos(relative);
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();
        helper.setBlock(relative,lower);
        helper.setBlock(relative.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER));
        AirlockBlockEntity door=(AirlockBlockEntity)helper.getLevel().getBlockEntity(root);
        require(door.prototype()!=null,"resolved airlock prototype is present");
        require(!door.electrificationState().enabled(),"both power wires start mended");
        // Trusted actorless domain mutation proves live state composition but intentionally cannot shock.
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT),"cut first power wire");
        require(door.electrificationState().enabled(),"one cut wire electrifies door");
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_TWO,AirlockState.WireAction.CUT),"cut second power wire");
        require(!door.electrificationState().enabled(),"both cut wires disable electrification");
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.MEND),"mend first power wire");
        require(door.electrificationState().enabled(),"one mended wire restores electrification");
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_TWO,AirlockState.WireAction.MEND),"mend second power wire");
        require(!door.electrificationState().enabled(),"both mended wires disable electrification");
        helper.succeed();
    }
    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
