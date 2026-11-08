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
import net.neoforged.bus.api.EventPriority;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.level.block.Blocks;

/** Native device-network button invokes the actual powered airlock endpoint. */
@GameTestTags("tagrepair-button-signal")
@PrefixGameTestTemplate(false)
public final class DeviceButtonAirlockWorkflowGameTests {
    private DeviceButtonAirlockWorkflowGameTests() { }

    @GameTestTags("shuttle-owner-regression")
    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", batch="device_button_airlock_workflow", timeoutTicks=300)
    public static void buttonPressOpensLinkedPoweredAirlock(GameTestHelper helper) {
        var fixture=AirlockCoreGameTests.poweredAirlock(helper);
        ServerLevel level=helper.getLevel();
        BlockPos buttonPos=helper.absolutePos(new BlockPos(3,1,7));
        level.setBlock(buttonPos.south(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(buttonPos,ModBlocks.SIGNAL_BUTTON.get().defaultBlockState(),3);
        helper.runAfterDelay(1,()->{
            if(!(level.getBlockEntity(buttonPos) instanceof WallSignalControlBlockEntity))
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
        if(remaining<=0)throw new GameTestAssertException("button readiness timeout: fresh="
                +com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPowerDiagnostic(helper.getLevel(),door)
                +" sample="+sample+" endpoints="+endpointDiagnostic(sender,tx)+" -> "+endpointDiagnostic(doorEndpoint,rx)
                +" receiverPowered="+receiverPowered+" link="+linkReady);
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForButtonReady(helper,fixture,buttonPos,remaining-1));
    }

    private static boolean rxRequiresPower(DeviceEndpoint endpoint){return endpoint.composition().network().map(DeviceComposition.NetworkInterface::requiresPower).orElse(false);}

    private static String endpointDiagnostic(DeviceEndpoint endpoint,DeviceComposition.NetworkInterface network){
        return endpoint==null?"missing":endpoint.identity()+"[available="+endpoint.available()+",net="+(network==null?"missing":network.networkId())
                +",tx="+(network!=null&&network.transmit())+",freq="+(network==null?0:network.transmitFrequency()+"/"+network.receiveFrequency())
                +",address="+(network==null?"":network.address())+"]";
    }

    private static void runPoweredButtonWorkflow(GameTestHelper helper, AirlockCoreGameTests.Fixture fixture,BlockPos buttonPos) {
        ServerLevel level=helper.getLevel();
        AirlockBlockEntity door=fixture.door();
        DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(level);
        final String[] latest={"not-yet-sampled"};
        final boolean[] submitted={false};
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.observeServerTicksUntil(
                helper,120,EventPriority.LOW,event->{
            var meter=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level,door);
            DeviceEndpoint buttonEndpoint=level.getBlockEntity(buttonPos) instanceof WallSignalControlBlockEntity buttonEntity
                    ?runtime.transport().lookup(buttonEntity.identity()):null;
            DeviceEndpoint doorEndpoint=runtime.transport().lookup(new DeviceIdentity(door.identity()));
            var buttonNetwork=buttonEndpoint==null?null:buttonEndpoint.composition().network().orElse(null);
            var doorNetwork=doorEndpoint==null?null:doorEndpoint.composition().network().orElse(null);
            boolean endpointsAvailable=buttonEndpoint!=null&&doorEndpoint!=null&&buttonEndpoint.available()&&doorEndpoint.available();
            boolean connected=endpointsAvailable&&buttonNetwork!=null&&doorNetwork!=null
                    &&buttonNetwork.networkId()==doorNetwork.networkId()
                    &&runtime.transport().isConnected(buttonEndpoint.identity(),buttonNetwork.networkId())
                    &&runtime.transport().isConnected(doorEndpoint.identity(),doorNetwork.networkId());
            String meterGate=!meter.known()||meter.deliveredWatts()!=door.prototype().apcPowerReceiver().requestedWatts()
                    ?com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPowerDiagnostic(level,door):"accepted";
            latest[0]="serverTick="+level.getServer().getTickCount()+" meter="+meter+" gate="+meterGate
                    +" phase="+door.state().phase()+" cachedPower="+door.state().powerAvailable()+" connected="+connected
                    +" queued="+runtime.transport().queuedPackets();
            if(!meter.known()||meter.deliveredWatts()!=door.prototype().apcPowerReceiver().requestedWatts()
                    ||!connected||door.state().phase()!=AirlockState.DoorPhase.CLOSED)return;
            if(!(level.getBlockEntity(buttonPos) instanceof WallSignalControlBlockEntity button))
                throw new GameTestAssertException("real button endpoint disappeared at paid LOW post phase");
            if(!runtime.links().link(runtime.graph(),button,"Pressed",doorEndpoint,"Toggle"))
                throw new GameTestAssertException("actual button source failed to link to airlock Toggle at paid phase");
            int queuedBefore=runtime.transport().queuedPackets();
            if(!button.activate(FakePlayerFactory.getMinecraft(level)))
                throw new GameTestAssertException("actual button rejected its single eligible activation");
            if(door.state().phase()!=AirlockState.DoorPhase.CLOSED||runtime.transport().queuedPackets()<=queuedBefore)
                throw new GameTestAssertException("button must queue one deferred signal while door remains closed: "+latest[0]);
            submitted[0]=true;
        },()->submitted[0],()->{
            DeviceEndpoint endpoint=runtime.transport().lookup(new DeviceIdentity(door.identity()));
            if(!(level.getBlockEntity(buttonPos) instanceof WallSignalControlBlockEntity button))
                throw new GameTestAssertException("button endpoint disappeared after one-shot paid-phase submit");
            if(!submitted[0])throw new GameTestAssertException("button paid-phase enqueue timeout: "+latest[0]);
            awaitNativeOpen(helper,door,runtime,5,button,endpoint);
        },()->{if(!submitted[0])throw new GameTestAssertException("button paid-phase enqueue timeout: "+latest[0]);});
    }

    private static void awaitNativeOpen(GameTestHelper helper,AirlockBlockEntity door,DeviceNetworkRuntime runtime,int remaining,
                                        WallSignalControlBlockEntity button,DeviceEndpoint doorEndpoint){
        if(door.state().phase()==AirlockState.DoorPhase.OPENING||door.state().phase()==AirlockState.DoorPhase.OPEN){helper.succeed();return;}
        if(remaining<=0)throw new GameTestAssertException("native button delivery not accepted: serverTick="+helper.getLevel().getServer().getTickCount()
                +" doorPhase="+door.state().phase()+" cachedPower="+door.state().powerAvailable()+" queued="+runtime.transport().queuedPackets()
                +" buttonIdentity="+button.identity()+" receiverIdentity="+doorEndpoint.identity());
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->awaitNativeOpen(helper,door,runtime,remaining-1,button,doorEndpoint));
    }
}
