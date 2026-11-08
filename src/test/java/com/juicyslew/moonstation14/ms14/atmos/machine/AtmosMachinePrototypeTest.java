package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachinePrototypeTest {
    private static final String[] RESOURCE_IDS = {"atmos_machine_base", "atmos_machine_base_powered",
            "constructible_machine_base", "gas_pipe_sans_layers", "gas_pipe_base", "gas_binary_base",
            "gas_trinary_base", "gas_thermo_base", "teg_center", "teg_circulator", "gas_pressure_pump",
            "gas_volume_pump", "gas_valve", "gas_mixer", "gas_filter", "gas_thermo_heater",
            "gas_thermo_freezer", "passive_heat_exchanger"};
    private static final String[] CONCRETE_IDS = {"teg_center", "teg_circulator", "gas_pressure_pump",
            "gas_volume_pump", "gas_valve", "gas_mixer", "gas_filter", "gas_thermo_heater",
            "gas_thermo_freezer", "passive_heat_exchanger"};
    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/atmos_machine/" + name + ".json";
        try (var in = AtmosMachinePrototypeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    private static PrototypeManager manager() {
        var manager = new PrototypeManager(); manager.register(ModAtmosMachines.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : RESOURCE_IDS)
            raw.put(ModAtmosMachines.id(name), resource(name));
        manager.reload(ModAtmosMachines.TYPE, raw); return manager;
    }
    @Test void allResourcesAreDiscoverableAndTenConcreteProfilesResolve() {
        var rawIds = java.util.Arrays.stream(RESOURCE_IDS).map(ModAtmosMachines::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(18, rawIds.size());
        for (String name : RESOURCE_IDS) assertNotNull(resource(name), "discoverable profile " + name);
        var resolved = manager().snapshot(ModAtmosMachines.TYPE);
        assertEquals(10, resolved.asMap().size());
        for (String name : CONCRETE_IDS) {
            var simulation = resolved.get(ModAtmosMachines.id(name)).simulation();
            assertEquals(0.6, simulation.deviceIntervalSeconds());
            assertEquals(8, simulation.pumpSpeedup());
            assertEquals(30, simulation.tileRadiationLossWatts());
        }
        assertEquals(java.util.Arrays.stream(CONCRETE_IDS).map(ModAtmosMachines::id).collect(java.util.stream.Collectors.toSet()),
                resolved.asMap().keySet());
    }
    @Test void valveActivationCompositionUsesExistingRegisteredCollectionAndCanonicalSamples() throws Exception {
        var soundJson = JsonParser.parseReader(new InputStreamReader(AtmosMachinePrototypeTest.class.getResourceAsStream(
                "/assets/moonstation14/sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(soundJson.has("gas_valve_toggle"));
        var samples = soundJson.getAsJsonObject("gas_valve_toggle").getAsJsonArray("sounds");
        assertEquals(3, samples.size());
        for (int i = 1; i <= 3; i++) {
            var path = "/assets/moonstation14/sounds/ss14/atmos/squeak" + i + ".ogg";
            try (var stream = AtmosMachinePrototypeTest.class.getResourceAsStream(path)) { assertNotNull(stream, path); }
            assertEquals("moonstation14:ss14/atmos/squeak" + i, samples.get(i - 1).getAsString());
        }
        assertNotNull(com.juicyslew.moonstation14.sounds.ModSounds.GAS_VALVE_TOGGLE);
        var provenance = JsonParser.parseReader(new InputStreamReader(AtmosMachinePrototypeTest.class.getResourceAsStream(
                "/assets/moonstation14/power-reactor-provenance/teg/resource-import-manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var assets = provenance.getAsJsonArray("assets");
        var hum = java.util.stream.StreamSupport.stream(assets.spliterator(), false).map(e -> e.getAsJsonObject())
                .filter(e -> e.get("source").getAsString().endsWith("vending_machine_hum.ogg")).findFirst().orElseThrow();
        assertTrue(hum.get("identical").getAsBoolean());
        assertEquals("1f13c219aef1f1aaa6ff3edb03d30222e8555f92cd19cf467a1849602edc3c00", hum.get("sourceSha256").getAsString());
        assertTrue(hum.get("attributionStatus").getAsString().contains("unknown"));
    }
    @Test void resolvedAmbientCompositionAndProductionEligibilityGates() {
        var profiles = manager().snapshot(ModAtmosMachines.TYPE);
        var pump = profiles.get(ModAtmosMachines.id("gas_pressure_pump"));
        var mixer = profiles.get(ModAtmosMachines.id("gas_mixer"));
        var valve = profiles.get(ModAtmosMachines.id("gas_valve"));
        var teg = profiles.get(ModAtmosMachines.id("teg_center"));
        assertEquals("moonstation14:ss14/atmospherics/gas_pump", pump.ambientAudio().orElseThrow().sound());
        assertEquals(-9f, pump.ambientAudio().orElseThrow().volumeDb()); assertEquals(5f, pump.ambientAudio().orElseThrow().range());
        assertFalse(AtmosAmbientEligibility.eligible(pump,true,0,true,0), "enabled/powered idle pump stays silent");
        assertFalse(AtmosAmbientEligibility.eligible(pump,true,1,false,0), "pump without actual APC power stays silent");
        assertTrue(AtmosAmbientEligibility.eligible(pump,true,1,true,0), "accepted transfer with APC power enables pump cue");
        assertFalse(AtmosAmbientEligibility.eligible(pump,false,1,true,0), "disabled pump is silent");
        assertTrue(AtmosAmbientEligibility.eligible(mixer,true,1,false,0), "mixer transfer does not require APC power");
        assertFalse(AtmosAmbientEligibility.eligible(mixer,true,0,false,0), "idle mixer is silent");
        assertTrue(AtmosAmbientEligibility.eligible(profiles.get(ModAtmosMachines.id("gas_filter")),true,1,false,0), "filter transfer does not require APC power");
        assertTrue(AtmosAmbientEligibility.eligible(valve,true,0,false,0), "open valve ambience follows open state");
        assertFalse(AtmosAmbientEligibility.eligible(valve,false,4,false,0), "closed valve ambience is silent");
        assertFalse(AtmosAmbientEligibility.eligible(teg,true,0,false,1), "powered-only TEG display level remains silent");
        assertTrue(AtmosAmbientEligibility.eligible(teg,true,0,false,2), "TEG output display level one enables hum");
        var ventilationCue = new com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio("moonstation14:ss14/atmospherics/gas_vent", -12f, 5f,
                com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio.Gate.ENABLED_POWERED);
        var ventilationProfile = new AtmosMachineProfile(null, java.util.List.of(), java.util.List.of(), null, null, null,
                null, null, null, null, null, null, AtmosMachineProfile.DEFAULT_SIMULATION,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(ventilationCue));
        assertTrue(AtmosAmbientEligibility.eligible(ventilationProfile, true, 0, true, 0), "vent remains audible with no gas flow");
        assertFalse(AtmosAmbientEligibility.eligible(ventilationProfile, false, 1, true, 0));
        assertFalse(AtmosAmbientEligibility.eligible(ventilationProfile, true, 1, false, 0));
        assertEquals("moonstation14:ss14/atmospherics/gas_hiss", profiles.get(ModAtmosMachines.id("gas_filter")).ambientAudio().orElseThrow().sound());
        assertEquals("moonstation14:ss14/atmospherics/vending_machine_hum", teg.ambientAudio().orElseThrow().sound());
        var custom = com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"sound\":\"moonstation14:test/custom\",\"volumeDb\":-3,\"range\":8,\"gate\":\"transfer\"}")).getOrThrow();
        assertEquals("moonstation14:test/custom", custom.sound()); assertEquals(-3f, custom.volumeDb()); assertEquals(8f, custom.range());
        for (String id : new String[] {"gas_thermo_heater","gas_thermo_freezer","passive_heat_exchanger","teg_circulator"})
            assertTrue(profiles.get(ModAtmosMachines.id(id)).ambientAudio().isEmpty(), id + " has no unsupported ambience");
    }
    @Test void configurationMenuCapabilityAllowsOnlySixOrdinaryMachineKinds() {
        var catalog = manager().snapshot(ModAtmosMachines.TYPE);
        for (String id : new String[] {"gas_pressure_pump", "gas_volume_pump", "gas_mixer", "gas_filter", "gas_thermo_heater", "gas_thermo_freezer"})
            assertTrue(com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineMenuService.supportsMenu(catalog.get(ModAtmosMachines.id(id))), id);
        for (String id : new String[] {"gas_valve", "teg_center", "teg_circulator", "passive_heat_exchanger"})
            assertFalse(com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineMenuService.supportsMenu(catalog.get(ModAtmosMachines.id(id))), id);
    }
    @Test void compositionsInheritPipeDefaultsAndKeepOptionalComponentsAbsent() {
        var catalog = manager().snapshot(ModAtmosMachines.TYPE);
        assertEquals(10, catalog.asMap().size());
        assertNull(catalog.get(ModAtmosMachines.id("gas_binary_base")));
        var pump = catalog.get(ModAtmosMachines.id("gas_pressure_pump"));
        assertEquals(0.2, pump.ports().getFirst().volumeM3());
        assertEquals(200, pump.power().watts()); assertFalse(pump.pump().enabled()); assertEquals(101.325, pump.pump().targetPressureKpa().orElseThrow()); assertEquals(4500, pump.pump().maxPressureKpa().orElseThrow());
        var volumePump = catalog.get(ModAtmosMachines.id("gas_volume_pump")).pump();
        assertEquals(200, volumePump.transferRate().orElseThrow()); assertEquals(0.1, volumePump.leakRatio().orElseThrow());
        assertTrue(volumePump.targetPressureKpa().isEmpty());
        assertNull(pump.mixer()); assertNull(pump.thermostat());
        var valve = catalog.get(ModAtmosMachines.id("gas_valve"));
        assertEquals(0.1, valve.ports().getFirst().volumeM3()); assertTrue(valve.valve().open());
        assertEquals("moonstation14:gas_valve_toggle", valve.activationAudio().orElseThrow().sound());
        assertEquals(1f, valve.activationAudio().orElseThrow().volume());
        assertEquals(.25f, valve.activationAudio().orElseThrow().pitchVariation());
        var customAudio = AtmosMachineProfile.ActivationAudio.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"sound\":\"moonstation14:apc/switch\",\"volume\":0.7,\"pitchVariation\":0.1}")).getOrThrow();
        assertEquals(.7f, customAudio.volume()); assertEquals(.1f, customAudio.pitchVariation());
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "apc/switch"),
                com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineMenuService.activationSoundId(customAudio));
        assertNotNull(com.juicyslew.moonstation14.sounds.ModSounds.APC_SWITCH);
        var mixer = catalog.get(ModAtmosMachines.id("gas_mixer")); assertNull(mixer.power());
        var filter = catalog.get(ModAtmosMachines.id("gas_filter")).filter();
        assertFalse(filter.enabled()); assertTrue(filter.gas().isEmpty());
        assertEquals(1000, filter.transferRate()); assertEquals(1000, filter.maxTransferRate());
        var exchanger = catalog.get(ModAtmosMachines.id("passive_heat_exchanger")); assertNull(exchanger.power());
        assertEquals(1, exchanger.pipeLayers().orElseThrow().count());
        assertEquals(20, exchanger.pipeLayers().orElseThrow().changeDurationTicks());
        assertEquals(1, exchanger.heatExchanger().conductivityMolesPerKpaPerS());
        assertEquals(8000, exchanger.heatExchanger().convectionCoefficientJPerKPerS());
        assertEquals(140, exchanger.heatExchanger().radiationCoefficient());
        assertEquals(1000, catalog.get(ModAtmosMachines.id("teg_center")).power().watts());
        assertEquals(2, catalog.get(ModAtmosMachines.id("teg_center")).networkNodes().size());
        assertEquals(0.1, catalog.get(ModAtmosMachines.id("teg_circulator")).ports().getFirst().volumeM3());
        assertEquals(1, catalog.get(ModAtmosMachines.id("teg_circulator")).networkNodes().size());
        var heater = catalog.get(ModAtmosMachines.id("gas_thermo_heater"));
        assertNull(heater.power());
        assertEquals(5000, heater.configuredReceiverLoadWatts());
        assertEquals(5000, heater.thermostat().inputPowerWatts());
        assertEquals(0.95, heater.thermostat().coefficientOfPerformance());
        var freezer = catalog.get(ModAtmosMachines.id("gas_thermo_freezer"));
        assertEquals(3, heater.pipeLayers().orElseThrow().count());
        assertEquals(3, freezer.pipeLayers().orElseThrow().count());
        assertEquals(-3.9, freezer.thermostat().coefficientOfPerformance());
        assertEquals(73.15, freezer.thermostat().minimumTemperatureK());
        for (String id : new String[] {"gas_pressure_pump", "gas_volume_pump", "gas_valve", "gas_mixer", "gas_filter"}) {
            var layers = catalog.get(ModAtmosMachines.id(id)).pipeLayers().orElseThrow();
            assertEquals(3, layers.count(), id); assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY, layers.initialLayer(), id);
            assertFalse(layers.locked(), id); assertEquals(20, layers.changeDurationTicks(), id);
        }
        assertTrue(catalog.get(ModAtmosMachines.id("teg_center")).pipeLayers().isEmpty());
        assertTrue(catalog.get(ModAtmosMachines.id("teg_circulator")).pipeLayers().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> pump.ports().add(pump.ports().getFirst()));
    }

    @Test void pipeLayerCompositionSupportsResolvedCustomDurationAndRejectsInvalidShape() {
        var parsed = AtmosMachineProfile.PipeLayers.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"count\":2,\"initialLayer\":\"Secondary\",\"locked\":false,\"changeDurationTicks\":7}"));
        assertEquals(7, parsed.result().orElseThrow().changeDurationTicks());
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY, parsed.result().orElseThrow().initialLayer());
        var customManager = new PrototypeManager(); customManager.register(ModAtmosMachines.TYPE);
        var customRaw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : RESOURCE_IDS) customRaw.put(ModAtmosMachines.id(name), resource(name));
        JsonObject customizedPump = resource("gas_pressure_pump");
        customizedPump.add("pipeLayers", JsonParser.parseString("{\"count\":2,\"initialLayer\":\"Secondary\",\"locked\":false,\"changeDurationTicks\":7}").getAsJsonObject());
        customRaw.put(ModAtmosMachines.id("gas_pressure_pump"), customizedPump);
        customManager.reload(ModAtmosMachines.TYPE, customRaw);
        var resolvedCustom = customManager.snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id("gas_pressure_pump")).pipeLayers().orElseThrow();
        assertEquals(2, resolvedCustom.count());
        assertEquals(7, resolvedCustom.changeDurationTicks());
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY, resolvedCustom.initialLayer());
        assertThrows(IllegalArgumentException.class, () -> new AtmosMachineProfile.PipeLayers(1,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY, false, 20));
        var catalog = manager().snapshot(ModAtmosMachines.TYPE);
        var pump = catalog.get(ModAtmosMachines.id("gas_pressure_pump"));
        assertTrue(AtmosMachineBlockEntity.supportsPipeLayerChange(pump,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY));
        assertFalse(AtmosMachineBlockEntity.supportsPipeLayerChange(catalog.get(ModAtmosMachines.id("teg_center")),
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY));
        assertFalse(AtmosMachineBlockEntity.supportsPipeLayerChange(catalog.get(ModAtmosMachines.id("passive_heat_exchanger")),
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY));
        var locked = new AtmosMachineProfile.PipeLayers(3,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY, true, 20);
        var lockedProfile = new AtmosMachineProfile(pump.kind(), pump.ports(), pump.networkNodes(), pump.power(), pump.pump(),
                pump.mixer(), pump.filter(), pump.valve(), pump.thermostat(), pump.teg(), pump.heatExchanger(), pump.visual(),
                pump.simulation(), java.util.Optional.of(locked));
        assertFalse(AtmosMachineBlockEntity.supportsPipeLayerChange(lockedProfile,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY));
    }

    @Test void timedScrewdriverPolicyCyclesOnlySupportedResolvedLayersAndConsumesDuration() {
        var catalog = manager().snapshot(ModAtmosMachines.TYPE);
        for (String id : new String[] {"gas_pressure_pump", "gas_volume_pump", "gas_valve", "gas_mixer",
                "gas_filter", "gas_thermo_heater", "gas_thermo_freezer"}) {
            var profile = catalog.get(ModAtmosMachines.id(id));
            assertEquals(20, AtmosMachineLayerUse.durationTicks(profile), id);
            assertTrue(AtmosMachineLayerUse.supportsChange(profile, com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY), id);
            assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY,
                    AtmosMachineLayerUse.nextLayer(profile, com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY), id);
            assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.TERTIARY,
                    AtmosMachineLayerUse.nextLayer(profile, com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY), id);
        }
        for (String id : new String[] {"teg_center", "teg_circulator", "passive_heat_exchanger"}) {
            var profile = catalog.get(ModAtmosMachines.id(id));
            assertFalse(AtmosMachineLayerUse.supportsChange(profile, com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY), id);
            assertNull(AtmosMachineLayerUse.nextLayer(profile, com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY), id);
        }
        var pump = catalog.get(ModAtmosMachines.id("gas_pressure_pump"));
        var custom = new AtmosMachineProfile.PipeLayers(2,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY, false, 7);
        var customProfile = new AtmosMachineProfile(pump.kind(), pump.ports(), pump.networkNodes(), pump.power(), pump.pump(),
                pump.mixer(), pump.filter(), pump.valve(), pump.thermostat(), pump.teg(), pump.heatExchanger(), pump.visual(), pump.simulation(), java.util.Optional.of(custom));
        assertEquals(7, AtmosMachineLayerUse.durationTicks(customProfile));
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY,
                AtmosMachineLayerUse.nextLayer(customProfile, com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY));
        var oneLayer = new AtmosMachineProfile.PipeLayers(1,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY, false, 20);
        var oneLayerProfile = new AtmosMachineProfile(pump.kind(), pump.ports(), pump.networkNodes(), pump.power(), pump.pump(),
                pump.mixer(), pump.filter(), pump.valve(), pump.thermostat(), pump.teg(), pump.heatExchanger(), pump.visual(), pump.simulation(), java.util.Optional.of(oneLayer));
        assertFalse(AtmosMachineLayerUse.supportsChange(oneLayerProfile,
                com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY));
    }

    @Test void rejectsUnknownEnumsAndConflictingPortComposition() {
        var unknownKind = JsonParser.parseString("{\"kind\":\"made_up\"}");
        assertTrue(AtmosMachineProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, unknownKind).error().isPresent());
        var pump = manager().snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id("gas_pressure_pump"));
        assertThrows(IllegalArgumentException.class, () -> new AtmosMachineProfile(pump.kind(), java.util.List.of(pump.ports().get(0), pump.ports().get(0)), pump.networkNodes(), pump.power(), pump.pump(), pump.mixer(), pump.filter(), pump.valve(), pump.thermostat(), pump.teg(), pump.heatExchanger(), pump.visual()));
        var badPressure = new AtmosMachineProfile.Pump(AtmosMachineProfile.PumpMode.PRESSURE, false, java.util.Optional.of(5000d), java.util.Optional.of(4500d), java.util.Optional.empty(), java.util.Optional.of(200d), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        var pressureProfile = new AtmosMachineProfile(pump.kind(), pump.ports(), pump.networkNodes(), pump.power(), badPressure, null, null, null, null, null, null, pump.visual());
        assertThrows(IllegalArgumentException.class, () -> ModAtmosMachines.validate(pressureProfile));
        var mixer = manager().snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id("gas_mixer"));
        var badRatio = new AtmosMachineProfile.Mixer(java.util.List.of(0.8, 0.8), 101.325, 4500, false);
        var mixerProfile = new AtmosMachineProfile(mixer.kind(), mixer.ports(), mixer.networkNodes(), null, null, badRatio, null, null, null, null, null, mixer.visual());
        assertThrows(IllegalArgumentException.class, () -> ModAtmosMachines.validate(mixerProfile));
    }
}
