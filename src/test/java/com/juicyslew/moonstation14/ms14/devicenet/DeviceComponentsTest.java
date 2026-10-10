package com.juicyslew.moonstation14.ms14.devicenet;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeviceComponentsTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static PrototypeManager manager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModDeviceNetworks.NETWORK_TYPE); manager.register(ModDeviceNetworks.FREQUENCY_TYPE); manager.register(ModDeviceNetworks.PORT_TYPE);
        manager.publishDecoded(ModDeviceNetworks.NETWORK_TYPE, Map.of(id("atmos_devices"), new DeviceNetworkPrototype(4)));
        manager.publishDecoded(ModDeviceNetworks.FREQUENCY_TYPE, Map.of(id("atmos_monitor"), new DeviceFrequency(1621)));
        manager.publishDecoded(ModDeviceNetworks.PORT_TYPE, Map.of(
                id("pressed"), new DevicePortPrototype("Pressed", "Pressed", "", DevicePortPrototype.Direction.SOURCE, java.util.List.of()),
                id("door_status"), new DevicePortPrototype("DoorStatus", "Door status", "", DevicePortPrototype.Direction.SOURCE, java.util.List.of()),
                id("toggle"), new DevicePortPrototype("Toggle", "Toggle", "", DevicePortPrototype.Direction.SINK, java.util.List.of())));
        return manager;
    }
    @Test void codecDefaultsAndPrototypeResolutionUseCatalogValues() {
        var data = DeviceComponents.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"deviceNetwork":{"network":"moonstation14:atmos_devices","transmitFrequency":"moonstation14:atmos_monitor","receiveFrequency":"moonstation14:atmos_monitor","addressPrefix":"alarm"},
                 "deviceConnection":{"wired":true,"wireless":true,"wirelessRange":200},
                 "deviceLinkSource":{"ports":["moonstation14:door_status"],"lastSignals":{"moonstation14:door_status":"LOW"}},
                 "deviceLinkSink":{"ports":["moonstation14:toggle"]},"deviceList":{}}
                """)).getOrThrow(IllegalArgumentException::new);
        assertTrue(data.network().orElseThrow().autoConnect()); assertTrue(data.network().orElseThrow().savableAddress());
        var composition = data.resolve(manager());
        assertEquals(4, composition.network().orElseThrow().networkId());
        assertEquals(1621, composition.network().orElseThrow().transmitFrequency());
        assertEquals("", composition.network().orElseThrow().address());
        assertEquals("alarm", composition.network().orElseThrow().addressPrefix());
        assertEquals("generated-address", composition.withAddress("generated-address").network().orElseThrow().address());
        assertTrue(composition.connection().orElseThrow().wired()); assertEquals(200, composition.connection().orElseThrow().wirelessRange());
        assertEquals(DeviceSignal.LOW, composition.sourceConfig().orElseThrow().lastSignals().get("DoorStatus"));
        assertEquals(30, composition.sourceConfig().orElseThrow().range());
        assertEquals(10, composition.sinkConfig().orElseThrow().limit());
        assertEquals(new DeviceList.Config(32, true, false), composition.deviceList().orElseThrow());
        assertTrue(DeviceComponents.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow(IllegalArgumentException::new).resolve(manager()).sources().isEmpty());
        var graph = new DeviceLinkGraph(); var identity = DeviceIdentity.random();
        graph.initializeRemembered(identity, composition.sourceConfig().orElseThrow().lastSignals());
        graph.signal(new DeviceLinkGraph.PortRef(identity, "DoorStatus"), DeviceSignal.HIGH);
        graph.initializeRemembered(identity, composition.sourceConfig().orElseThrow().lastSignals());
        assertEquals(DeviceSignal.HIGH, graph.remembered(new DeviceLinkGraph.PortRef(identity, "DoorStatus")).orElseThrow());
    }

    @Test void unknownReferencesAndWrongPortDirectionsFailClosed() {
        var manager = manager();
        var unknown = new DeviceComponents(Optional.empty(), Optional.empty(), Optional.of(new DeviceComponents.Source(java.util.List.of(id("missing")), 30, Map.of())), Optional.empty(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> unknown.resolve(manager));
        var wrongDirection = new DeviceComponents(Optional.empty(), Optional.empty(), Optional.of(new DeviceComponents.Source(java.util.List.of(id("toggle")), 30, Map.of())), Optional.empty(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> wrongDirection.resolve(manager));
        var unknownNetwork = new DeviceComponents(Optional.of(new DeviceComponents.Network(id("missing"), true, true, Optional.empty(), Optional.empty(), "", true, true, false, false, false, false)), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> unknownNetwork.resolve(manager));
    }

    @Test void inheritedComponentJsonDecodesWithTypedDefaults() {
        var parent = JsonParser.parseString("{\"id\":\"base\",\"deviceComponents\":{\"deviceNetwork\":{\"network\":\"moonstation14:atmos_devices\"}}}").getAsJsonObject();
        var child = JsonParser.parseString("{\"id\":\"child\",\"parent\":\"base\"}").getAsJsonObject();
        var resolved = PrototypeResolver.resolve(Map.of(id("base"), parent, id("child"), child));
        var inherited = DeviceComponents.CODEC.parse(JsonOps.INSTANCE, resolved.get(id("child")).getAsJsonObject().get("deviceComponents")).getOrThrow(IllegalArgumentException::new);
        var composition = inherited.resolve(manager());
        assertEquals(4, composition.network().orElseThrow().networkId());
        assertTrue(composition.network().orElseThrow().autoConnect());
        assertTrue(composition.network().orElseThrow().savableAddress());
    }
}
