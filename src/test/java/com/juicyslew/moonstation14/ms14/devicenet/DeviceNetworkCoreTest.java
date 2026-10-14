package com.juicyslew.moonstation14.ms14.devicenet;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class DeviceNetworkCoreTest {
    private static final ResourceKey<Level> DIM=Level.OVERWORLD;
    static final class Endpoint implements DeviceEndpoint {
        final DeviceIdentity id=DeviceIdentity.random(); String address; final double range;
        final List<DevicePayload> received=new ArrayList<>(); final BlockPos pos; boolean powered=true,requiresPower;
        int networkId=2, txFrequency=1280, rxFrequency=1280, configuredLimit=10; boolean receiveAll=true, broadcastHook,wired,wireless=true,connectionPresent=true,autoConnect=true,customAddress, externalAddressOwner; int overloaded; DeviceList list; Consumer<DevicePayload> onReceive=payload->{ }; Optional<DeviceComposition.SoundOnOverload> overloadSound=Optional.empty();
        Endpoint(String address,int x,double range){this.address=address;this.pos=new BlockPos(x,64,0);this.range=range;}
        public DeviceIdentity identity(){return id;} public ResourceKey<Level> dimension(){return DIM;} public BlockPos position(){return pos;}
        public DeviceComposition composition(){return new DeviceComposition(List.of(new DevicePort("Pressed","Pressed","")),List.of(new DevicePort("Toggle","Toggle","")),Optional.of(new DeviceComposition.NetworkInterface(networkId,true,true,address,"",txFrequency,rxFrequency,receiveAll,requiresPower,autoConnect,true,customAddress,broadcastHook)),Optional.of(new DeviceList.Config(32,true,false)),Optional.of(new DeviceComposition.SourceConfig(30,Map.of())),Optional.of(new DeviceComposition.SinkConfig(configuredLimit)),connectionPresent?Optional.of(new DeviceComposition.ConnectionConfig(wired,wireless,range)):Optional.empty(),overloadSound);}
        public boolean available(){return true;} public boolean powered(){return powered;} public DeviceList deviceList(){return list;} public boolean mutateDeviceListMembers(DeviceList replacement){list=replacement;return true;} public void receive(DevicePayload payload,String source){received.add(payload);onReceive.accept(payload);} public void onOverload(){overloaded++;}
        public boolean ownsAddressOutsideWorldSavedData(){return externalAddressOwner;}
    }
    @Test void enqueueIsDeferredAndRoutesByNumericNetworkFrequencyAndRange(){
        var transport=new DeviceNetworkTransport(); var sender=new Endpoint("source",0,30); var target=new Endpoint("target",20,30); var far=new Endpoint("far",40,30);
        transport.register(sender);transport.register(target);transport.register(far);
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"source","",true,new SignalPayload("Pressed",DeviceSignal.MOMENTARY))));
        assertTrue(target.received.isEmpty()); assertEquals(1,transport.drain()); assertEquals(1,target.received.size()); assertTrue(far.received.isEmpty());
    }
    @Test void scopedDeliveryObserverDistinguishesPowerVetoFromEndpointCallAndCanBeClosed() throws Exception {
        var transport=new DeviceNetworkTransport();var sender=new Endpoint("sender",0,30);var target=new Endpoint("target",1,30);
        transport.register(sender);target.requiresPower=true;transport.register(target);target.powered=false;
        var attempts=new ArrayList<DeviceNetworkTransport.DeliveryAttempt>();
        AutoCloseable observer=transport.observeDelivery(sender.id,target.id,attempts::add);
        var packet=new DeviceNetworkPacket(sender.id,2,1280,"sender","",true,new SignalPayload("Pressed",DeviceSignal.MOMENTARY));
        assertTrue(transport.enqueue(packet));assertEquals(0,transport.drain());
        assertEquals(DeviceNetworkTransport.DeliveryOutcome.RECEIVER_UNPOWERED,attempts.getFirst().outcome());
        target.powered=true;assertTrue(transport.enqueue(packet));assertEquals(1,transport.drain());
        assertEquals(DeviceNetworkTransport.DeliveryOutcome.ENDPOINT_CALLED,attempts.getLast().outcome());
        observer.close();assertTrue(transport.enqueue(packet));sender.txFrequency=1300;assertEquals(0,transport.drain());
        assertEquals(2,attempts.size(),"closed observer receives no further notifications");
        assertEquals(1,target.received.size());
    }
    @Test void ordinaryBroadcastListenersReceiveEvenWhenReceiveAllIsFalse(){
        var transport=new DeviceNetworkTransport();var sender=new Endpoint("sender",0,30);var target=new Endpoint("target",1,30);target.receiveAll=false;
        transport.register(sender);transport.register(target);
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"sender","",true,new SignalPayload("Pressed",DeviceSignal.MOMENTARY))));
        assertEquals(1,transport.drain());assertEquals(1,target.received.size());
    }
    @Test void graphRemembersOnlyMaintainedSignalsAndRetiresDirectedEdges(){
        var graph=new DeviceLinkGraph(); var source=new DeviceLinkGraph.PortRef(DeviceIdentity.random(),"On"); var sink=new DeviceLinkGraph.PortRef(DeviceIdentity.random(),"Toggle");
        var link=new DeviceLinkGraph.Link(source,sink); graph.link(link); graph.signal(source,DeviceSignal.HIGH); assertEquals(DeviceSignal.HIGH,graph.remembered(source).orElseThrow());
        graph.signal(source,DeviceSignal.MOMENTARY); assertEquals(DeviceSignal.HIGH,graph.remembered(source).orElseThrow()); graph.signal(source,DeviceSignal.UNKNOWN); assertTrue(graph.remembered(source).isEmpty());
        graph.link(link); graph.retire(sink.device()); assertTrue(graph.links().isEmpty());
    }
    @Test void queuedPacketIsDiscardedWhenSenderAddressChangesBeforeDrain(){
        var transport=new DeviceNetworkTransport(); var sender=new Endpoint("old-address",0,30); var target=new Endpoint("target",5,30);
        transport.register(sender); transport.register(target);
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"old-address","",true,new SignalPayload("Pressed",DeviceSignal.MOMENTARY))));
        sender.address="new-address";
        assertTrue(transport.register(sender));
        assertEquals(0,transport.drain()); assertTrue(target.received.isEmpty());
    }
    @Test void queuedPacketIsDiscardedWhenSenderFrequencyOrNetworkChangesBeforeDrain(){
        for (boolean changeFrequency : List.of(true,false)) {
            var transport=new DeviceNetworkTransport(); var sender=new Endpoint("sender",0,30); var target=new Endpoint("target",1,30);
            transport.register(sender);transport.register(target);
            assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"sender","",true,new SignalPayload("Pressed",DeviceSignal.MOMENTARY))));
            if(changeFrequency)sender.txFrequency=1300;else sender.networkId=3;
            assertEquals(0,transport.drain()); assertTrue(target.received.isEmpty());
        }
    }
    @Test void deviceLinkUsesWirelessOverrideAndTargetReceiveFrequency(){
        var transport=new DeviceNetworkTransport(); var runtimeLinks=new DeviceLinkService(transport);
        var source=new Endpoint("source",0,30); source.networkId=3; source.txFrequency=0; var target=new Endpoint("target",10,30); target.networkId=2; target.rxFrequency=1621; target.receiveAll=false;
        transport.register(source); transport.register(target); var graph=new DeviceLinkGraph();
        assertTrue(runtimeLinks.link(graph,source,"Pressed",target,"Toggle"));
        assertTrue(runtimeLinks.invoke(graph,source,"Pressed",DeviceSignal.MOMENTARY));
        assertEquals(1,transport.queuedPackets()); assertEquals(1,transport.drain()); assertEquals(1,target.received.size());
    }
    @Test void replayOnNewLinkUsesQueuedInvokePath(){
        var transport=new DeviceNetworkTransport(); var links=new DeviceLinkService(transport); var source=new Endpoint("source",0,30); var target=new Endpoint("target",1,30); target.receiveAll=false;
        transport.register(source);transport.register(target);var graph=new DeviceLinkGraph();
        var from=new DeviceLinkGraph.PortRef(source.id,"Pressed");graph.signal(from,DeviceSignal.HIGH);
        assertTrue(links.link(graph,source,"Pressed",target,"Toggle")); assertEquals(1,transport.queuedPackets());
        assertTrue(target.received.isEmpty());assertEquals(1,transport.drain());assertEquals(1,target.received.size());
    }
    @Test void clearingRememberedSignalDoesNotDeliverOrChargeAndNewLinkDoesNotReplay(){
        var transport=new DeviceNetworkTransport();var links=new DeviceLinkService(transport);var source=new Endpoint("source",0,30);var target=new Endpoint("target",1,30);target.configuredLimit=1;
        transport.register(source);transport.register(target);var graph=new DeviceLinkGraph();var from=new DeviceLinkGraph.PortRef(source.id,"Pressed");
        graph.signal(from,DeviceSignal.HIGH);
        assertTrue(links.link(graph,source,"Pressed",target,"Toggle"));assertEquals(1,transport.queuedPackets());
        assertTrue(links.invoke(graph,source,"Pressed",DeviceSignal.UNKNOWN));
        assertTrue(graph.remembered(from).isEmpty());assertEquals(1,transport.queuedPackets());assertEquals(0,target.overloaded);
        assertTrue(links.invoke(graph,source,"Pressed",DeviceSignal.MOMENTARY));
        assertEquals(2,transport.queuedPackets(),"clear must not charge the sink invocation limit");assertEquals(0,target.overloaded);

        var freshGraph=new DeviceLinkGraph();var freshFrom=new DeviceLinkGraph.PortRef(source.id,"Pressed");
        freshGraph.signal(freshFrom,DeviceSignal.HIGH);links.invoke(freshGraph,source,"Pressed",DeviceSignal.UNKNOWN);
        assertTrue(links.link(freshGraph,source,"Pressed",target,"Toggle"));
        assertEquals(2,transport.queuedPackets(),"linking after clear has no remembered signal to replay");
    }
    @Test void existingLowSignalStillReplaysOnNewLink(){
        var transport=new DeviceNetworkTransport();var links=new DeviceLinkService(transport);var source=new Endpoint("source",0,30);var target=new Endpoint("target",1,30);
        transport.register(source);transport.register(target);var graph=new DeviceLinkGraph();graph.signal(new DeviceLinkGraph.PortRef(source.id,"Pressed"),DeviceSignal.LOW);
        assertTrue(links.link(graph,source,"Pressed",target,"Toggle"));assertEquals(1,transport.queuedPackets());
    }
    @Test void addressedPacketsAlsoReachReceiveAllListeners(){
        var transport=new DeviceNetworkTransport(); var sender=new Endpoint("sender",0,30); var addressed=new Endpoint("dest",1,30); addressed.receiveAll=false; var monitor=new Endpoint("monitor",2,30);
        transport.register(sender);transport.register(addressed);transport.register(monitor);
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"sender","dest",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(2,transport.drain()); assertEquals(1,addressed.received.size()); assertEquals(1,monitor.received.size());
    }
    @Test void addressedWiredOnlyReceiversWorkAndWrongNetworkAddressesDoNot(){
        var transport=new DeviceNetworkTransport();var sender=new Endpoint("sender",0,30);var target=new Endpoint("target",1,30);
        sender.wired=true;target.wired=true;target.receiveAll=false;transport.register(sender);transport.register(target);
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"sender","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(1,transport.drain());assertEquals(1,target.received.size());
        var wrong=new Endpoint("target",1,30);wrong.networkId=4;wrong.wired=true;wrong.receiveAll=false;
        var other=new DeviceNetworkTransport();other.register(sender);other.register(wrong);
        assertTrue(other.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"sender","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(0,other.drain());assertTrue(wrong.received.isEmpty());
    }
    @Test void receiverConnectionComponentsAreIndependentVetoHooks(){
        var sender=new Endpoint("sender",0,1);var target=new Endpoint("target",10,30);sender.wired=true;sender.wireless=true;target.wired=true;target.wireless=true;target.receiveAll=false;
        var dual=new DeviceNetworkTransport();dual.register(sender);dual.register(target);
        assertTrue(dual.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"sender","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(0,dual.drain(),"same-grid wired connection cannot bypass failed wireless range");

        var wirelessSender=new Endpoint("sender",0,30);var wiredReceiver=new Endpoint("target",1,30);wiredReceiver.wired=true;wiredReceiver.wireless=false;wiredReceiver.receiveAll=false;
        var wiredReceiverTransport=new DeviceNetworkTransport();wiredReceiverTransport.register(wirelessSender);wiredReceiverTransport.register(wiredReceiver);
        assertTrue(wiredReceiverTransport.enqueue(new DeviceNetworkPacket(wirelessSender.id,2,1280,"sender","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(1,wiredReceiverTransport.drain(),"wired receiver accepts same-grid sender without a wired component");

        var noHooksSender=new Endpoint("sender",0,1);var noHooksTarget=new Endpoint("target",100,30);noHooksTarget.receiveAll=false;noHooksSender.connectionPresent=false;noHooksTarget.connectionPresent=false;
        var noHooks=new DeviceNetworkTransport();noHooks.register(noHooksSender);noHooks.register(noHooksTarget);
        assertTrue(noHooks.enqueue(new DeviceNetworkPacket(noHooksSender.id,2,1280,"sender","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(1,noHooks.drain(),"absent connection components do not impose a transport veto");
    }
    @Test void responseEnqueuedDuringDrainWaitsForNextUpdate(){
        var transport=new DeviceNetworkTransport();var requester=new Endpoint("requester",0,30);var responder=new Endpoint("responder",1,30);transport.register(requester);transport.register(responder);
        responder.onReceive=payload->transport.enqueue(new DeviceNetworkPacket(responder.id,2,1280,"responder","requester",false,new SignalPayload("Status",DeviceSignal.HIGH)));
        assertTrue(transport.enqueue(new DeviceNetworkPacket(requester.id,2,1280,"requester","responder",false,new SignalPayload("Pressed",DeviceSignal.MOMENTARY))));
        assertEquals(1,transport.drain());assertEquals(1,transport.queuedPackets());assertTrue(requester.received.isEmpty());
        assertEquals(1,transport.drain());assertEquals(1,requester.received.size());
    }
    @Test void broadcastHookFiltersRecipientIdentityAndIncomingListFiltersSenderIdentity(){
        var transport=new DeviceNetworkTransport();var sender=new Endpoint("sender",0,30);var included=new Endpoint("included",1,30);var excluded=new Endpoint("excluded",2,30);
        sender.broadcastHook=true;sender.list=new DeviceList(List.of(included.id.value().toString()),32,true,false);
        transport.register(sender);transport.register(included);transport.register(excluded);
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.id,2,1280,"sender","",true,new SignalPayload("Pressed",DeviceSignal.MOMENTARY))));
        assertEquals(1,transport.drain());assertEquals(1,included.received.size());assertTrue(excluded.received.isEmpty());

        var filteredTransport=new DeviceNetworkTransport();var sender2=new Endpoint("sender2",0,30);var target=new Endpoint("target",1,30);
        target.list=new DeviceList(List.of("another-device"),32,true,true);filteredTransport.register(sender2);filteredTransport.register(target);
        assertTrue(filteredTransport.enqueue(new DeviceNetworkPacket(sender2.id,2,1280,"sender2","",true,new SignalPayload("Pressed",DeviceSignal.MOMENTARY))));
        assertEquals(0,filteredTransport.drain());assertTrue(target.received.isEmpty());
    }
    @Test void invalidSourcePortCannotInvokeAndPerEndpointSinkLimitOverloadsAllLinks(){
        var transport=new DeviceNetworkTransport(); var links=new DeviceLinkService(transport); var source=new Endpoint("source",0,30); var target=new Endpoint("target",1,30);
        transport.register(source);transport.register(target);var graph=new DeviceLinkGraph();
        assertFalse(links.invoke(graph,source,"NotARealPort",DeviceSignal.HIGH));
        var from=new DeviceLinkGraph.PortRef(source.id,"Pressed"); graph.link(new DeviceLinkGraph.Link(from,new DeviceLinkGraph.PortRef(target.id,"Toggle")));
        for(int i=0;i<12;i++) links.invoke(graph,source,"Pressed",DeviceSignal.MOMENTARY);
        assertEquals(1,target.overloaded); assertTrue(graph.links().isEmpty());
    }
    @Test void endpointResolvedSinkLimitControlsPreincrementOverloadBoundary(){
        var transport=new DeviceNetworkTransport();var links=new DeviceLinkService(transport);var source=new Endpoint("source",0,30);var target=new Endpoint("target",1,30);target.configuredLimit=1;
        transport.register(source);transport.register(target);var graph=new DeviceLinkGraph();graph.link(new DeviceLinkGraph.Link(new DeviceLinkGraph.PortRef(source.id,"Pressed"),new DeviceLinkGraph.PortRef(target.id,"Toggle")));
        links.invoke(graph,source,"Pressed",DeviceSignal.MOMENTARY);links.invoke(graph,source,"Pressed",DeviceSignal.MOMENTARY);assertEquals(2,transport.queuedPackets());
        links.invoke(graph,source,"Pressed",DeviceSignal.MOMENTARY);assertEquals(1,target.overloaded);assertTrue(graph.links().isEmpty());
    }
    @Test void overloadCallsEndpointAndInjectedSoundEffectOnceBeforeRetiringLinks(){
        var transport=new DeviceNetworkTransport();var sounds=new ArrayList<DeviceComposition.SoundOnOverload>();var links=new DeviceLinkService(transport,(endpoint,effect)->sounds.add(effect));
        var source=new Endpoint("source",0,30);var target=new Endpoint("target",1,30);target.configuredLimit=0;
        var effect=new DeviceComposition.SoundOnOverload(Optional.of(net.minecraft.resources.ResourceLocation.parse("moonstation14:test")),0f);target.overloadSound=Optional.of(effect);
        transport.register(source);transport.register(target);var graph=new DeviceLinkGraph();graph.link(new DeviceLinkGraph.Link(new DeviceLinkGraph.PortRef(source.id,"Pressed"),new DeviceLinkGraph.PortRef(target.id,"Toggle")));
        links.invoke(graph,source,"Pressed",DeviceSignal.MOMENTARY);links.invoke(graph,source,"Pressed",DeviceSignal.MOMENTARY);
        assertEquals(1,target.overloaded);assertEquals(List.of(effect),sounds);assertTrue(graph.links().isEmpty());
    }
    @Test void frequencyPrototypeRuntimeRegistrationIsAvailableInBothRuntimes(){
        assertTrue(PrototypeRuntime.serverManager().registeredTypes().contains(ModDeviceNetworks.FREQUENCY_TYPE));
        assertTrue(PrototypeRuntime.clientManager().registeredTypes().contains(ModDeviceNetworks.NETWORK_TYPE));
    }
}
