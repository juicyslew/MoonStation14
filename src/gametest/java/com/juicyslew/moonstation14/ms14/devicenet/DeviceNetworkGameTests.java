package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Native server fixtures for the shared, per-level queued device-network runtime. */
@GameTestTags("device-network")
@PrefixGameTestTemplate(false)
public final class DeviceNetworkGameTests {
    private DeviceNetworkGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=80)
    public static void packetWaitsForTheNativeLaterServerUpdate(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(level);
        FakeEndpoint sender=endpoint(level,1,true,false,"tx",1280,1280,false,false,Optional.empty());
        FakeEndpoint receiver=endpoint(level,2,false,true,"rx",1280,1280,false,false,Optional.empty());
        runtime.loaded(sender); runtime.loaded(receiver);
        DeviceNetworkPacket packet=new DeviceNetworkPacket(sender.identity(),2,1280,"tx","rx",false,
                new SignalPayload("input",DeviceSignal.HIGH));
        require(runtime.transport().enqueue(packet),"runtime accepts valid addressed enqueue");
        require(receiver.received.isEmpty(),"enqueue does not invoke receiver synchronously");
        helper.runAfterDelay(1,()->{
            require(!receiver.received.isEmpty(),"native server post-tick update drains queued packet without manual drain");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=80)
    public static void offlineEndpointStaysGraphMemberWithoutBeingForceLoaded(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(level);
        FakeEndpoint source=endpoint(level,3,true,false,"s",1280,1280,false,false,Optional.empty());
        FakeEndpoint sink=endpoint(level,4,false,true,"r",1280,1280,false,false,Optional.empty());
        runtime.loaded(source); runtime.loaded(sink);
        DeviceLinkGraph graph=runtime.graph();
        var link=new DeviceLinkGraph.Link(new DeviceLinkGraph.PortRef(source.identity(),"out"),new DeviceLinkGraph.PortRef(sink.identity(),"in"));
        require(graph.link(link),"persisted dimension graph accepts relationship");
        runtime.unloaded(sink.identity());
        require(runtime.transport().lookup(sink.identity())==null,"unload removes endpoint from loaded-only directory");
        require(graph.links().contains(link),"offline endpoint identity and graph relationship persist");
        helper.succeed();
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=80)
    public static void filtersAndNetworkFrequencyAreEnforcedByRuntimeTransport(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(level);
        DeviceList emptyAllowlist=new DeviceList(List.of(),32,true,false);
        FakeEndpoint sender=endpoint(level,5,true,false,"b",1280,1280,false,true,Optional.empty());
        sender.list=emptyAllowlist;
        FakeEndpoint addressed=endpoint(level,6,false,true,"target",1280,1280,false,false,Optional.empty());
        FakeEndpoint all=endpoint(level,7,false,true,"all",1280,1280,true,false,Optional.empty());
        FakeEndpoint wrongFrequency=endpoint(level,8,false,true,"wrong",999,999,false,false,Optional.empty());
        runtime.loaded(sender);runtime.loaded(addressed);runtime.loaded(all);runtime.loaded(wrongFrequency);
        require(runtime.transport().enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"b","target",false,new SignalPayload("in",DeviceSignal.HIGH))),"addressed packet enqueued");
        runtime.transport().drain();
        require(addressed.received.size()==1&&all.received.size()==1,"addressed packet reaches matching target and receive-all listener");
        require(wrongFrequency.received.isEmpty(),"receiver on wrong frequency rejected");
        require(runtime.transport().enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"b","",true,new SignalPayload("in",DeviceSignal.HIGH))),"broadcast enqueued");
        runtime.transport().drain();
        require(addressed.received.size()==1&&all.received.size()==1,"empty allow-list with broadcast-attempt hook filters all recipients");
        helper.succeed();
    }

    private static FakeEndpoint endpoint(ServerLevel level,int id,boolean tx,boolean rx,String address,int txFrequency,int rxFrequency,
                                         boolean receiveAll,boolean broadcastHook,Optional<DeviceList.Config> list) {
        var network=new DeviceComposition.NetworkInterface(2,tx,rx,address,"",txFrequency,rxFrequency,receiveAll,false,true,true,false,broadcastHook);
        var composition=new DeviceComposition(tx?List.of(new DevicePort("out","out","")):List.of(),
                rx?List.of(new DevicePort("in","in","")):List.of(),Optional.of(network),list,
                Optional.of(new DeviceComposition.SourceConfig(30,Map.of())),Optional.of(new DeviceComposition.SinkConfig(10)),
                Optional.of(new DeviceComposition.ConnectionConfig(false,true,30)));
        // GameTest levels/runtime graphs can survive fixture reuse; deterministic identities
        // would make a later run collide with links persisted by an earlier run.
        return new FakeEndpoint(DeviceIdentity.random(),level.dimension(),new BlockPos(id,1,1),composition);
    }
    private static void require(boolean value,String message){if(!value)throw new GameTestAssertException(message);}

    private static final class FakeEndpoint implements DeviceEndpoint {
        private final DeviceIdentity identity; private final ResourceKey<Level> dimension; private final BlockPos position;
        private final DeviceComposition composition; private final List<String> received=new java.util.ArrayList<>();
        private DeviceList list;
        private FakeEndpoint(DeviceIdentity identity,ResourceKey<Level> dimension,BlockPos position,DeviceComposition composition){this.identity=identity;this.dimension=dimension;this.position=position;this.composition=composition;}
        public DeviceIdentity identity(){return identity;} public ResourceKey<Level> dimension(){return dimension;} public BlockPos position(){return position;}
        public DeviceComposition composition(){return composition;} public boolean available(){return true;}
        public DeviceList deviceList(){return list;}
        public void receive(DevicePayload payload,String sourceAddress){received.add(sourceAddress);}
    }
}
