package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VentilationDeviceConfigTest {
    @Test
    void boundPrototypeChangesOnlyLegacyDefaultsAndMissingPrototypeDisablesThem() {
        var resolved = ventilationPrototype();
        var definition = new VentilationDeviceData(resolved.kind(), VentilationDeviceData.PipeLane.SECONDARY,
                resolved.apcPowerReceiver(), resolved.extensionCableReceiver(),
                new VentilationDeviceData.GasVentPump(12, 30), null, resolved.fixtureGeometry(),
                resolved.deviceComponents(), resolved.atmosMonitor(), resolved.atmosAlarmable());
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, null);
        assertFalse(config.settings().configuredEnabled());
        assertEquals(0, config.receiverDemandWatts());
        config.bindPrototype(definition);
        assertEquals(12, config.settings().targetPressureChangeKPaPerSecond());
        assertEquals(30, config.settings().targetPressureKPa());
        assertEquals(VentilationDeviceConfig.Layer.SECONDARY, config.settings().selectedLayer());
        assertEquals(5, config.receiverDemandWatts());
        var saved = config.save();
        saved.putLong("Revision", 7);
        var restored = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, definition);
        assertTrue(restored.restore(saved));
        restored.bindPrototype(null);
        assertEquals(7, restored.settings().revision());
        assertEquals(config.settings().targetPressureKPa(), restored.settings().targetPressureKPa());
        assertEquals(0, restored.receiverDemandWatts());
        config.bindPrototype(null);
        assertFalse(config.settings().configuredEnabled());
        config.bindPrototype(new VentilationDeviceData(VentilationDeviceData.Kind.SCRUBBER,
                VentilationDeviceData.PipeLane.PRIMARY, new VentilationDeviceData.ApcPowerReceiver(5),
                new VentilationDeviceData.ExtensionCableReceiver(3), null,
                new VentilationDeviceData.GasVentScrubber(0.2, java.util.List.of(GasType.CARBON_DIOXIDE)),
                 new VentilationDeviceData.FixtureGeometry(0.75, 0.75, 0.125, true, true)));
        assertFalse(config.settings().configuredEnabled());
    }

    private static VentilationDeviceData ventilationPrototype() {
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        var type = com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices.VENTILATION_DEVICE_TYPE;
        manager.register(type);
        var raw = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, com.google.gson.JsonObject>();
        for (String name : java.util.List.of("gas_unary_base", "gas_vent", "gas_scrubber")) {
            String path = "/data/moonstation14/moonstation14/ventilation_device/" + name + ".json";
            try (var stream = VentilationDeviceConfigTest.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing test resource " + path);
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var id = switch (name) {
                    case "gas_unary_base" -> com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices.BASE;
                    case "gas_vent" -> com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices.VENT;
                    default -> com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices.SCRUBBER;
                };
                raw.put(id, json);
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
        manager.reload(type, raw);
        return manager.snapshot(type).get(com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices.VENT);
    }

    @Test
    void exposesSourceDefaultsAndFixedReceiverDemand() {
        var vent = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        var pump = vent.settings();
        assertTrue(pump.configuredEnabled());
        assertEquals(101.325, pump.externalPressureBoundKPa());
        assertEquals(101.325, pump.targetPressureChangeKPaPerSecond());
        assertEquals(4500.0, Ss14VentilationDefaults.MAXIMUM_TARGET_PRESSURE_KPA);
        assertEquals(5.0, vent.receiverDemandWatts());
        var scrubber = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        assertTrue(scrubber.settings().configuredEnabled());
        assertEquals(0.2, scrubber.settings().volumeRateM3PerSecond());
        assertEquals(Ss14VentilationDefaults.DEFAULT_SCRUBBED_GASES, scrubber.settings().gasesToScrub());
        assertFalse(scrubber.settings().gasesToScrub().contains(GasType.OXYGEN));
        assertFalse(scrubber.settings().gasesToScrub().contains(GasType.NITROGEN));
    }

    @Test
    void enablingDoesNotAlterFixedReceiverDemandAndSiphonMeansAllSpecies() {
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        var original = config.settings();
        var disabled = new VentilationDeviceConfig.Settings(0, false, 0, 0, 0, 0,
                VentilationDeviceConfig.ScrubberMode.FILTER, original.filteredGases());
        assertTrue(config.compareAndSet(0, disabled));
        assertEquals(5.0, config.receiverDemandWatts());
        var siphon = new VentilationDeviceConfig.Settings(1, true, 0, 0, 0, 0,
                VentilationDeviceConfig.ScrubberMode.SIPHON, original.filteredGases());
        assertTrue(config.compareAndSet(1, siphon));
        assertEquals(Set.of(GasType.values()), config.settings().gasesToScrub());
    }

    @Test
    void rejectsInvalidValuesAndStaleRevision() {
        assertThrows(IllegalArgumentException.class, () -> new VentilationDeviceConfig.Settings(0, true,
                -1, 0, 0, 0, VentilationDeviceConfig.ScrubberMode.FILTER, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new VentilationDeviceConfig.Settings(0, true,
                0, Double.NaN, 0, 0, VentilationDeviceConfig.ScrubberMode.FILTER, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new VentilationDeviceConfig.Settings(0, true,
                0, 0, 4500.01, 0, VentilationDeviceConfig.ScrubberMode.FILTER, Set.of()));
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        var current = config.settings();
        assertTrue(config.compareAndSet(0, current));
        assertFalse(config.compareAndSet(0, current));
        assertEquals(1, config.settings().revision());
    }

    @Test
    void selectedLayerChangesOnlyOnCurrentRevisionAndDefaultsPrimary() {
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        assertEquals(VentilationDeviceConfig.Layer.PRIMARY, config.settings().selectedLayer());
        var current = config.settings();
        var secondary = new VentilationDeviceConfig.Settings(current.revision(), current.configuredEnabled(),
                current.externalPressureBoundKPa(), current.targetPressureChangeKPaPerSecond(), current.targetPressureKPa(),
                current.volumeRateM3PerSecond(), current.scrubberMode(), current.filteredGases(), VentilationDeviceConfig.Layer.SECONDARY);
        assertTrue(config.compareAndSet(0, secondary));
        assertFalse(config.compareAndSet(0, secondary));
        assertEquals(VentilationDeviceConfig.Layer.SECONDARY, config.settings().selectedLayer());
        assertEquals(1, config.settings().revision());
    }

    @Test
    void refusesCasAtMaximumRevisionWithoutOverflow() {
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        var tag = config.save();
        tag.putLong("Revision", Long.MAX_VALUE);
        assertTrue(config.restore(tag));
        var current = config.settings();
        assertFalse(config.compareAndSet(Long.MAX_VALUE, current));
        assertEquals(Long.MAX_VALUE, config.settings().revision());
        assertTrue(config.settings().configuredEnabled());
    }
}
