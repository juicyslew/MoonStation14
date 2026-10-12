package com.juicyslew.moonstation14.ms14.devicenet;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DeviceLinkSavedDataDirtyTest {
    @Test void exposedGraphMutationsMarkSavedDataDirtyExactlyWhenStateChanges() {
        var data=new DeviceLinkSavedData(); var graph=data.graph();
        var source=new DeviceIdentity(java.util.UUID.randomUUID()); var sink=new DeviceIdentity(java.util.UUID.randomUUID());
        var sourcePort=new DeviceLinkGraph.PortRef(source,"On");
        var edge=new DeviceLinkGraph.Link(sourcePort,new DeviceLinkGraph.PortRef(sink,"Toggle"));
        assertFalse(data.isDirty());
        assertTrue(graph.link(edge)); assertTrue(data.isDirty());
        data.setDirty(false); assertFalse(graph.link(edge)); assertFalse(data.isDirty());

        graph.signal(sourcePort,DeviceSignal.HIGH); assertTrue(data.isDirty());
        data.setDirty(false); graph.signal(sourcePort,DeviceSignal.HIGH); assertFalse(data.isDirty());
        graph.initializeRemembered(source,Map.of("Status",DeviceSignal.LOW)); assertTrue(data.isDirty());
        data.setDirty(false); graph.initializeRemembered(source,Map.of("Status",DeviceSignal.HIGH)); assertFalse(data.isDirty());

        // The overload path calls this public graph operation directly; persistence follows it too.
        graph.retireLinksTo(sink); assertTrue(data.isDirty());
        data.setDirty(false); graph.retireLinksTo(sink); assertFalse(data.isDirty());
        graph.retire(source); assertTrue(data.isDirty());
    }

    @Test void loadedGraphIsNotDirtyUntilItsNextDirectMutation() {
        var data=new DeviceLinkSavedData(); var source=new DeviceIdentity(java.util.UUID.randomUUID());
        var sink=new DeviceIdentity(java.util.UUID.randomUUID()); var port=new DeviceLinkGraph.PortRef(source,"Status");
        data.graph().link(new DeviceLinkGraph.Link(port,new DeviceLinkGraph.PortRef(sink,"Toggle")));
        data.graph().signal(port,DeviceSignal.HIGH);
        CompoundTag saved=data.save(new CompoundTag(),null);
        var restored=DeviceLinkSavedData.load(saved,null);
        assertFalse(restored.isDirty());
        assertEquals(DeviceSignal.HIGH,restored.graph().remembered(port).orElseThrow());
        restored.graph().signal(port,DeviceSignal.LOW);
        assertTrue(restored.isDirty());
    }

    @Test void defaultAddressPersistsAcrossReloadAndRetirementForgetsOnlyExistingOwner() {
        var data=new DeviceLinkSavedData();var identity=new DeviceIdentity(java.util.UUID.randomUUID());
        data.rememberDefaultAddress(identity,"AIR-0001-0002");assertTrue(data.isDirty());
        data.setDirty(false);data.rememberDefaultAddress(identity,"AIR-0001-0002");assertFalse(data.isDirty());
        var restored=DeviceLinkSavedData.load(data.save(new CompoundTag(),null),null);
        assertEquals("AIR-0001-0002",restored.defaultAddress(identity).orElseThrow());
        assertFalse(restored.isDirty());
        assertTrue(restored.forgetDefaultAddress(identity));assertTrue(restored.isDirty());
        restored.setDirty(false);assertFalse(restored.forgetDefaultAddress(identity));assertFalse(restored.isDirty());
        assertTrue(restored.defaultAddress(identity).isEmpty());
    }

    @Test void linkServiceOverloadUnlinksThroughDirtyTrackedGraph() {
        var data=new DeviceLinkSavedData();var transport=new DeviceNetworkTransport();var service=new DeviceLinkService(transport);
        var source=new DeviceNetworkCoreTest.Endpoint("source",0,30);var target=new DeviceNetworkCoreTest.Endpoint("target",1,30);
        transport.register(source);transport.register(target);
        var edge=new DeviceLinkGraph.Link(new DeviceLinkGraph.PortRef(source.identity(),"Pressed"),
                new DeviceLinkGraph.PortRef(target.identity(),"Toggle"));
        data.graph().link(edge);data.setDirty(false);
        for(int i=0;i<12;i++)service.invoke(data.graph(),source,"Pressed",DeviceSignal.MOMENTARY);
        assertEquals(1,target.overloaded);assertTrue(data.graph().links().isEmpty());assertTrue(data.isDirty());
    }
}
