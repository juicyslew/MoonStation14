package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native fixture coverage for the production prepared-installation delegate. */
@GameTestTags("firelock")
@PrefixGameTestTemplate(false)
public final class FirelockInstallationGameTests {
    private FirelockInstallationGameTests() { }
    private static AirlockBlockEntity airlock(GameTestHelper helper,BlockPos root){
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();helper.getLevel().setBlock(root,lower,3);
        helper.getLevel().setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        return helper.getLevel().getBlockEntity(root) instanceof AirlockBlockEntity be?be:null;
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_install",timeoutTicks=40)
    public static void preparedInstallConsumesThenPublishesWithoutReplacingBaseIdentity(GameTestHelper helper){
        BlockPos root=helper.absolutePos(new BlockPos(3,1,3));var be=airlock(helper,root);if(be==null){helper.fail("airlock host missing");return;}var identity=be.identity();var prepared=be.prepareFirelockInstallation(root);if(prepared==null){helper.fail("valid attachment was not prepared");return;}var baseState=be.state();
        var result=FirelockInstallationCommit.commit(prepared::isCurrent,()->com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.SUCCESS,prepared::publish);
        if(result!=com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.SUCCESS||be.firelockController()==null||!identity.equals(be.identity())||be.state()!=baseState){helper.fail("prepared attachment failed or replaced base airlock identity/controller state");return;}helper.succeed();
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_install",timeoutTicks=40)
    public static void rejectedPreparationAndHeldFailureDoNotPublish(GameTestHelper helper){
        BlockPos root=helper.absolutePos(new BlockPos(3,1,3));var be=airlock(helper,root);if(be==null){helper.fail("airlock host missing");return;}
        if(be.prepareFirelockInstallation(root.above())!=null){helper.fail("wrong target root prepared");return;}var prepared=be.prepareFirelockInstallation(root);if(prepared==null){helper.fail("valid attachment did not prepare");return;}
        var result=FirelockInstallationCommit.commit(prepared::isCurrent,()->com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.DENIED,prepared::publish);
        if(result!=com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.DENIED||be.firelockController()!=null){helper.fail("failed held mutation published an attachment");return;}helper.succeed();
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="firelock_install",timeoutTicks=40)
    public static void staleAndAlreadyAttachedTargetsRejectBeforeHeldMutation(GameTestHelper helper){
        BlockPos root=helper.absolutePos(new BlockPos(3,1,3));var be=airlock(helper,root);if(be==null){helper.fail("airlock host missing");return;}var stale=be.prepareFirelockInstallation(root);if(stale==null){helper.fail("valid attachment did not prepare");return;}
        helper.getLevel().setBlock(root.above(),Blocks.GLASS.defaultBlockState(),3);var result=FirelockInstallationCommit.commit(stale::isCurrent,()->{helper.fail("stale target consumed held item");return com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.SUCCESS;},stale::publish);
        if(result!=com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.DENIED||be.firelockController()!=null){helper.fail("stale target published");return;}
        // Replacing the upper cell breaks AirlockBlock's paired-cell contract and removes the lower host.
        // A removed BE remains stale; restore a complete, fresh pair rather than reviving it.
        var fresh=airlock(helper,root);if(fresh==null||fresh==be||fresh.isRemoved()){helper.fail("fresh valid pair did not create a live airlock host");return;}
        var prepared=fresh.prepareFirelockInstallation(root);if(prepared==null){helper.fail("fresh valid target did not prepare");return;}prepared.publish();
        if(fresh.prepareFirelockInstallation(root)!=null){helper.fail("already-attached target prepared a second controller");return;}helper.succeed();
    }
}
