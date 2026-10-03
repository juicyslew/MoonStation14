package com.juicyslew.moonstation14.ms14.atmos.piping.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VentilationPrototypeTest {
    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/ventilation_device/" + name + ".json";
        try (var reader = new InputStreamReader(
                java.util.Objects.requireNonNull(VentilationPrototypeTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<ResourceLocation, JsonObject> bundled() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        raw.put(ModVentilationDevices.BASE, resource("gas_unary_base"));
        raw.put(ModVentilationDevices.VENT, resource("gas_vent"));
        raw.put(ModVentilationDevices.SCRUBBER, resource("gas_scrubber"));
        return raw;
    }

    private static PrototypeManager manager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        return manager;
    }

    @Test void bundledDefinitionsResolveAndRoundTrip() {
        PrototypeManager manager = manager();
        manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, bundled());
        var catalog = manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        assertEquals(2, catalog.asMap().size());
        assertNull(catalog.get(ModVentilationDevices.BASE));
        var vent = catalog.get(ModVentilationDevices.VENT);
        assertEquals(VentilationDeviceData.Kind.VENT, vent.kind());
        assertEquals(VentilationDeviceData.PipeLane.PRIMARY, vent.pipeLane());
        assertEquals(5.0, vent.apcPowerReceiver().requestedWatts());
        assertEquals(3, vent.extensionCableReceiver().localReachBlocks());
        assertEquals(new VentilationDeviceData.FixtureGeometry(0.75, 0.75, 0.125, true, true), vent.fixtureGeometry());
        assertEquals(101.325, vent.gasVentPump().targetPressureChangeKPaPerSecond());
        assertEquals(101.325, vent.gasVentPump().externalPressureBoundKPa());
        assertNull(vent.gasVentScrubber());
        var scrubber = catalog.get(ModVentilationDevices.SCRUBBER);
        assertEquals(0.2, scrubber.gasVentScrubber().volumeRateM3PerSecond());
        assertEquals(Ss14VentilationDefaults.DEFAULT_SCRUBBED_GASES,
                java.util.Set.copyOf(scrubber.gasVentScrubber().filteredGases()));
        assertNull(scrubber.gasVentPump());
        PrototypeManager copy = manager();
        copy.publishEncodedCatalogs(manager.encodePublishedCatalogs());
        assertEquals(catalog.asMap(), copy.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).asMap());
    }

    @Test void absentPublishedDefinitionCannotSupplyLiveDefaultsOrReceiverDemand() {
        PrototypeManager isolated = manager();
        isolated.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, bundled());
        var vent = isolated.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).get(ModVentilationDevices.VENT);
        VentilationDeviceConfig config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, vent);
        assertTrue(config.settings().configuredEnabled());
        assertEquals(5, config.receiverDemandWatts());

        isolated.clearPublishedCatalogs();
        assertNull(isolated.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).get(ModVentilationDevices.VENT));
        config.bindPrototype(isolated.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).get(ModVentilationDevices.VENT));
        assertFalse(config.settings().configuredEnabled());
        assertEquals(0, config.receiverDemandWatts());
    }

    @Test void rejectsBadInheritanceAndMissingRequiredIdsWithoutPublishing() {
        PrototypeManager manager = manager();
        var raw = bundled();
        raw.get(ModVentilationDevices.VENT).remove("parent");
        var orphan = raw;
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, orphan));
        raw = bundled();
        raw.remove(ModVentilationDevices.SCRUBBER);
        var missing = raw;
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, missing));
        raw = bundled();
        raw.get(ModVentilationDevices.VENT).add("apcPowerReceiver", resource("gas_unary_base").get("apcPowerReceiver"));
        var override = raw;
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, override));
        assertTrue(manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).asMap().isEmpty());
    }

    @Test void rejectsInvalidReceiversCompositionAndRates() {
        var raw = bundled();
        raw.get(ModVentilationDevices.BASE).getAsJsonObject("extensionCableReceiver").addProperty("localReachBlocks", -1);
        rejects(raw);
        raw = bundled();
        raw.get(ModVentilationDevices.BASE).getAsJsonObject("apcPowerReceiver").addProperty("requestedWatts", 0);
        rejects(raw);
        raw = bundled();
        raw.get(ModVentilationDevices.VENT).add("gasVentScrubber", resource("gas_scrubber").get("gasVentScrubber"));
        rejects(raw);
        raw = bundled();
        raw.get(ModVentilationDevices.SCRUBBER).getAsJsonObject("gasVentScrubber").addProperty("volumeRateM3PerSecond", -0.2);
        rejects(raw);
        raw = bundled();
        raw.get(ModVentilationDevices.VENT).getAsJsonObject("gasVentPump").addProperty("externalPressureBoundKPa", 0);
        rejects(raw);
        raw = bundled();
        raw.get(ModVentilationDevices.SCRUBBER).getAsJsonObject("gasVentScrubber").getAsJsonArray("filteredGases").add("unknown");
        rejects(raw);
    }

    @Test void rejectsUnsupportedDeviceNetworkField() {
        var raw = bundled();
        JsonObject network = new JsonObject();
        network.addProperty("enabled", true);
        raw.get(ModVentilationDevices.BASE).add("deviceNetwork", network);
        rejects(raw);
    }

    private static void rejects(Map<ResourceLocation, JsonObject> raw) {
        PrototypeManager manager = manager();
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, raw));
        assertTrue(manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).asMap().isEmpty());
    }

    @Test void rejectsMalformedEncodedCatalogAtomically() {
        PrototypeManager source = manager();
        source.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, bundled());
        var encoded = new LinkedHashMap<>(source.encodePublishedCatalogs()
                .get(ModVentilationDevices.VENTILATION_DEVICE_TYPE.typeId()));
        encoded.get(ModVentilationDevices.VENT).getAsJsonObject("gasVentPump")
                .addProperty("targetPressureChangeKPaPerSecond", -1);
        PrototypeManager target = manager();
        assertThrows(PrototypeLoadException.class, () -> target.publishEncodedCatalogs(
                Map.of(ModVentilationDevices.VENTILATION_DEVICE_TYPE.typeId(), encoded)));
        assertTrue(target.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).asMap().isEmpty());
    }
}
