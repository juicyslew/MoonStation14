package com.juicyslew.moonstation14.ms14.radiation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class RadiationCollectorPrototypeTest {
    private static JsonObject load(String file) throws Exception {
        try (var in = new InputStreamReader(RadiationCollectorPrototypeTest.class.getResourceAsStream(
                "/data/moonstation14/moonstation14/radiation_collector/" + file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }

    @Test void profilesResolveCollectorDefaultsAndTankChildren() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String file : new String[]{"radiation_collector.json", "radiation_collector_no_tank.json", "radiation_collector_full_tank.json"}) {
            JsonObject value = load(file);
            raw.put(ResourceLocation.parse(value.get("id").getAsString()), value);
        }
        var manager = new PrototypeManager();
        manager.register(ModRadiationCollectors.TYPE);
        manager.reload(ModRadiationCollectors.TYPE, raw);
        var catalog = manager.snapshot(ModRadiationCollectors.TYPE);
        var base = catalog.get(ModRadiationCollectors.EMPTY_ID);
        assertEquals(15000, base.chargeModifier());
        assertEquals(GasType.PLASMA, base.reactant());
        assertEquals(1, base.efficiency());
        assertEquals(.0001, base.breakdownRate());
        assertFalse(base.enabled());
        assertEquals(40, base.powerHoldoverTicks());
        assertEquals("moonstation14:plasma_tank", base.startingTank());
        assertEquals(base.chargeModifier(), catalog.get(ModRadiationCollectors.NO_TANK_ID).chargeModifier());
        assertEquals("", catalog.get(ModRadiationCollectors.NO_TANK_ID).startingTank());
        assertEquals("moonstation14:plasma_tank_filled", catalog.get(ModRadiationCollectors.FULL_TANK_ID).startingTank());
    }
}
