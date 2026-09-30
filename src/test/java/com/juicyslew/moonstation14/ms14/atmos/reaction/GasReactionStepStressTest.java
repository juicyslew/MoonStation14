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
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GasReactionStepStressTest {
    private static final int PER_FIXTURE = 160;
    private static final int CELLS = 800;

    private static PrototypeCatalog<GasReactionData> bundled() {
        var manager = new PrototypeManager();
        manager.register(ModGasReactions.GAS_REACTION_TYPE);
        Map<ResourceLocation, JsonObject> definitions = new LinkedHashMap<>();
        for (String name : List.of("frezon_production", "ammonia_oxygen", "frezon_coolant",
                "n2o_decomposition", "tritium_fire", "plasma_fire")) {
            String path = "/data/moonstation14/moonstation14/gas_reaction/" + name + ".json";
            try (var input = GasReactionStepStressTest.class.getResourceAsStream(path)) {
                assertNotNull(input, path);
                definitions.put(ResourceLocation.parse("moonstation14:" + name),
                        JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        }
        manager.reload(ModGasReactions.GAS_REACTION_TYPE, definitions);
        return manager.snapshot(ModGasReactions.GAS_REACTION_TYPE);
    }

    @Test void eightHundredDetachedCellsMatchIndependentSpeciesAndEnergyLedgers() {
        var catalog = bundled();
        List<GasMixture> fixtures = List.of(
                GasMixture.vacuum(),
                new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 50., GasType.NITROGEN, 1.), 73.15),
                new GasMixture(Map.of(GasType.NITROUS_OXIDE, 2.), 850),
                new GasMixture(Map.of(GasType.AMMONIA, 2., GasType.OXYGEN, 2.), 400),
                new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.PLASMA, 1., GasType.OXYGEN, 100.), 1643.15));
        EnumMap<GasType, Double> expected = new EnumMap<>(GasType.class);
        double expectedHeat = 0;
        int expectedEvents = 0;
        for (GasMixture fixture : fixtures) {
            var independent = GasReactionStep.evaluate(catalog, fixture, null);
            for (GasType type : GasType.values())
                expected.merge(type, PER_FIXTURE * (independent.mixture().moles(type) - fixture.moles(type)), Double::sum);
            expectedHeat += PER_FIXTURE * (independent.mixture().thermalEnergy() - fixture.thermalEnergy());
            expectedEvents += PER_FIXTURE * independent.events().size();
        }

        EnumMap<GasType, Double> aggregate = new EnumMap<>(GasType.class);
        double initialEnergy = 0, finalEnergy = 0, declaredHeat = 0;
        int evaluations = 0, events = 0, changedCells = 0, vacuumWrites = 0;
        for (int cell = 0; cell < CELLS; cell++) {
            GasMixture before = fixtures.get(cell % fixtures.size());
            var result = GasReactionStep.evaluate(catalog, before, null);
            evaluations++;
            if (!result.events().isEmpty()) changedCells++;
            if (before == fixtures.getFirst()) {
                assertSame(before, result.mixture(), "ambient vacuum must not materialize a new override");
                assertTrue(result.events().isEmpty());
                assertTrue(result.speciesDelta().isEmpty());
                assertEquals(0, result.energyDeltaJoules());
                if (result.mixture() != before) vacuumWrites++;
            }
            for (GasType type : GasType.values()) {
                double delta = result.mixture().moles(type) - before.moles(type);
                assertEquals(delta, result.speciesDelta().getOrDefault(type, 0.), 1e-9);
                assertEquals(delta, result.events().stream()
                        .mapToDouble(event -> event.speciesDelta().getOrDefault(type, 0.)).sum(), 1e-9);
                aggregate.merge(type, delta, Double::sum);
            }
            double eventHeat = result.events().stream()
                    .mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum();
            assertEquals(eventHeat, result.energyDeltaJoules(), 1e-7);
            assertEquals(before.thermalEnergy() + result.energyDeltaJoules(),
                    result.mixture().thermalEnergy(), Math.max(1e-7, before.thermalEnergy() * 1e-12));
            initialEnergy += before.thermalEnergy();
            finalEnergy += result.mixture().thermalEnergy();
            declaredHeat += result.energyDeltaJoules();
            events += result.events().size();
        }
        assertEquals(CELLS, evaluations);
        assertEquals(0, vacuumWrites);
        assertEquals(640, changedCells);
        assertEquals(expectedEvents, events);
        for (GasType type : GasType.values())
            assertEquals(expected.getOrDefault(type, 0.), aggregate.getOrDefault(type, 0.), 1e-6, type.name());
        assertEquals(expectedHeat, declaredHeat, Math.max(1e-5, Math.abs(expectedHeat) * 1e-10));
        assertEquals(initialEnergy + declaredHeat, finalEnergy, Math.max(1e-5, finalEnergy * 1e-10));
    }
}
