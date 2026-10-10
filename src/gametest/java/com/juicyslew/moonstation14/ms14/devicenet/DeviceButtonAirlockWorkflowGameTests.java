package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.devicenet.control.WallSignalControlBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockCoreGameTests;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.world.level.block.Blocks;

/** Native device-network button invokes the actual powered airlock endpoint. */
@GameTestTags("device-network")
@PrefixGameTestTemplate(false)
public final class DeviceButtonAirlockWorkflowGameTests {
    private DeviceButtonAirlockWorkflowGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=200)
    public static void buttonPressOpensLinkedPoweredAirlock(GameTestHelper helper) {
        var fixture=AirlockCoreGameTests.poweredAirlock(helper);
        ServerLevel level=helper.getLevel();
        BlockPos buttonPos=helper.absolutePos(new BlockPos(3,1,7));
        level.setBlock(buttonPos.south(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(buttonPos,ModBlocks.SIGNAL_BUTTON.get().defaultBlockState(),3);
        helper.runAfterDelay(1,()->{
            if(!(level.getBlockEntity(buttonPos) instanceof WallSignalControlBlockEntity button))
                throw new GameTestAssertException("placed button creates native endpoint");
            AirlockBlockEntity door=fixture.door();
            AirlockCoreGameTests.waitForFullPower(helper, fixture, 120,
                    () -> runPoweredButtonWorkflow(helper, fixture, buttonPos));
        });
    }

    private static void runPoweredButtonWorkflow(GameTestHelper helper, AirlockCoreGameTests.Fixture fixture,
                                                  BlockPos buttonPos) {
        ServerLevel level=helper.getLevel();
        if (!(level.getBlockEntity(buttonPos) instanceof WallSignalControlBlockEntity button))
            throw new GameTestAssertException("placed button creates native endpoint");
        AirlockBlockEntity door=fixture.door();
        DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(level);
        DeviceEndpoint doorEndpoint=runtime.transport().lookup(new DeviceIdentity(door.identity()));
        if(doorEndpoint==null||!runtime.links().link(runtime.graph(),button,"Pressed",doorEndpoint,"Toggle"))
            throw new GameTestAssertException("actual button source links to airlock Toggle sink");
        if(door.state().phase()!=AirlockState.DoorPhase.CLOSED)
            throw new GameTestAssertException("powered airlock starts closed");
        var player=FakePlayerFactory.getMinecraft(level);
        if(!button.activate(player))throw new GameTestAssertException("actual button accepts activation");
        if(door.state().phase()!=AirlockState.DoorPhase.CLOSED)
            throw new GameTestAssertException("button delivery is queued, not synchronous");
        helper.startSequence().thenExecuteAfter(1,()->{
            if(door.state().phase()!=AirlockState.DoorPhase.OPENING)
                throw new GameTestAssertException("native runtime server tick delivers button signal to door");
        }).thenExecuteAfter(8,()->{
            if(door.state().phase()!=AirlockState.DoorPhase.OPENING_PASSABLE
                    &&door.state().phase()!=AirlockState.DoorPhase.OPEN)
                throw new GameTestAssertException("airlock advances through scheduled opening after runtime tick");
        }).thenExecuteAfter(4,helper::succeed);
    }
}
