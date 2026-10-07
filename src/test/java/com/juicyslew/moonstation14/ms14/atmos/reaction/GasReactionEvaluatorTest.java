package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GasReactionEvaluatorTest {
    private static final String[] NAMES = {"frezon_production", "ammonia_oxygen", "frezon_coolant",
            "n2o_decomposition", "tritium_fire", "plasma_fire"};
    private static final PrototypeCatalog<GasReactionData> BUNDLED_CATALOG = loadBundledCatalog();

    private static PrototypeCatalog<GasReactionData> loadBundledCatalog() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModGasReactions.GAS_REACTION_TYPE);
        Map<ResourceLocation, JsonObject> definitions = new LinkedHashMap<>();
        for (String name : NAMES) {
            String path = "/data/moonstation14/moonstation14/gas_reaction/" + name + ".json";
            try (var stream = GasReactionEvaluatorTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                definitions.put(ResourceLocation.parse("moonstation14:" + name),
                        JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        }
        manager.reload(ModGasReactions.GAS_REACTION_TYPE, definitions);
        return manager.snapshot(ModGasReactions.GAS_REACTION_TYPE);
    }

    private static PrototypeCatalog<GasReactionData> catalog(String... names) {
        // The manager validates the complete bundled catalog; only pure evaluator tests
        // use detached subsets so unrelated reactions cannot affect their assertions.
        Map<ResourceLocation, GasReactionData> selected = new LinkedHashMap<>();
        for (String name : names) {
            ResourceLocation id = ResourceLocation.parse("moonstation14:" + name);
            GasReactionData reaction = BUNDLED_CATALOG.get(id);
            assertNotNull(reaction, name);
            selected.put(id, reaction);
        }
        return new PrototypeCatalog<>(selected);
    }

    private static GasMixture mix(double temperature, Object... pairs) {
        Map<GasType, Double> values = new java.util.EnumMap<>(GasType.class);
        for (int i = 0; i < pairs.length; i += 2) values.put((GasType) pairs[i], (Double) pairs[i + 1]);
        return new GasMixture(values, temperature);
    }

    private static GasReactionEvaluator.Result run(String name, GasMixture mixture) {
        return GasReactionEvaluator.evaluate(catalog(name), mixture);
    }

    private static void budget(GasMixture before, GasReactionEvaluator.Result result) {
        for (GasType type : GasType.values()) {
            double scale = Math.max(before.moles(type), result.mixture().moles(type));
            assertEquals(result.mixture().moles(type) - before.moles(type),
                    result.speciesDelta().getOrDefault(type, 0.0), Math.max(1e-9, Math.ulp((float) scale) * 2));
            assertEquals(result.speciesDelta().getOrDefault(type, 0.0), result.events().stream()
                    .mapToDouble(event -> event.speciesDelta().getOrDefault(type, 0.0)).sum(), 1e-9, type.name());
        }
        assertEquals(before.thermalEnergy() + result.energyDeltaJoules(), result.mixture().thermalEnergy(),
                Math.max(1e-8, Math.ulp((float) before.thermalEnergy()) * 2));
        assertEquals(result.energyDeltaJoules(), result.events().stream().mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum(), 1e-7);
    }

    @Test void n2oInclusiveGateAndNoop() {
        var before = mix(850.0, GasType.NITROUS_OXIDE, 2.0);
        var result = run("n2o_decomposition", before);
        assertEquals(1, result.events().size());
        assertEquals(1, result.mixture().moles(GasType.NITROUS_OXIDE));
        assertEquals(1, result.mixture().moles(GasType.NITROGEN));
        assertEquals(.5, result.mixture().moles(GasType.OXYGEN));
        assertEquals(0, result.energyDeltaJoules());
        budget(before, result);
        var cold = mix(849.999, GasType.NITROUS_OXIDE, 2.0);
        assertSame(cold, run("n2o_decomposition", cold).mixture());
        var vacuum = GasMixture.vacuum();
        assertSame(vacuum, GasReactionEvaluator.evaluate(catalog(NAMES), vacuum).mixture());
    }

    private static GasReactionData noOpReaction(int priority) {
        return new GasReactionData(Map.of(GasType.OXYGEN, .01), 2.7, Float.MAX_VALUE, 0,
                priority, List.of(new GasReactionData.Effect(GasReactionData.EffectType.N2O_DECOMPOSITION)));
    }

    @Test void preparedCatalogIsReusedAndReplacementOrAlternationNeverUsesStaleEntries() {
        ResourceLocation a = ResourceLocation.parse("moonstation14:cache_a");
        ResourceLocation b = ResourceLocation.parse("moonstation14:cache_b");
        Map<ResourceLocation, GasReactionData> firstDefinitions = new LinkedHashMap<>();
        firstDefinitions.put(a, noOpReaction(4));
        firstDefinitions.put(b, noOpReaction(4));
        var first = new PrototypeCatalog<>(firstDefinitions);
        var entries = GasReactionEvaluator.preparedEntries(first);
        assertSame(entries, GasReactionEvaluator.preparedEntries(first));
        assertEquals(List.of(a, b), entries.stream().map(Map.Entry::getKey).toList());
        GasReactionEvaluator.evaluate(first, mix(293.15, GasType.OXYGEN, 1.0));
        assertSame(entries, GasReactionEvaluator.preparedEntries(first));

        Map<ResourceLocation, GasReactionData> replacementDefinitions = new LinkedHashMap<>();
        replacementDefinitions.put(a, noOpReaction(1));
        replacementDefinitions.put(b, noOpReaction(9));
        var replacement = new PrototypeCatalog<>(replacementDefinitions);
        var replacedEntries = GasReactionEvaluator.preparedEntries(replacement);
        assertNotSame(entries, replacedEntries);
        assertEquals(List.of(b, a), replacedEntries.stream().map(Map.Entry::getKey).toList());
        assertSame(replacedEntries, GasReactionEvaluator.preparedEntries(replacement));
        var alternatedEntries = GasReactionEvaluator.preparedEntries(first);
        assertNotSame(entries, alternatedEntries);
        assertEquals(List.of(a, b), alternatedEntries.stream().map(Map.Entry::getKey).toList());
        var replacementAgain = GasReactionEvaluator.preparedEntries(replacement);
        assertNotSame(replacedEntries, replacementAgain);
        assertEquals(List.of(b, a), replacementAgain.stream().map(Map.Entry::getKey).toList());

        var empty = new PrototypeCatalog<GasReactionData>(Map.of());
        assertTrue(GasReactionEvaluator.preparedEntries(empty).isEmpty());
        var vacuum = GasMixture.vacuum();
        assertSame(vacuum, GasReactionEvaluator.evaluate(empty, vacuum).mixture());
    }

    @Test void canonicalPrototypeRequirementsConvertAtTheEvaluatorBoundary() {
        for (String name : NAMES) {
            var reaction = BUNDLED_CATALOG.get(ResourceLocation.parse("moonstation14:" + name));
            assertTrue(reaction.minimumRequirements().values().stream().allMatch(amount -> amount == .01), name);
        }
        var catalog = catalog("n2o_decomposition");
        double localMinimum = AtmosphereUnits.referenceTileQuantity(.01);
        var exact = mix(850, GasType.NITROUS_OXIDE, (double) (float) localMinimum);
        assertEquals(1, GasReactionEvaluator.evaluate(catalog, exact).events().size());
        var below = mix(850, GasType.NITROUS_OXIDE, (double) Math.nextDown((float) localMinimum));
        assertTrue(GasReactionEvaluator.evaluate(catalog, below).events().isEmpty());
    }

    @Test void minimumEnergyGateUsesUpstreamNormalizedSimulationEnergy() {
        var reaction = new GasReactionData(Map.of(GasType.NITROUS_OXIDE, .0001), 850, 2000,
                AtmosphereUnits.localCellQuantityToReferenceTile(.6), 1,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.N2O_DECOMPOSITION)));
        var catalog = new PrototypeCatalog<>(Map.of(ResourceLocation.parse("moonstation14:energy_gate"), reaction));
        var gas = mix(1000, GasType.NITROUS_OXIDE, .0001);
        assertTrue(gas.thermalEnergy() > .6);
        assertTrue(gas.simulationThermalEnergy() < .6);
        assertTrue(GasReactionEvaluator.evaluate(catalog, gas).events().isEmpty());
    }

    @Test void sourceFloatTemperatureGateBoundariesAreInclusive() {
        var minimumGate = new GasReactionData(Map.of(GasType.OXYGEN, .01, GasType.PLASMA, .01),
                373.15, 373.15, 0, 1,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.PLASMA_FIRE)));
        var catalog = new PrototypeCatalog<>(Map.of(ResourceLocation.parse("moonstation14:float_gate"), minimumGate));
        var atBoundary = mix(373.15f, GasType.OXYGEN, .004, GasType.PLASMA, .004);
        assertEquals(0, GasReactionEvaluator.evaluate(catalog, atBoundary, 7).fireReactionResultMoles());
        var belowBoundary = mix(Math.nextDown(373.15f), GasType.OXYGEN, .004, GasType.PLASMA, .004);
        assertEquals(7, GasReactionEvaluator.evaluate(catalog, belowBoundary, 7).fireReactionResultMoles());
        var aboveBoundary = mix(Math.nextUp(373.15f), GasType.OXYGEN, .004, GasType.PLASMA, .004);
        assertEquals(7, GasReactionEvaluator.evaluate(catalog, aboveBoundary, 7).fireReactionResultMoles());
    }

    @Test void priorityTwoTieHasNoInventedIdOrderAndUsesEvolvingRequirements() {
        var before = mix(50, GasType.OXYGEN, 50.0, GasType.TRITIUM, 2.0,
                GasType.NITROGEN, 1.0, GasType.AMMONIA, 1.0);
        var result = GasReactionEvaluator.evaluate(catalog(NAMES), before);
        assertEquals("frezon_production", result.events().getFirst().id().getPath());
        assertTrue(result.events().stream().noneMatch(e -> e.id().getPath().equals("ammonia_oxygen")));
        budget(before, result);
        // The packaged temperature windows do not overlap; when widened, priority ties have no
        // invented ID ordering requirement.
        var loaded = catalog("frezon_production", "ammonia_oxygen");
        var ammonia = loaded.get(ResourceLocation.parse("moonstation14:ammonia_oxygen"));
        var widened = new GasReactionData(ammonia.minimumRequirements(), 2.7, ammonia.maximumTemperature(),
                ammonia.minimumEnergy(), ammonia.priority(), ammonia.effects());
        Map<ResourceLocation, GasReactionData> inputOrder = new LinkedHashMap<>();
        inputOrder.put(ResourceLocation.parse("moonstation14:ammonia_oxygen"), widened);
        inputOrder.put(ResourceLocation.parse("moonstation14:frezon_production"),
                loaded.get(ResourceLocation.parse("moonstation14:frezon_production")));
        var tied = GasReactionEvaluator.evaluate(new PrototypeCatalog<>(inputOrder), before);
        assertEquals(2, tied.events().size());
        assertEquals(2, tied.events().stream().map(GasReactionEvaluator.Event::id).distinct().count());
        assertEquals("ammonia_oxygen", tied.events().getFirst().id().getPath());
        assertEquals("frezon_production", tied.events().get(1).id().getPath());
        budget(before, tied);
        var hot = mix(400, GasType.OXYGEN, 2.0, GasType.AMMONIA, 2.0);
        assertEquals("ammonia_oxygen", GasReactionEvaluator.evaluate(catalog(NAMES), hot).events().getFirst().id().getPath());
    }

    @Test void coldProductionCatalystAndAmmoniaConcentration() {
        var before = new GasMixture(Map.of(GasType.OXYGEN, 50.0, GasType.TRITIUM, 1.0,
                GasType.NITROGEN, 1.0), Math.nextDown((float) 73.15), 2.5);
        var result = run("frezon_production", before);
        assertEquals("frezon_production", result.events().getFirst().id().getPath());
        assertTrue(result.mixture().moles(GasType.FREZON) > 0);
        assertTrue(result.mixture().moles(GasType.OXYGEN) < before.moles(GasType.OXYGEN));
        assertTrue(result.mixture().moles(GasType.TRITIUM) < before.moles(GasType.TRITIUM));
        assertEquals(before.temperatureKelvin(), result.mixture().temperatureKelvin(), 0);
        assertEquals(2.5, result.mixture().volumeCubicMeters(), 1e-6);
        assertEquals((result.mixture().heatCapacity() - before.heatCapacity()) * before.temperatureKelvin(),
                result.energyDeltaJoules(), .01);
        budget(before, result);
        assertTrue(run("frezon_production", mix(73.15, GasType.OXYGEN, 1.0,
                GasType.TRITIUM, 1.0, GasType.NITROGEN, .0)).events().isEmpty());
        var ammonia = mix((double) Math.nextUp((float) 323.149), GasType.AMMONIA, 2.0, GasType.OXYGEN, 2.0);
        var reacted = run("ammonia_oxygen", ammonia);
        assertEquals(.025, reacted.events().getFirst().extentMoles(), 1e-7);
        assertEquals(.0125, reacted.mixture().moles(GasType.NITROUS_OXIDE), 1e-7);
        assertEquals(.0375, reacted.mixture().moles(GasType.WATER_VAPOR), 1e-7);
        budget(ammonia, reacted);
        assertTrue(run("ammonia_oxygen", mix(323.149, GasType.AMMONIA, 2.0)).events().isEmpty());
    }

    @Test void inefficientFrezonProductionIsCatalystLimitedAndReturnsNitrogen() {
        var before = mix(36.575, GasType.OXYGEN, 50.0, GasType.TRITIUM, 1.0, GasType.NITROGEN, .01);
        var result = run("frezon_production", before);
        assertEquals(1, result.events().size());
        assertEquals(.00008, result.events().getFirst().extentMoles(), 1e-8);
        assertEquals(49.996, result.mixture().moles(GasType.OXYGEN), 1e-5);
        assertEquals(.99992, result.mixture().moles(GasType.TRITIUM), 1e-7);
        assertEquals(.00204, result.mixture().moles(GasType.FREZON), 1e-8);
        assertEquals(.01204, result.mixture().moles(GasType.NITROGEN), 1e-7);
        budget(before, result);
    }

    @Test void inertNitrogenDilutesAmmoniaRateWithoutBeingConsumed() {
        var before = mix(400, GasType.AMMONIA, 2.0, GasType.OXYGEN, 2.0, GasType.NITROGEN, 16.0);
        var result = run("ammonia_oxygen", before);
        assertEquals(.00004, result.events().getFirst().extentMoles(), 1e-8);
        assertEquals(1.99996, result.mixture().moles(GasType.AMMONIA), 1e-6);
        assertEquals(1.99996, result.mixture().moles(GasType.OXYGEN), 1e-6);
        assertEquals(.00002, result.mixture().moles(GasType.NITROUS_OXIDE), 1e-8);
        assertEquals(.00006, result.mixture().moles(GasType.WATER_VAPOR), 1e-8);
        assertEquals(16, result.mixture().moles(GasType.NITROGEN));
        budget(before, result);
    }

    @Test void coolantSignedHeatAndLimitedNitrogen() {
        var before = mix(373.15, GasType.FREZON, 1.0, GasType.NITROGEN, 100.0);
        var result = run("frezon_coolant", before);
        assertEquals(-30000, result.energyDeltaJoules(), .01);
        assertEquals(.95, result.mixture().moles(GasType.FREZON), 1e-7);
        assertEquals(99.75, result.mixture().moles(GasType.NITROGEN), 1e-5);
        assertEquals(.3, result.mixture().moles(GasType.NITROUS_OXIDE), 1e-7);
        budget(before, result);
        var starved = mix(373.15, GasType.FREZON, 1.0, GasType.NITROGEN, .01);
        assertEquals(.01, run("frezon_coolant", starved).mixture().moles(GasType.NITROUS_OXIDE) - .05, 1e-7);
        assertTrue(run("frezon_coolant", mix(23.15, GasType.FREZON, 1.0, GasType.NITROGEN, 1.0)).events().isEmpty());
    }

    @Test void hotCoolantMultipliesCoolingEvenWhenNitrogenIsScarce() {
        var before = mix(723.15, GasType.FREZON, 1.0, GasType.NITROGEN, .01);
        var result = run("frezon_coolant", before);
        assertEquals(.05, result.events().getFirst().extentMoles(), 1e-7);
        assertEquals(-60000, result.energyDeltaJoules(), .02);
        assertEquals(.95, result.mixture().moles(GasType.FREZON), 1e-7);
        assertEquals(0, result.mixture().moles(GasType.NITROGEN));
        assertEquals(.06, result.mixture().moles(GasType.NITROUS_OXIDE), 1e-7);
        budget(before, result);
    }

    @Test void allPackagedMinimumsAreInclusiveAndJustBelowIsIneligible() {
        var loaded = catalog(NAMES);
        for (String name : NAMES) {
            var reaction = loaded.get(ResourceLocation.parse("moonstation14:" + name));
            for (GasType boundary : reaction.minimumRequirements().keySet()) {
                // A detached decomposition effect isolates the catalog requirement gate from
                // the individual reaction's rate and temperature thresholds.
                var gated = new GasReactionData(reaction.minimumRequirements(), 2.7, 1000, 0,
                        reaction.priority(), List.of(new GasReactionData.Effect(GasReactionData.EffectType.N2O_DECOMPOSITION)));
                var fixture = new PrototypeCatalog<>(Map.of(ResourceLocation.parse("moonstation14:gate"), gated));
                Map<GasType, Double> amounts = new java.util.EnumMap<>(GasType.class);
                for (GasType gas : reaction.minimumRequirements().keySet()) amounts.put(gas, 1.0);
                amounts.put(GasType.NITROUS_OXIDE, 2.0);
                double localRequirement = AtmosphereUnits.referenceTileQuantity(
                        reaction.minimumRequirements().get(boundary));
                amounts.put(boundary, (double) (float) localRequirement);
                var exact = new GasMixture(amounts, 850);
                assertEquals(1, GasReactionEvaluator.evaluate(fixture, exact).events().size(), name + ":" + boundary);
                amounts.put(boundary, (double) Math.nextDown((float) localRequirement));
                var below = new GasMixture(amounts, 850);
                var rejected = GasReactionEvaluator.evaluate(fixture, below);
                assertTrue(rejected.events().isEmpty(), name + ":" + boundary);
                assertSame(below, rejected.mixture(), name + ":" + boundary);
            }
        }
    }

    @Test void tritiumBothBranchesAndPlasmaThresholdAndSupersaturation() {
        var low = mix(400, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0);
        var lowResult = run("tritium_fire", low);
        assertEquals(.01, lowResult.events().getFirst().extentMoles(), 1e-12);
        assertEquals(.01, lowResult.fireReactionResultMoles(), 1e-12);
        assertEquals(2840, lowResult.energyDeltaJoules(), 1e-9);
        budget(low, lowResult);
        var high = mix(400, GasType.TRITIUM, 1.0, GasType.OXYGEN, 100.0);
        var highResult = run("tritium_fire", high);
        assertEquals(.1, highResult.events().getFirst().extentMoles(), 1e-12);
        assertEquals(.1, highResult.fireReactionResultMoles(), 1e-12);
        assertEquals(284000, highResult.energyDeltaJoules(), 1e-8);
        budget(high, highResult);
        assertTrue(run("plasma_fire", mix(373.15, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0)).events().isEmpty());
        assertTrue(run("plasma_fire", mix(373.151, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0)).events().isEmpty());
        assertTrue(run("plasma_fire", mix(373.149, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0)).events().isEmpty());
        var plasma = mix(1643.15, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0);
        var plasmaResult = run("plasma_fire", plasma);
        assertEquals(1.0 / 9, plasmaResult.mixture().moles(GasType.TRITIUM), 1e-7);
        assertEquals(0, plasmaResult.mixture().moles(GasType.CARBON_DIOXIDE));
        assertEquals(160000.0 / 9, plasmaResult.energyDeltaJoules(), 1e-8);
        budget(plasma, plasmaResult);
    }

    @Test void oxygenPoorPlasmaProducesCarbonDioxideAndPreliminaryRateIsStrict() {
        var poor = mix(1643.15, GasType.PLASMA, 1.0, GasType.OXYGEN, 5.0);
        var result = run("plasma_fire", poor);
        assertEquals(1, result.events().size());
        assertEquals(7.0 / 90, result.events().getFirst().extentMoles(), 1e-7);
        assertEquals(17.0 / 18, result.mixture().moles(GasType.PLASMA), 1e-7);
        assertEquals(5 - 1.0 / 45, result.mixture().moles(GasType.OXYGEN), 1e-6);
        assertEquals(1.0 / 18, result.mixture().moles(GasType.CARBON_DIOXIDE), 1e-7);
        assertEquals(0, result.mixture().moles(GasType.TRITIUM));
        assertEquals(80000.0 / 9, result.energyDeltaJoules(), 1e-8);
        budget(poor, result);

        // The upstream per-tile rate cutoff is converted to a 1,000 L cell.
        var atThreshold = mix(1643.15, GasType.PLASMA, 1.0, GasType.OXYGEN,
                (double) Math.nextDown((float) .0108));
        assertTrue(atThreshold.moles(GasType.OXYGEN) / 10 / 9 <= .00012);
        var noOp = run("plasma_fire", atThreshold);
        assertSame(atThreshold, noOp.mixture());
        assertTrue(noOp.events().isEmpty());
        var invokedNoOp = GasReactionEvaluator.evaluate(catalog("plasma_fire"), atThreshold, .25);
        assertEquals(0, invokedNoOp.fireReactionResultMoles());
        var above = mix(1643.15, GasType.PLASMA, 1.0, GasType.OXYGEN,
                (double) Math.nextUp((float) .0108));
        assertTrue(above.moles(GasType.OXYGEN) / 10 / 9 > .00012);
        var reacted = run("plasma_fire", above);
        assertEquals(.000120001 * 1.4, reacted.events().getFirst().extentMoles(), 1e-8);
        budget(above, reacted);
    }

    @Test void lowerPriorityTritiumFireOwnsPersistentResultSlotAfterPlasmaPass() {
        var before = mix(1643.15, GasType.PLASMA, 1.0, GasType.TRITIUM, .01, GasType.OXYGEN, .00401);
        var result = GasReactionEvaluator.evaluate(catalog("plasma_fire", "tritium_fire"), before, .25);
        assertEquals(1, result.events().size());
        assertEquals("tritium_fire", result.events().getFirst().id().getPath());
        assertEquals(.0000401, result.events().getFirst().extentMoles(), 1e-8);
        assertEquals(.00398995, result.mixture().moles(GasType.OXYGEN), 1e-8);
        assertEquals(1.0, result.mixture().moles(GasType.PLASMA));
        assertEquals(result.events().getLast().extentMoles(), result.fireReactionResultMoles(), 1e-8,
                "priority order invokes plasma before tritium, leaving the later tritium slot");
        budget(before, result);
    }

    @Test void mixedFiresHaveOnePassAndExtremeValidation() {
        var mixed = mix(1643.15, GasType.TRITIUM, .01, GasType.PLASMA, 1.0, GasType.OXYGEN, 100.0);
        var result = GasReactionEvaluator.evaluate(catalog("plasma_fire", "tritium_fire"), mixed);
        assertEquals(2, result.events().size());
        assertEquals("tritium_fire", result.events().getFirst().id().getPath());
        assertEquals("plasma_fire", result.events().getLast().id().getPath());
        assertEquals(result.events().getLast().extentMoles(), result.fireReactionResultMoles(), 1e-12);
        assertTrue(result.mixture().moles(GasType.TRITIUM) > mixed.moles(GasType.TRITIUM));
        budget(mixed, result);
        var nonFire = GasReactionEvaluator.evaluate(catalog("n2o_decomposition"),
                mix(850, GasType.NITROUS_OXIDE, 2.0), .25);
        assertEquals(.25, nonFire.fireReactionResultMoles());
        var noFirePrototypeGate = GasReactionEvaluator.evaluate(catalog("tritium_fire"),
                mix(50, GasType.TRITIUM, 1.0, GasType.OXYGEN, 50.0), .25);
        assertEquals(.25, noFirePrototypeGate.fireReactionResultMoles());
        var huge = mix(1e300, GasType.PLASMA, 1e-100, GasType.OXYGEN, 1e-100);
        assertSame(huge, run("plasma_fire", huge).mixture());
        assertThrows(IllegalArgumentException.class, () -> mix(Double.NaN, GasType.OXYGEN, 1.0));
    }

    @Test void producedN2oIsEligibleLaterButPreloopTemperatureStillGates() {
        var hot = mix(850, GasType.AMMONIA, 2.0, GasType.OXYGEN, 2.0);
        var result = GasReactionEvaluator.evaluate(catalog("n2o_decomposition", "ammonia_oxygen"), hot);
        assertEquals(2, result.events().size());
        assertEquals("ammonia_oxygen", result.events().getFirst().id().getPath());
        assertEquals("n2o_decomposition", result.events().get(1).id().getPath());
        assertEquals(.00625, result.mixture().moles(GasType.NITROUS_OXIDE), 1e-7);
        budget(hot, result);
        var cold = mix(849, GasType.AMMONIA, 2.0, GasType.OXYGEN, 2.0);
        var coldResult = GasReactionEvaluator.evaluate(catalog("n2o_decomposition", "ammonia_oxygen"), cold);
        assertEquals(1, coldResult.events().size());
        assertEquals(.0125, coldResult.mixture().moles(GasType.NITROUS_OXIDE), 1e-7);
        budget(cold, coldResult);
    }

    @Test void allEffectsSelectorPreservesLegacySixEffectPass() {
        for (var gas : List.of(
                mix(50, GasType.TRITIUM, 1.0, GasType.OXYGEN, 50.0, GasType.NITROGEN, 1.0),
                mix(373.15, GasType.FREZON, 1.0, GasType.NITROGEN, 100.0,
                        GasType.AMMONIA, 2.0, GasType.OXYGEN, 2.0),
                mix(1643.15, GasType.TRITIUM, 1.0, GasType.PLASMA, 1.0,
                        GasType.OXYGEN, 100.0, GasType.AMMONIA, 2.0, GasType.NITROUS_OXIDE, 2.0))) {
            var legacy = GasReactionEvaluator.evaluate(catalog(NAMES), gas);
            var selected = GasReactionEvaluator.evaluate(catalog(NAMES), gas, type -> true);
            assertEquals(legacy.events(), selected.events());
            assertEquals(legacy.speciesDelta(), selected.speciesDelta());
            assertEquals(legacy.energyDeltaJoules(), selected.energyDeltaJoules());
            assertEquals(legacy.mixture().gasMoles(), selected.mixture().gasMoles());
            assertEquals(legacy.mixture().temperatureKelvin(), selected.mixture().temperatureKelvin());
            budget(gas, selected);
        }
    }

    @Test void selectorFiltersEffectsNotPrototypesAndKeepsPrepassGateAndOrder() {
        var both = new GasReactionData(Map.of(GasType.OXYGEN, .01, GasType.TRITIUM, .01),
                373.149, 2000, 0, 3, List.of(
                new GasReactionData.Effect(GasReactionData.EffectType.N2O_DECOMPOSITION),
                new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)));
        var earlier = new GasReactionData(Map.of(GasType.NITROUS_OXIDE, .01),
                2.7, 2000, 0, 2, List.of(new GasReactionData.Effect(GasReactionData.EffectType.N2O_DECOMPOSITION)));
        var tooHot = new GasReactionData(Map.of(GasType.OXYGEN, .01),
                2.7, 399, 0, 1, List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)));
        var catalog = new PrototypeCatalog<>(Map.of(
                ResourceLocation.parse("moonstation14:both"), both,
                ResourceLocation.parse("moonstation14:later"), earlier,
                ResourceLocation.parse("moonstation14:cold_gate"), tooHot));
        var gas = mix(400, GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0, GasType.NITROUS_OXIDE, 2.0);
        var result = GasReactionEvaluator.evaluate(catalog, gas,
                type -> type == GasReactionData.EffectType.TRITIUM_FIRE);
        assertEquals(1, result.events().size());
        assertEquals("both", result.events().getFirst().id().getPath());
        assertEquals(GasReactionData.EffectType.TRITIUM_FIRE, result.events().getFirst().effect());
        assertEquals(2, result.mixture().moles(GasType.NITROUS_OXIDE));
        budget(gas, result);
        var none = GasReactionEvaluator.evaluate(catalog, gas, type -> false);
        assertSame(gas, none.mixture());
        assertTrue(none.events().isEmpty());
    }
}
