package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Trusted root-domain behavior only; this fixture does not claim authenticated held-tool admission. */
@GameTestTags({"airlock", "airlock_pry_domain"})
@PrefixGameTestTemplate(false)
public final class AirlockPryDomainGameTests {
    private AirlockPryDomainGameTests() { }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_pry_domain",timeoutTicks=90)
    public static void unpoweredPryTogglesOpenThenClosesThroughScheduledPartialTransition(GameTestHelper helper) {
        var level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(5,1,5));
        level.setBlock(root,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        level.setBlock(root.above(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        var door=(AirlockBlockEntity)level.getBlockEntity(root);
        long startedAt=level.getGameTime();
        require(door!=null&&!door.isRemoved()&&level.getBlockEntity(root)==door
                        &&level.getBlockEntity(root.above())==null
                        &&level.getBlockState(root).getBlock()==ModBlocks.AIRLOCK.get()
                        &&level.getBlockState(root.above()).getBlock()==ModBlocks.AIRLOCK.get(),
                "placed root/upper halves and live BE; time="+startedAt);
        require(door!=null&&!door.state().powerAvailable()&&!door.state().boltsDown(),"placed door starts unpowered and unbolted");
        require(door.pry()&&door.state().phase()==AirlockState.DoorPhase.OPENING,"first crowbar domain action starts opening");
        var actionIdentity=door.identity();long actionRevision=door.state().revision(),actionDeadline=door.state().transitionAt();
        door.onLoad();
        require(door.identity().equals(actionIdentity)&&door.state().revision()==actionRevision
                        &&door.state().phase()==AirlockState.DoorPhase.OPENING&&door.state().transitionAt()==actionDeadline,
                "later onLoad preserves first-action state, identity, revision, and deadline");
        helper.runAfterDelay(14,()->{
            require(!door.isRemoved()&&level.getBlockEntity(root)==door&&level.getBlockEntity(root.above())==null
                            &&level.getBlockState(root).getBlock()==ModBlocks.AIRLOCK.get()
                            &&level.getBlockState(root.above()).getBlock()==ModBlocks.AIRLOCK.get()
                            &&door.state().phase()==AirlockState.DoorPhase.OPEN,
                    "scheduled pry opening; started="+startedAt+" now="+level.getGameTime()+" root="+level.getBlockState(root)
                            +" upper="+level.getBlockState(root.above())+" rootBE="+level.getBlockEntity(root)
                             +" upperBE="+level.getBlockEntity(root.above())+" removed="+door.isRemoved()
                             +" state="+door.state());
            require(door.pry()&&door.state().phase()==AirlockState.DoorPhase.CLOSING,"second crowbar domain action starts closing");
            helper.runAfterDelay(9,()->{
                require(door.state().phase()==AirlockState.DoorPhase.CLOSING_PASSABLE&&!door.state().doorPassable(),"pry close becomes solid at partial close");
                helper.runAfterDelay(5,()->{
                    require(door.state().phase()==AirlockState.DoorPhase.CLOSED,"pry close finishes its second transition");
                    helper.succeed();
                });
            });
        });
    }

    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
