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

    private static GasReactionStep.Result check(GasMixture before, HotspotState state) {
        var result = GasReactionStep.evaluate(CATALOG, before, state);
        for (GasType type : GasType.values()) {
            double events = result.events().stream().mapToDouble(e -> e.speciesDelta().getOrDefault(type, 0.0)).sum();
            double scale = Math.max(before.moles(type), result.mixture().moles(type));
            double stateUlp = Math.ulp((float) scale);
            assertEquals(before.moles(type) + events, result.mixture().moles(type), Math.max(1e-9, stateUlp * 2), type.name());
            assertEquals(events, result.speciesDelta().getOrDefault(type, 0.0), Math.max(1e-9, stateUlp * 2), type.name());
        }
        assertEquals(before.thermalEnergy() + result.energyDeltaJoules(), result.mixture().thermalEnergy(),
                Math.max(1e-7, Math.ulp((float) before.thermalEnergy()) * 2));
        assertEquals(!result.events().stream().noneMatch(e -> e.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                || e.effect() == GasReactionData.EffectType.PLASMA_FIRE), result.fireOccurred());
        return result;
    }

    @Test void coldProductionAndCoolantRunOnEntireCellWithoutFire() {
        var production = check(gas(Math.nextDown((float) 73.15), GasType.TRITIUM, 1.0, GasType.OXYGEN, 50.0,
                GasType.NITROGEN, 1.0), null);
        assertEquals("frezon_production", production.events().getFirst().id().getPath());
        assertTrue(production.events().getFirst().speciesDelta().get(GasType.FREZON) > 0);
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
        assertFalse(result.spreadEligibleThisPass());
        // Full-cell fire would burn ten times as much tritium in this seeded pass.
        assertEquals(.1, result.events().get(2).extentMoles(), 1e-6);
        assertEquals(result.mixture().temperatureKelvin(), result.nextState().orElseThrow().temperatureKelvin());
    }

    @Test void postCoolingRetainsEnergyBackedHotspotWithoutFreeHeat() {
        var before = gas(400, GasType.FREZON, 1.0, GasType.NITROGEN, 3.0,
                GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var result = check(before, new HotspotState(300, 500, true, false));
        assertEquals(4, result.events().size(), "whole-air pass is followed by the extracted hotspot pass");
        assertEquals("frezon_coolant", result.events().getFirst().id().getPath());
        assertEquals("tritium_fire", result.events().get(1).id().getPath());
        assertTrue(result.fireOccurred());
        assertTrue(result.fireOccurred(), "hotspot reaction is evaluated after whole-air chemistry");
    }

    @Test void diffusionTemperatureReconciliationAndLowInventoryPolicy() {
        for (double amount : new double[] {.01, .05, .1}) {
            var before = gas(400, GasType.TRITIUM, amount, GasType.OXYGEN, amount);
            var result = check(before, null);
            assertTrue(result.fireOccurred(), "source has full-cell minimum: " + amount);
            assertTrue(result.nextState().isEmpty(),
                    "combustible trace inventories below 0.2 mol do not seed an SS14-ignitable hotspot");
        }
        var below = gas(400, GasType.TRITIUM, (double) Math.nextDown((float) .004), GasType.OXYGEN, 1.0);
        assertFalse(check(below, null).fireOccurred());
        var ignitable = gas(400, GasType.TRITIUM, .25, GasType.OXYGEN, .25);
        var seeded = check(ignitable, null);
        assertTrue(seeded.nextState().isPresent());
        assertEquals(ignitable.volumeCubicMeters() * 1000 * 25,
                seeded.nextState().orElseThrow().volumeLiters(), 1e-3);
        var diffused = gas(450, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var resumed = check(diffused, new HotspotState(200, 800, true, false));
        assertTrue(resumed.fireOccurred());
        assertTrue(resumed.spreadEligibleThisPass());
        assertEquals(resumed.events().getLast().extentMoles() * 40000,
                resumed.nextState().orElseThrow().volumeLiters(), 1e-3);
    }

    @Test void plasmaStillNeedsPreliminaryRateAndStrictEffectTemperature() {
        assertFalse(check(gas(373.15, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0), null).fireOccurred());
        assertFalse(check(gas(373.151, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0), null).fireOccurred());
        assertTrue(check(gas(1643.15, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0), null).fireOccurred());
    }

    @Test void wholeAirFireExposureReportsUnscaledCellVolumeForOneKernelScale() {
        var hot = gas(1643.15, GasType.TRITIUM, 1.0, GasType.OXYGEN, 100.0);
        var whole = GasReactionStep.evaluateWholeAir(CATALOG, hot);
        assertTrue(whole.fireExtentMoles() > 0);
        assertEquals(hot.volumeCubicMeters() * 1000, whole.autoExposureVolumeLiters(), 0);
        var result = GasReactionStep.evaluate(CATALOG, hot, null);
        assertEquals(hot.volumeCubicMeters() * 1000 * 25,
                result.nextState().orElseThrow().volumeLiters(), 1e-8);
        assertFalse(result.nextState().orElseThrow().skippedFirstProcess());
    }

    @Test void wholeAirCarriesFinalFireResultSlotEvenWhenNoFireEffectRuns() {
        var inert = gas(293, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var empty = new PrototypeCatalog<GasReactionData>(Map.of());
        var whole = GasReactionStep.evaluateWholeAir(empty, inert, .037);
        assertEquals(.037, whole.fireExtentMoles(), 0);
        assertEquals(0, whole.autoExposureVolumeLiters(), 0);
        double cellLiters = inert.volumeCubicMeters() * 1000;
        var bypass = GasReactionStep.processHotspot(empty, inert,
                new HotspotState(cellLiters * .96, 500, true, true), whole.fireExtentMoles());
        assertEquals(.037 * 40000, bypass.nextState().orElseThrow().volumeLiters(), 0);
    }

    @Test void pendingExplosionAddsMetadataWithoutHeatingOrBurningSamePass() {
        var cold = gas(293, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var exposure = new ExplosionHotspotExposure.Decision(true, 800, .1);
        var result = GasReactionStep.evaluate(CATALOG, cold, null, exposure);
        assertFalse(result.fireOccurred());
        assertEquals(cold.temperatureKelvin(), result.mixture().temperatureKelvin(), 0);
        assertEquals(0, result.externalHeatJoules());
        assertEquals(100, result.nextState().orElseThrow().volumeLiters(), 1e-6);
        var later = GasReactionStep.evaluate(CATALOG, result.mixture(), result.nextState().orElse(null));
        assertEquals(0, later.externalHeatJoules());
    }

    @Test void pendingExposureCannotGrowFireBeyondHeatedCellFraction() {
        var cold = gas(293, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var result = GasReactionStep.evaluate(CATALOG, cold, null,
                new ExplosionHotspotExposure.Decision(true, 1400, .1));
        var next = result.nextState().orElseThrow();
        assertEquals(100, next.volumeLiters(), 1e-6);
        var later = GasReactionStep.evaluate(CATALOG, result.mixture(), next);
        assertTrue(later.events().stream().filter(e -> e.effect() == GasReactionData.EffectType.TRITIUM_FIRE)
                .mapToDouble(GasReactionEvaluator.Event::extentMoles).sum() < .01,
                "the next ordinary pass must not burn the whole cell");
    }

    @Test void pendingExposureWithoutEnoughFuelDoesNotHeatCell() {
        var cold = gas(293, GasType.TRITIUM, .001, GasType.OXYGEN, .001);
        var result = GasReactionStep.evaluate(CATALOG, cold, null,
                new ExplosionHotspotExposure.Decision(true, 800, .1));
        assertFalse(result.fireOccurred());
        assertEquals(0, result.externalHeatJoules());
        assertEquals(cold.thermalEnergy(), result.mixture().thermalEnergy());
    }

    @Test void burningPartialHotspotCanSpreadBeforeFollowingWholeAirPass() {
        var gas = gas(1643.15, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var crossing = check(gas, new HotspotState(940, 1643.15, true, false));
        assertTrue(crossing.fireOccurred());
        assertTrue(crossing.events().stream().anyMatch(e -> e.extentMoles() > 0));
        assertEquals(crossing.events().getLast().extentMoles() * 40000,
                crossing.nextState().orElseThrow().volumeLiters(), 1e-3);
        assertTrue(crossing.spreadEligibleThisPass());
        var full = check(crossing.mixture(), crossing.nextState().orElseThrow());
        assertTrue(full.fireOccurred());
        assertTrue(full.spreadEligibleThisPass());
    }

    @Test void explosionExposureNeverQualifiesForSpreadBypass() {
        var gas = gas(293, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var result = GasReactionStep.evaluate(CATALOG, gas,
                new HotspotState(990, 293, true, false),
                new ExplosionHotspotExposure.Decision(true, 800, 1));
        assertFalse(result.fireOccurred(), "explosion exposure adds metadata, not same-pass heat or combustion");
        assertFalse(result.spreadEligibleThisPass());
    }

    @Test void subcellHotspotRetainsHotLabelBelowBulkIgnitionAndKeepsLedger() {
        var before = gas(300, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var result = check(before, new HotspotState(250, 400, true, false));
        assertTrue(result.fireOccurred());
        assertTrue(result.spreadEligibleThisPass());
        assertTrue(result.nextState().orElseThrow().temperatureKelvin() > 373.15);
    }

    @Test void invalidSubcellLabelCollapsesWithoutPhantomHeat() {
        var before = gas(300, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var result = check(before, new HotspotState(250, 1201, true, false));
        assertTrue(result.fireOccurred(), "stored hotspot temperature independently drives its partition reaction");
        assertTrue(result.nextState().isPresent());
    }
}
