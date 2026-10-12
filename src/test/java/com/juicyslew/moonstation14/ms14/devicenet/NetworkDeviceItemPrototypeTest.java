package com.juicyslew.moonstation14.ms14.devicenet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.devicenet.ModDeviceNetworks;
import com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class NetworkDeviceItemPrototypeTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void bundledNetworkItemsResolveInheritedPoliciesAndComponentRanges() {
        var manager = new PrototypeManager();
        manager.register(ModDeviceNetworks.FREQUENCY_TYPE);
        manager.register(ModDeviceNetworks.NETWORK_TYPE);
        manager.register(ModDeviceNetworks.PORT_TYPE);
        manager.register(ModNetworkDeviceItems.TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.FREQUENCY_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.NETWORK_TYPE);
        PrototypeResourceFixture.loadCatalog(manager, ModDeviceNetworks.PORT_TYPE);
        Map<ResourceLocation, JsonObject> source = new LinkedHashMap<>();
        for (String name : java.util.List.of("localbase", "standardwireless15", "advancedwireless50", "configurator")) {
            String path = "data/moonstation14/moonstation14/network_device_item/" + name + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                source.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) { throw new AssertionError(e); }
        }
        manager.reload(ModNetworkDeviceItems.TYPE, source);
        var resolved = PrototypeResolver.resolve(source, new NetworkDeviceItemMergeStrategy());
        var standard = decode(resolved.get(id("standardwireless15")));
        var network = standard.deviceComponents().resolve(manager).network().orElseThrow();
        assertEquals(15, standard.deviceComponents().resolve(manager).connection().orElseThrow().wirelessRange());
        assertEquals(1280, network.receiveFrequency());
        assertEquals(0, network.transmitFrequency());
        assertEquals(1f, standard.useDelaySeconds().orElseThrow());
        assertEquals("Pressed", standard.signallerPort().orElseThrow());
        var advanced = decode(resolved.get(id("advancedwireless50")));
        assertEquals(50, advanced.deviceComponents().resolve(manager).connection().orElseThrow().wirelessRange());
        assertEquals(network.networkId(), advanced.deviceComponents().resolve(manager).network().orElseThrow().networkId());
        var configurator = decode(resolved.get(id("configurator"))).configurator().orElseThrow();
        assertTrue(configurator.linkDefault());
        assertFalse(configurator.settingsFrequency());
    }

    @Test void policiesRejectInvalidDelayAndAmbiguousRoles() {
        assertTrue(NetworkDeviceItemData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"deviceComponents\":{},\"use_delay_seconds\":-1}" )).error().isPresent());
    }

    private static NetworkDeviceItemData decode(JsonObject json) {
        return NetworkDeviceItemData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new);
    }
}
