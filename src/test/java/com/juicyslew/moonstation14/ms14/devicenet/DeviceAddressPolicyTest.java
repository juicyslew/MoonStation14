package com.juicyslew.moonstation14.ms14.devicenet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceAddressPolicyTest {
    @Test void generatedFormatRetainsResolvedPrefix() {
        assertEquals("AIR-0000-0000",DeviceAddressPolicy.generate("AIR-",()->0));
        assertEquals("0000-0000",DeviceAddressPolicy.generate("",()->0));
    }
    @Test void autoConnectFalseRequiresExplicitMembershipAndDisconnectRemovesIt() {
        var transport=new DeviceNetworkTransport(null,()->0);var endpoint=new DeviceNetworkCoreTest.Endpoint("",0,20);endpoint.autoConnect=false;
        assertTrue(transport.register(endpoint));
        DeviceEndpoint loaded=transport.lookup(endpoint.identity());
        String address=loaded.composition().network().orElseThrow().address();
        assertEquals("0000-0000",address);
        assertFalse(transport.isConnected(endpoint.identity(),2));
        assertFalse(transport.enqueue(new DeviceNetworkPacket(endpoint.identity(),2,1280,address,"",true,new SignalPayload("Pressed",DeviceSignal.MOMENTARY))));
        assertTrue(transport.connect(endpoint.identity()));assertTrue(transport.isConnected(endpoint.identity(),2));
        assertTrue(transport.disconnect(endpoint.identity()));assertFalse(transport.isConnected(endpoint.identity(),2));
    }

    @Test void customAddressIsPreservedAndDuplicateRegistrationIsRefused() {
        var transport=new DeviceNetworkTransport();var first=new DeviceNetworkCoreTest.Endpoint("configured",0,20);first.customAddress=true;
        var second=new DeviceNetworkCoreTest.Endpoint("configured",1,20);second.customAddress=true;
        assertTrue(transport.register(first));assertEquals("configured",transport.lookup(first.identity()).composition().network().orElseThrow().address());
        assertFalse(transport.register(second));assertFalse(transport.isConnected(second.identity(),2));
        assertEquals("configured",transport.lookup(second.identity()).composition().network().orElseThrow().address());
    }

    @Test void loadedButDisconnectedReceiverIsNotRoutedUntilManualConnect() {
        var transport=new DeviceNetworkTransport();var sender=new DeviceNetworkCoreTest.Endpoint("sender",0,20);
        var target=new DeviceNetworkCoreTest.Endpoint("target",1,20);target.autoConnect=false;target.receiveAll=false;
        transport.register(sender);transport.register(target);
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"sender","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(0,transport.drain());assertTrue(target.received.isEmpty());
        assertTrue(transport.connect(target.identity()));
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"sender","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(1,transport.drain());assertEquals(1,target.received.size());
    }

    @Test void generatedAddressesAreShortStableAndCollisionRetried() {
        var sequence=new java.util.concurrent.atomic.AtomicInteger();
        var transport=new DeviceNetworkTransport(null,sequence::getAndIncrement);var first=new DeviceNetworkCoreTest.Endpoint("",0,20);var second=new DeviceNetworkCoreTest.Endpoint("",1,20);
        transport.register(first);transport.register(second);
        String a=transport.lookup(first.identity()).composition().network().orElseThrow().address();
        String b=transport.lookup(second.identity()).composition().network().orElseThrow().address();
        assertEquals("0000-0000",a);assertEquals("0000-0001",b);
        assertTrue(a.matches("[0-9A-F]{4}-[0-9A-F]{4}"));
        assertNotEquals(a,b);
    }

    @Test void allocationRetriesConnectedCollisionAndRetainsAddressAfterUnload() {
        var sequence=new java.util.concurrent.atomic.AtomicInteger();
        var transport=new DeviceNetworkTransport(null,()->sequence.getAndIncrement()==0?0:1);
        var first=new DeviceNetworkCoreTest.Endpoint("",0,20);var second=new DeviceNetworkCoreTest.Endpoint("",1,20);
        transport.register(first);transport.register(second);
        assertEquals("0000-0000",transport.lookup(first.identity()).composition().network().orElseThrow().address());
        assertEquals("0000-0001",transport.lookup(second.identity()).composition().network().orElseThrow().address());
        transport.unload(first.identity());transport.register(first);
        assertEquals("0000-0000",transport.lookup(first.identity()).composition().network().orElseThrow().address());
    }

    @Test void preferredDefaultCollisionRegeneratesButDifferentNetworksMayReuse() {
        var saved=new java.util.HashMap<DeviceIdentity,String>();
        var store=new DeviceNetworkTransport.AddressStore(){public java.util.Optional<String> get(DeviceIdentity id){return java.util.Optional.ofNullable(saved.get(id));}public void put(DeviceIdentity id,String a){saved.put(id,a);}};
        var transport=new DeviceNetworkTransport(store,()->2);
        var occupant=new DeviceNetworkCoreTest.Endpoint("0000-0001",0,20);occupant.customAddress=true;
        assertTrue(transport.register(occupant));
        var identity=DeviceIdentity.random();saved.put(identity,"0000-0001");
        assertEquals("0000-0002",transport.allocateDefaultAddress(identity,2,"","0000-0001"));
        assertEquals("0000-0001",transport.allocateDefaultAddress(identity,3,"","0000-0001"));
    }

    @Test void externallyOwnedPreparedAddressNeverEntersWorldSavedAddressStore() {
        var saved=new java.util.HashMap<DeviceIdentity,String>();
        var store=new DeviceNetworkTransport.AddressStore(){public java.util.Optional<String> get(DeviceIdentity id){return java.util.Optional.ofNullable(saved.get(id));}public void put(DeviceIdentity id,String a){saved.put(id,a);}};
        var transport=new DeviceNetworkTransport(store,()->0);
        var owner=new DeviceNetworkCoreTest.Endpoint("remote-0000-0001",0,20);
        owner.externalAddressOwner=true;
        assertTrue(transport.register(owner));
        assertEquals("remote-0000-0001",transport.lookup(owner.identity()).composition().network().orElseThrow().address());
        assertTrue(saved.isEmpty());
    }

    @Test void registeredEndpointForwardsDeviceListMutationToOwner() {
        var transport=new DeviceNetworkTransport();var owner=new DeviceNetworkCoreTest.Endpoint("device",0,20);transport.register(owner);
        var members=new DeviceList(java.util.List.of("member-identity"),32,true,false);
        assertTrue(transport.lookup(owner.identity()).mutateDeviceListMembers(members));
        assertEquals(members,owner.list);
    }

    @Test void addressEditsInvalidateQueuedPacketsAndRequireReconnect() {
        var transport=new DeviceNetworkTransport();var sender=new DeviceNetworkCoreTest.Endpoint("before",0,20);var target=new DeviceNetworkCoreTest.Endpoint("target",1,20);
        transport.register(sender);transport.register(target);
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"before","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        sender.address="after";
        assertEquals(0,transport.drain());assertFalse(transport.isConnected(sender.identity(),2));
        assertFalse(transport.enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"after","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertTrue(transport.connect(sender.identity()));
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"after","target",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(1,transport.drain());assertEquals(1,target.received.size());
        target.address="target-edited";
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"after","target-edited",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(0,transport.drain());assertFalse(transport.isConnected(target.identity(),2));
        assertTrue(transport.connect(target.identity()));
        assertTrue(transport.enqueue(new DeviceNetworkPacket(sender.identity(),2,1280,"after","target-edited",false,new SignalPayload("Toggle",DeviceSignal.HIGH))));
        assertEquals(1,transport.drain());assertEquals(2,target.received.size());
    }
}
