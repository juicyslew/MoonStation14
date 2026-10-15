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
@GameTestTags("tagrepair-button-signal")
@PrefixGameTestTemplate(false)
public final class DeviceButtonAirlockWorkflowGameTests {
    private DeviceButtonAirlockWorkflowGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=300)
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
            AirlockCoreGameTests.waitForFullPower(helper, fixture, 120, () -> waitForButtonReady(helper,fixture,buttonPos,20));
        });
    }

    private static void waitForButtonReady(GameTestHelper helper,AirlockCoreGameTests.Fixture fixture,BlockPos buttonPos,int remaining){
        AirlockBlockEntity door=fixture.door();
        var runtime=DeviceNetworkRuntime.forLevel(helper.getLevel());
        var endpoint=runtime.transport().lookup(new DeviceIdentity(door.identity()));
        var network=endpoint==null?null:endpoint.composition().network().orElse(null);
        boolean connected=network!=null&&runtime.transport().isConnected(endpoint.identity(),network.networkId());
        if(door.state().powerAvailable()&&connected){runPoweredButtonWorkflow(helper,fixture,buttonPos);return;}
        if(remaining<=0){
            String diagnostic="fresh="+com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPowerDiagnostic(helper.getLevel(),door)
                    +" doorPhase="+door.state().phase()+" cachedPower="+door.state().powerAvailable()+" targetNetworkConnected="+connected
                    +" targetNetwork="+(network==null?"missing":network.networkId())+" queued="+runtime.transport().queuedPackets();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[button airlock GameTest readiness exhausted] {}",diagnostic);
            throw new GameTestAssertException("button readiness timeout: doorPhase="+door.state().phase()
                    +" cachedPower="+door.state().powerAvailable()+" targetNetworkConnected="+connected);
        }
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForButtonReady(helper,fixture,buttonPos,remaining-1));
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
        awaitNativeOpen(helper,door,runtime,5);
    }

    private static void awaitNativeOpen(GameTestHelper helper,AirlockBlockEntity door,DeviceNetworkRuntime runtime,int remaining){
        if(door.state().phase()==AirlockState.DoorPhase.OPENING||door.state().phase()==AirlockState.DoorPhase.OPEN){helper.succeed();return;}
        if(remaining<=0)throw new GameTestAssertException("native button delivery not accepted: doorPhase="+door.state().phase()
                +" cachedPower="+door.state().powerAvailable()+" queued="+runtime.transport().queuedPackets());
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->awaitNativeOpen(helper,door,runtime,remaining-1));
    }
}
