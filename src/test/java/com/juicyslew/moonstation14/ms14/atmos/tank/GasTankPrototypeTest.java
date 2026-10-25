package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class GasTankPrototypeTest {
    @Test void resolvesEmptyBaseAndFilledChildIntoStackFactoryProfiles() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModGasTanks.TYPE);
        var source = new LinkedHashMap<net.minecraft.resources.ResourceLocation, JsonObject>();
        for (String id : java.util.List.of("gas_tank_base", "plasma_tank", "plasma_tank_filled")) {
            String path = "/data/moonstation14/moonstation14/gas_tank/" + id + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                source.put(ModGasTanks.id(id), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        }
        manager.reload(ModGasTanks.TYPE, source);
        var resolved = manager.snapshot(ModGasTanks.TYPE);
        GasTankData empty = resolved.get(ModGasTanks.PLASMA_ID);
        GasTankData filled = resolved.get(ModGasTanks.PLASMA_FILLED_ID);
        assertNotNull(empty);
        assertNotNull(filled);
        assertEquals(0.005, empty.initialMixture().volumeCubicMeters(), 1e-9);
        assertEquals(293.15, empty.initialMixture().temperatureKelvin(), 1e-3);
        assertEquals(0, empty.initialMixture().totalMoles(), 0);
        var emptyStackContents = com.juicyslew.moonstation14.item.custom.GasTankItem.createContents(resolved, ModGasTanks.PLASMA_ID);
        assertEquals(ModGasTanks.PLASMA_ID, emptyStackContents.prototypeId());
        assertEquals(0, emptyStackContents.mixture().totalMoles(), 0);
        assertEquals(2.05137905, filled.initialMixture().moles(GasType.PLASMA), 1e-6);
        assertEquals(0.005, filled.initialMixture().volumeCubicMeters(), 1e-9);
        var stackContents = com.juicyslew.moonstation14.item.custom.GasTankItem.createContents(resolved, ModGasTanks.PLASMA_FILLED_ID);
        assertEquals(ModGasTanks.PLASMA_FILLED_ID, stackContents.prototypeId());
        assertEquals(2.05137905, stackContents.mixture().moles(GasType.PLASMA), 1e-6);
    }
}
