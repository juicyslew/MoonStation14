package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineSettingsTest {
    private static final String[] IDS = {"atmos_machine_base", "atmos_machine_base_powered", "constructible_machine_base",
            "gas_pipe_sans_layers", "gas_pipe_base", "gas_binary_base", "gas_trinary_base", "gas_thermo_base",
            "teg_center", "teg_circulator", "gas_pressure_pump", "gas_volume_pump", "gas_valve", "gas_mixer",
            "gas_filter", "gas_thermo_heater", "gas_thermo_freezer", "passive_heat_exchanger"};

    static AtmosMachineProfile profile(String id) {
        var manager = new PrototypeManager(); manager.register(ModAtmosMachines.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : IDS) {
            String path = "/data/moonstation14/moonstation14/atmos_machine/" + name + ".json";
            try (var stream = AtmosMachineSettingsTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                raw.put(ModAtmosMachines.id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        }
        manager.reload(ModAtmosMachines.TYPE, raw);
        return manager.snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id(id));
    }

    @Test void defaultsComeFromResolvedComponentsAndRatesConvertLitresPerSecond() {
        var pressure = AtmosMachineSettings.defaults(profile("gas_pressure_pump"));
        assertEquals(Optional.of(false), pressure.enabled());
        assertEquals(Optional.of(101.325), pressure.targetPressureKpa());
        var volume = AtmosMachineSettings.defaults(profile("gas_volume_pump"));
        assertEquals(Optional.of(0.2), volume.transferRateM3PerSecond());
        var filterProfile = profile("gas_filter");
        var filter = AtmosMachineSettings.defaults(filterProfile);
        assertEquals(Optional.of(1d), filter.transferRateM3PerSecond());
        assertTrue(filter.filterGas().isEmpty());
        var selected = AtmosMachineSettings.apply(filterProfile, filter,
                AtmosMachineSettings.Operation.SET_FILTER_GAS, new AtmosMachineSettings.GasValue(GasType.OXYGEN));
        assertEquals(Optional.of(GasType.OXYGEN), selected.filterGas());
        assertEquals(Optional.of(false), selected.enabled());
        assertEquals(filter.revision() + 1, selected.revision());
        assertTrue(AtmosMachineSettings.defaults(filterProfile).filterGas().isEmpty());
        assertTrue(filterProfile.filter().gas().isEmpty());
        assertEquals(Optional.of(true), AtmosMachineSettings.defaults(profile("gas_valve")).valveOpen());
        assertEquals(Optional.of(true), AtmosMachineSettings.defaults(profile("teg_center")).enabled());
    }

    @Test void operationsAreCapabilityCheckedAndBoundedThenRoundTrip() {
        var pump = profile("gas_pressure_pump");
        var initial = AtmosMachineSettings.defaults(pump);
        var changed = AtmosMachineSettings.apply(pump, initial, AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA,
                new AtmosMachineSettings.NumericValue(4500d));
        assertEquals(1, changed.revision());
        assertThrows(IllegalArgumentException.class, () -> AtmosMachineSettings.apply(pump, changed,
                AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA, new AtmosMachineSettings.NumericValue(4500.1d)));
        assertThrows(IllegalArgumentException.class, () -> AtmosMachineSettings.apply(pump, changed,
                AtmosMachineSettings.Operation.SET_MIXER_FRACTION_A, new AtmosMachineSettings.NumericValue(0.5d)));
        var loaded = AtmosMachineSettings.load(pump, AtmosMachineSettings.save(pump, changed));
        assertEquals(changed, loaded);

        var mixer = profile("gas_mixer");
        var fractions = AtmosMachineSettings.apply(mixer, AtmosMachineSettings.defaults(mixer),
                AtmosMachineSettings.Operation.SET_MIXER_FRACTION_A, new AtmosMachineSettings.NumericValue(0.25d));
        assertEquals(0.75d, fractions.mixerFractionB());
        var filter = profile("gas_filter");
        var selected = AtmosMachineSettings.apply(filter, AtmosMachineSettings.defaults(filter),
                AtmosMachineSettings.Operation.SET_FILTER_GAS, new AtmosMachineSettings.GasValue(GasType.PLASMA));
        assertEquals(java.util.Optional.of(GasType.PLASMA), selected.filterGas());
        assertEquals(selected, AtmosMachineSettings.load(filter, AtmosMachineSettings.save(filter, selected)));
        assertThrows(IllegalArgumentException.class, () -> AtmosMachineSettings.apply(filter, selected,
                AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND,
                new AtmosMachineSettings.NumericValue(1.0001d)));
        assertThrows(IllegalArgumentException.class, () -> AtmosMachineSettings.apply(profile("passive_heat_exchanger"),
                AtmosMachineSettings.defaults(profile("passive_heat_exchanger")),
                AtmosMachineSettings.Operation.SET_ENABLED, new AtmosMachineSettings.BooleanValue(true)));
    }

    @Test void thermostatUsesResolvedTemperatureBounds() {
        var freezer = profile("gas_thermo_freezer");
        var initial = AtmosMachineSettings.defaults(freezer);
        assertEquals(java.util.Optional.of(293.15), initial.thermostatSetpointK());
        assertThrows(IllegalArgumentException.class, () -> AtmosMachineSettings.apply(freezer, initial,
                AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K, new AtmosMachineSettings.NumericValue(73d)));
    }
}
