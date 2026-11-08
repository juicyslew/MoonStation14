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

public class VentilationPrototypeTest {
    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/ventilation_device/" + name + ".json";
        try (var reader = new InputStreamReader(
                java.util.Objects.requireNonNull(VentilationPrototypeTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Map<ResourceLocation, JsonObject> bundled() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        raw.put(ModVentilationDevices.BASE, resource("gas_unary_base"));
        raw.put(ModVentilationDevices.VENT, resource("gas_vent"));
        raw.put(ModVentilationDevices.SCRUBBER, resource("gas_scrubber"));
        return raw;
    }

    static PrototypeManager manager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        return manager;
    }

    public static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<VentilationDeviceData> resolvedCatalog() {
        PrototypeManager manager = manager();
        manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, bundled());
        return manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
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
        assertEquals(0.6, vent.deviceIntervalSeconds());
        assertEquals(0.6, catalog.get(ModVentilationDevices.SCRUBBER).deviceIntervalSeconds());
        assertEquals(3, vent.extensionCableReceiver().localReachBlocks());
        assertEquals(new VentilationDeviceData.FixtureGeometry(0.75, 0.75, 0.125, true, true), vent.fixtureGeometry());
        assertEquals(101.325, vent.gasVentPump().targetPressureChangeKPaPerSecond());
        assertEquals(101.325, vent.gasVentPump().externalPressureBoundKPa());
        assertEquals(2.0, vent.gasVentPump().pumpPower());
        assertEquals(VentilationDeviceData.PressureChecks.EXTERNAL, vent.gasVentPump().pressureChecks());
        assertEquals(0.0, vent.gasVentPump().internalPressureBoundKPa());
        var cue = vent.ambientAudio().orElseThrow();
        assertEquals("moonstation14:ss14/atmospherics/gas_vent", cue.sound());
        assertEquals(-12f, cue.volumeDb());
        assertEquals(5f, cue.range());
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio.Gate.ENABLED_POWERED, cue.gate());
        assertNull(vent.gasVentScrubber());
        assertEquals("VNT-", vent.deviceComponents().network().orElseThrow().addressPrefix());
        assertEquals(java.util.Set.of("GasVent"), vent.atmosAlarmable().sourceTags());
        assertTrue(vent.deviceComponents().network().orElseThrow().broadcastHook());
        assertFalse(vent.deviceComponents().network().orElseThrow().requiresPower());
        assertTrue(vent.deviceComponents().connection().orElseThrow().wired());
        assertFalse(vent.deviceComponents().connection().orElseThrow().wireless());
        var scrubber = catalog.get(ModVentilationDevices.SCRUBBER);
        assertEquals(0.2, scrubber.gasVentScrubber().volumeRateM3PerSecond());
        assertEquals(cue, scrubber.ambientAudio().orElseThrow());
        assertEquals(Ss14VentilationDefaults.DEFAULT_SCRUBBED_GASES,
                java.util.Set.copyOf(scrubber.gasVentScrubber().filteredGases()));
        assertNull(scrubber.gasVentPump());
        assertEquals("SCR-", scrubber.deviceComponents().network().orElseThrow().addressPrefix());
        assertEquals(java.util.Set.of("GasScrubber"), scrubber.atmosAlarmable().sourceTags());
        PrototypeManager copy = manager();
        copy.publishEncodedCatalogs(manager.encodePublishedCatalogs());
        assertEquals(catalog.asMap(), copy.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).asMap());
    }

    @Test void childPumpOverrideResolvesAndFeedsLiveConfigurationDefaults() {
        var raw = bundled();
        var pump = raw.get(ModVentilationDevices.VENT).getAsJsonObject("gasVentPump");
        pump.addProperty("pumpPower", 3.0);
        pump.addProperty("pressureChecks", "BOTH");
        pump.addProperty("internalPressureBoundKPa", 12.0);
        PrototypeManager manager = manager();
        manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, raw);
        var resolved = manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).get(ModVentilationDevices.VENT);
        assertEquals(3, resolved.gasVentPump().pumpPower());
        assertEquals(VentilationDeviceData.PressureChecks.BOTH, resolved.gasVentPump().pressureChecks());
        assertEquals(12, resolved.gasVentPump().internalPressureBoundKPa());
        assertTrue(new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, resolved).settings().configuredEnabled());
    }

    @Test void optionalCueCanBeAbsentInOlderDefinitions() {
        var raw = bundled();
        raw.get(ModVentilationDevices.BASE).remove("ambientAudio");
        PrototypeManager manager = manager();
        manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, raw);
        assertTrue(manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).get(ModVentilationDevices.VENT).ambientAudio().isEmpty());
    }

    @Test void rejectsInvalidAmbientCue() {
        var raw = bundled();
        raw.get(ModVentilationDevices.BASE).getAsJsonObject("ambientAudio").addProperty("range", -1);
        rejects(raw);
    }

    @Test void intervalIsInheritedCanBeOverriddenAndMustBeFinitePositive() {
        var raw = bundled();
        raw.get(ModVentilationDevices.VENT).addProperty("deviceIntervalSeconds", 0.3);
        PrototypeManager manager = manager();
        manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, raw);
        var resolved = manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        assertEquals(0.3, resolved.get(ModVentilationDevices.VENT).deviceIntervalSeconds());
        assertEquals(0.6, resolved.get(ModVentilationDevices.SCRUBBER).deviceIntervalSeconds());
        raw = bundled();
        raw.get(ModVentilationDevices.BASE).addProperty("deviceIntervalSeconds", Double.POSITIVE_INFINITY);
        var infinite = raw;
        rejects(infinite);
        raw = bundled();
        raw.get(ModVentilationDevices.BASE).addProperty("deviceIntervalSeconds", 0);
        rejects(raw);
    }

    @Test void childAddressPrefixOverridePreservesInheritedNetworkPolicy() {
        var raw = bundled();
        var baseNetwork = raw.get(ModVentilationDevices.BASE).getAsJsonObject("deviceComponents")
                .getAsJsonObject("deviceNetwork");
        baseNetwork.addProperty("transmitFrequency", "moonstation14:atmos_monitor");
        baseNetwork.addProperty("receiveFrequency", "moonstation14:atmos_monitor");
        PrototypeManager manager = manager();
        manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, raw);
        var network = manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE)
                .get(ModVentilationDevices.VENT).deviceComponents().network().orElseThrow();
        assertEquals("VNT-", network.addressPrefix());
        assertTrue(network.transmit());
        assertTrue(network.receive());
        assertEquals(ResourceLocation.parse("moonstation14:atmos_devices"), network.networkPrototypeId());
        assertEquals(ResourceLocation.parse("moonstation14:atmos_monitor"), network.transmitFrequencyPrototypeId().orElseThrow());
        assertEquals(ResourceLocation.parse("moonstation14:atmos_monitor"), network.receiveFrequencyPrototypeId().orElseThrow());
        assertTrue(network.broadcastHook());
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
