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
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.world.level.block.Blocks;

/** Native device-network button invokes the actual powered airlock endpoint. */
@GameTestTags("tagrepair-button-signal")
@PrefixGameTestTemplate(false)
public final class DeviceButtonAirlockWorkflowGameTests {
    private DeviceButtonAirlockWorkflowGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", batch="device_button_airlock_workflow", timeoutTicks=300)
    public static void buttonPressOpensLinkedPoweredAirlock(GameTestHelper helper) {
        var fixture=AirlockCoreGameTests.poweredAirlock(helper);
        ServerLevel level=helper.getLevel();
        BlockPos buttonPos=helper.absolutePos(new BlockPos(3,1,7));
        level.setBlock(buttonPos.south(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(buttonPos,ModBlocks.SIGNAL_BUTTON.get().defaultBlockState(),3);
        helper.runAfterDelay(1,()->{
            if(!(level.getBlockEntity(buttonPos) instanceof WallSignalControlBlockEntity button))
                throw new GameTestAssertException("placed button creates native endpoint");
            waitForButtonReady(helper,fixture,buttonPos,120);
        });
    }

    private static void waitForButtonReady(GameTestHelper helper,AirlockCoreGameTests.Fixture fixture,BlockPos buttonPos,int remaining){
        AirlockBlockEntity door=fixture.door();
        var runtime=DeviceNetworkRuntime.forLevel(helper.getLevel());
        var sample=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(helper.getLevel(),door);
        var doorEndpoint=runtime.transport().lookup(new DeviceIdentity(door.identity()));
        var sender=helper.getLevel().getBlockEntity(buttonPos) instanceof WallSignalControlBlockEntity control
                ?runtime.transport().lookup(control.identity()):null;
        DeviceComposition.NetworkInterface rx=doorEndpoint==null?null:doorEndpoint.composition().network().orElse(null);
        DeviceComposition.NetworkInterface tx=sender==null?null:sender.composition().network().orElse(null);
        boolean receiverPowered=doorEndpoint!=null&&(!rxRequiresPower(doorEndpoint)||doorEndpoint.powered());
        boolean linkReady=doorEndpoint!=null&&sender!=null&&doorEndpoint.available()&&sender.available()
                &&rx!=null&&tx!=null&&tx.transmit()&&runtime.transport().isConnected(sender.identity(),tx.networkId())
                &&runtime.transport().isConnected(doorEndpoint.identity(),rx.networkId())
                &&tx.networkId()==rx.networkId()&&tx.transmitFrequency()==rx.receiveFrequency();
        double demand=door.prototype().apcPowerReceiver().requestedWatts();
        boolean freshFullPower=sample.known()&&sample.deliveredWatts()==demand;
        if(freshFullPower&&door.state().powerAvailable()&&receiverPowered&&linkReady){runPoweredButtonWorkflow(helper,fixture,buttonPos);return;}
        if(remaining<=0){
            String diagnostic="fresh="+com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPowerDiagnostic(helper.getLevel(),door)
                    +" sample="+sample+" sampleTick="+levelGameTime(helper)+" topologyGeneration="
                    +com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(helper.getLevel())
                    +" cachedPower="+door.state().powerAvailable()+" endpoints="+endpointDiagnostic(sender,tx)+" -> "+endpointDiagnostic(doorEndpoint,rx)
                    +" receiverPowered="+receiverPowered+" link="+linkReady+" queued="+runtime.transport().queuedPackets();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[button airlock GameTest readiness exhausted] {}",diagnostic);
            throw new GameTestAssertException("button readiness timeout: freshPower="+sample.known()+"/"+sample.deliveredWatts()+"/"+demand
                    +" cached="+door.state().powerAvailable()+" endpoints="+endpointDiagnostic(sender,tx)+" -> "+endpointDiagnostic(doorEndpoint,rx)
                    +" receiverPowered="+receiverPowered+" sampleTick="+levelGameTime(helper)+" topologyGeneration="
                    +com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(helper.getLevel())+" link="+linkReady);
        }
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForButtonReady(helper,fixture,buttonPos,remaining-1));
    }

    private static boolean rxRequiresPower(DeviceEndpoint endpoint){return endpoint.composition().network().map(DeviceComposition.NetworkInterface::requiresPower).orElse(false);}
    private static long levelGameTime(GameTestHelper helper){return helper.getLevel().getGameTime();}

    private static String endpointDiagnostic(DeviceEndpoint endpoint,DeviceComposition.NetworkInterface network){
        return endpoint==null?"missing":endpoint.identity()+"[available="+endpoint.available()+",net="+(network==null?"missing":network.networkId())
                +",tx="+(network!=null&&network.transmit())+",freq="+(network==null?0:network.transmitFrequency()+"/"+network.receiveFrequency())
                +",address="+(network==null?"":network.address())+"]";
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
        if(doorEndpoint.composition().network().map(n->n.requiresPower()&&!doorEndpoint.powered()).orElse(false)){
            waitForButtonReady(helper,fixture,buttonPos,120);return;
        }
        if(door.state().phase()!=AirlockState.DoorPhase.CLOSED)
            throw new GameTestAssertException("powered airlock starts closed");
        var player=FakePlayerFactory.getMinecraft(level);
        var attempts=new java.util.ArrayList<String>();
        DeviceIdentity expectedDoorIdentity=doorEndpoint.identity();
        AutoCloseable observation=runtime.transport().observeDelivery(button.identity(),null,attempt->{
            if(attempt.receiverDeviceIdentity()!=null&&!attempt.receiverDeviceIdentity().equals(expectedDoorIdentity))return;
            var fresh=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,door);
            attempts.add(attempt.outcome()+" receiver="+attempt.receiverDeviceIdentity()+" powered="+doorEndpoint.powered()
                    +" fresh="+fresh.known()+"/"+fresh.deliveredWatts()+" phase="+door.state().phase()+" cached="+door.state().powerAvailable()
                    +" bolts="+door.state().boltsDown()+" boltWireCut="+door.state().boltWireCut());
        });
        helper.testInfo.addListener(new GameTestListener(){
            private void cleanup(){closeObservation(observation);}
            public void testStructureLoaded(GameTestInfo info){}
            public void testPassed(GameTestInfo info,GameTestRunner runner){cleanup();}
            public void testFailed(GameTestInfo info,GameTestRunner runner){cleanup();}
            public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){cleanup();}
        });
        boolean accepted=button.activate(player);
        int queued=runtime.transport().queuedPackets();
        if(!accepted){closeObservation(observation);throw new GameTestAssertException("button press rejected accepted="+accepted+" queued="+queued+" receiverPowered="+doorEndpoint.powered()+" sampleTick="+level.getGameTime()+" topologyGeneration="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level)+" power="+com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,door)+" sender="+endpointDiagnostic(runtime.transport().lookup(button.identity()),runtime.transport().lookup(button.identity())==null?null:runtime.transport().lookup(button.identity()).composition().network().orElse(null)));}
        if(door.state().phase()!=AirlockState.DoorPhase.CLOSED){closeObservation(observation);throw new GameTestAssertException("button delivery is queued, not synchronous");}
        awaitNativeOpen(helper,door,runtime,buttonPos,5,queued,observation,attempts);
    }

    private static void closeObservation(AutoCloseable observation){try{observation.close();}catch(Exception e){throw new IllegalStateException(e);}}

    private static void awaitNativeOpen(GameTestHelper helper,AirlockBlockEntity door,DeviceNetworkRuntime runtime,BlockPos buttonPos,int remaining,int queuedAtPress,AutoCloseable observation,java.util.List<String> attempts){
        if(door.state().phase()==AirlockState.DoorPhase.OPENING||door.state().phase()==AirlockState.DoorPhase.OPEN){closeObservation(observation);helper.succeed();return;}
        if(remaining<=0){
            var power=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(helper.getLevel(),door);
            var endpoint=runtime.transport().lookup(new DeviceIdentity(door.identity()));
            closeObservation(observation);
            String attemptSummary=attempts.isEmpty()?"none (source-level observer expects a null-receiver rejection)":attempts.getFirst();
            String failure="button delivery failed phase="+door.state().phase()+" power="+power.known()+"/"+power.deliveredWatts()
                    +" tick="+helper.getLevel().getGameTime()+" gen="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(helper.getLevel())
                    +" queued="+queuedAtPress+"/"+runtime.transport().queuedPackets()+" cached="+door.state().powerAvailable()
                    +" receiverPowered="+(endpoint!=null&&endpoint.powered())+" ownAttempt="+attemptSummary;
            throw new GameTestAssertException(failure.substring(0,Math.min(900,failure.length())));
        }
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->awaitNativeOpen(helper,door,runtime,buttonPos,remaining-1,queuedAtPress,observation,attempts));
    }
}
