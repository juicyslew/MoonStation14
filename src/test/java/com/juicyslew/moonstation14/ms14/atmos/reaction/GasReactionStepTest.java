package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GasReactionStepTest {
    private static final PrototypeCatalog<GasReactionData> CATALOG = bundled();

    private static PrototypeCatalog<GasReactionData> bundled() {
        var manager = new PrototypeManager();
        manager.register(ModGasReactions.GAS_REACTION_TYPE);
        Map<ResourceLocation, JsonObject> definitions = new LinkedHashMap<>();
        for (String name : new String[] {"frezon_production", "ammonia_oxygen", "frezon_coolant",
                "n2o_decomposition", "tritium_fire", "plasma_fire"}) {
            String path = "/data/moonstation14/moonstation14/gas_reaction/" + name + ".json";
            try (var input = GasReactionStepTest.class.getResourceAsStream(path)) {
                assertNotNull(input, path);
                definitions.put(ResourceLocation.parse("moonstation14:" + name),
                        JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        }
        manager.reload(ModGasReactions.GAS_REACTION_TYPE, definitions);
        return manager.snapshot(ModGasReactions.GAS_REACTION_TYPE);
    }

    private static GasMixture gas(double kelvin, Object... pairs) {
        EnumMap<GasType, Double> amounts = new EnumMap<>(GasType.class);
        for (int i = 0; i < pairs.length; i += 2) amounts.put((GasType) pairs[i], (Double) pairs[i + 1]);
        return new GasMixture(amounts, kelvin);
    }

    private static GasReactionStep.Result check(GasMixture before, HotspotKernel.HotspotState state) {
        var result = GasReactionStep.evaluate(CATALOG, before, state);
        for (GasType type : GasType.values()) {
            double events = result.events().stream().mapToDouble(e -> e.speciesDelta().getOrDefault(type, 0.0)).sum();
            assertEquals(before.moles(type) + events, result.mixture().moles(type), 1e-9, type.name());
            assertEquals(events, result.speciesDelta().getOrDefault(type, 0.0), 1e-9, type.name());
        }
        double joules = result.events().stream().mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum();
        assertEquals(joules, result.energyDeltaJoules(), 1e-7);
        assertEquals(before.thermalEnergy() + joules, result.mixture().thermalEnergy(),
                Math.max(1e-7, before.thermalEnergy() * 1e-12));
        assertEquals(!result.events().stream().noneMatch(e -> e.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                || e.effect() == GasReactionData.EffectType.PLASMA_FIRE), result.fireOccurred());
        return result;
    }

    @Test void coldProductionAndCoolantRunOnEntireCellWithoutFire() {
        var production = check(gas(73.15, GasType.TRITIUM, 1.0, GasType.OXYGEN, 50.0,
                GasType.NITROGEN, 1.0), null);
        assertEquals("frezon_production", production.events().getFirst().id().getPath());
        assertEquals(.204, production.events().getFirst().speciesDelta().get(GasType.FREZON), 1e-12);
        assertEquals("frezon_coolant", production.events().get(1).id().getPath());
        assertFalse(production.fireOccurred());
        var coolant = check(gas(373.15, GasType.FREZON, 1.0, GasType.NITROGEN, 100.0), null);
        assertEquals(-30000, coolant.energyDeltaJoules(), 1e-8);
        assertTrue(coolant.nextState().isEmpty());
    }

    @Test void mixedNonfireThenBothFiresProduceOneCombinedLedger() {
        var before = gas(1643.15, GasType.AMMONIA, 2.0, GasType.NITROUS_OXIDE, 2.0,
                GasType.TRITIUM, 1.0, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0);
        var result = check(before, null);
        assertEquals(4, result.events().size());
        assertEquals("ammonia_oxygen", result.events().get(0).id().getPath());
        assertEquals("n2o_decomposition", result.events().get(1).id().getPath());
        assertEquals("tritium_fire", result.events().get(2).id().getPath());
        assertEquals("plasma_fire", result.events().get(3).id().getPath());
        assertTrue(result.fireOccurred());
        assertTrue(result.nextState().isPresent());
        // Full-cell fire would burn ten times as much tritium in this seeded pass.
        assertEquals(.01, result.events().get(2).extentMoles(), 1e-12);
        assertEquals(result.mixture().temperatureKelvin(), result.nextState().orElseThrow().temperatureKelvin());
    }

    @Test void postCoolingQuenchesStaleHotspotWithoutFreeHeat() {
        var before = gas(400, GasType.FREZON, 1.0, GasType.NITROGEN, 3.0,
                GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var result = check(before, new HotspotKernel.HotspotState(.3, 500));
        assertEquals(1, result.events().size());
        assertEquals("frezon_coolant", result.events().getFirst().id().getPath());
        assertFalse(result.fireOccurred());
        assertTrue(result.nextState().isEmpty());
        assertTrue(result.mixture().temperatureKelvin() < 373.15);
    }

    @Test void diffusionTemperatureReconciliationAndLowInventoryPolicy() {
        for (double amount : new double[] {.01, .05, .1}) {
            var before = gas(400, GasType.TRITIUM, amount, GasType.OXYGEN, amount);
            var result = check(before, null);
            assertTrue(result.fireOccurred(), "source has full-cell minimum: " + amount);
            double portion = Math.max(.1, .01 / amount);
            assertEquals(amount * portion / 100, result.events().getFirst().extentMoles(), 1e-12);
            assertEquals(Math.min(1, portion + 40 * result.events().getFirst().extentMoles()),
                    result.nextState().orElseThrow().fraction(), 1e-12);
        }
        var below = gas(400, GasType.TRITIUM, Math.nextDown(.01), GasType.OXYGEN, 1.0);
        assertFalse(check(below, null).fireOccurred());
        var diffused = gas(450, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var resumed = check(diffused, new HotspotKernel.HotspotState(.2, 800));
        assertTrue(resumed.fireOccurred());
        assertEquals(.2 + 40 * resumed.events().getFirst().extentMoles(),
                resumed.nextState().orElseThrow().fraction(), 1e-12);
    }

    @Test void plasmaStillNeedsPreliminaryRateAndStrictEffectTemperature() {
        assertFalse(check(gas(373.15, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0), null).fireOccurred());
        assertFalse(check(gas(373.151, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0), null).fireOccurred());
        assertTrue(check(gas(1643.15, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0), null).fireOccurred());
    }
}
