package com.juicyslew.moonstation14.ms14.atmos.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class GasMinerProfileTest {
    @Test void resolvesStationCompositionAndDefaultsForLiveConsumer() {
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(ModGasMiners.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String id : java.util.List.of("gas_miner_base", "gas_miner_oxygen", "oxygen_station",
                "gas_miner_nitrogen", "nitrogen_station", "plasma_station")) raw.put(ModGasMiners.id(id), json(id));
        manager.reload(ModGasMiners.TYPE, raw);
        var oxygen = manager.snapshot(ModGasMiners.TYPE).get(ModGasMiners.id("oxygen_station"));
        var nitrogen = manager.snapshot(ModGasMiners.TYPE).get(ModGasMiners.id("nitrogen_station"));
        var plasma = manager.snapshot(ModGasMiners.TYPE).get(ModGasMiners.id("plasma_station"));
        assertEquals(GasType.OXYGEN, oxygen.gas()); assertEquals(1000, oxygen.maxExternalPressureKpa());
        assertEquals(GasType.NITROGEN, nitrogen.gas()); assertEquals(1000, nitrogen.maxExternalPressureKpa());
        assertEquals(GasType.PLASMA, plasma.gas()); assertEquals(300, plasma.maxExternalPressureKpa());
        for (var profile : java.util.List.of(oxygen, nitrogen, plasma)) {
            assertEquals(400, profile.spawnAmount()); assertEquals(293.15, profile.spawnTemperatureKelvin());
            assertEquals(20, profile.molesForElapsedSeconds(.05), 1e-9);
        }
    }
    @Test void dtScalesAndRejectsInvalidElapsedTime() {
        GasMinerProfile profile = new GasMinerProfile(GasType.OXYGEN, 400, 293.15, 300, Double.MAX_VALUE);
        assertEquals(profile.molesForElapsedSeconds(.05), profile.molesForElapsedSeconds(.025) * 2, 1e-9);
        assertEquals(0, profile.molesForElapsedSeconds(Double.NaN));
    }
    @Test void sourceStepHonorsPressureAndTotalMoleCapsWithEnergyConservingMix() {
        var room = new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(GasType.NITROGEN, 10.0), 250.0, 1.0);
        var result = GasMinerProfile.boundedAddition(room, GasType.OXYGEN, 400, 293.15, 101.325, 11);
        assertNotNull(result);
        assertEquals(1, result.moles(GasType.OXYGEN) + result.moles(GasType.NITROGEN) - 10, 1e-5);
        assertTrue(result.pressureKpa() <= 101.325);
        assertTrue(result.temperatureKelvin() > room.temperatureKelvin());
        assertNull(GasMinerProfile.boundedAddition(room, GasType.OXYGEN, 400, 293.15, 101.325, 10));
        assertNull(GasMinerProfile.boundedAddition(room, GasType.OXYGEN, 400, 293.15, 1, 100));
    }
    private static JsonObject json(String name) {
        String path = "/data/moonstation14/moonstation14/gas_miner/" + name + ".json";
        try (var input = GasMinerProfileTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
