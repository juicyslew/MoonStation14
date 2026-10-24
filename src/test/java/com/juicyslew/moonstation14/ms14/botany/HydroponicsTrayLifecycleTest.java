package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Focused persistence and poll-boundary coverage for tray-owned simulation state. */
class HydroponicsTrayLifecycleTest {
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("moonstation14", value); }
    private static final TrayProfile NORMAL = new TrayProfile(60, 300, 100, 100, true, 20000, 5000);
    private static final SeedProfile SEED = new SeedProfile(id("packet"), id("produce"), "plant",
            new SeedProfile.Biology(30, 0, 0, 293, 10, 81, 121, 5, 10, 4),
            new SeedProfile.Lifecycle(5, 25, 10, 5, 1, 1, SeedProfile.Harvest.REPEAT), Map.of(), "test-plant-name");
    private static final RandomGenerator NO_RANDOM = new RandomGenerator() { @Override public long nextLong() { return 0; } };

    @Test void duePollPersistsItsNextUpdateEvenWhenCycleIsNotDueAndDoesNotCatchUp() {
        PlantInstance plant = new PlantInstance(100, 100, 0);
        var result = PlantMetabolismEvaluator.poll(plant, NORMAL, null, 60, ignored -> {}, NO_RANDOM, Optional.empty()).orElseThrow();
        assertFalse(result.changed());
        assertEquals(0, plant.lastCycle());
        assertEquals(120, plant.nextUpdate());
        var stored = PlantInstance.CODEC.encodeStart(NbtOps.INSTANCE, plant).getOrThrow();
        PlantInstance restored = PlantInstance.CODEC.parse(NbtOps.INSTANCE, stored).getOrThrow();
        assertEquals(plant.nextUpdate(), restored.nextUpdate());
        assertEquals(plant.revision(), restored.revision());
        assertTrue(PlantMetabolismEvaluator.poll(restored, NORMAL, null, 1000, ignored -> {}, NO_RANDOM, Optional.empty()).isPresent());
        assertEquals(1060, restored.nextUpdate());
        assertEquals(1000, restored.lastCycle());
    }

    @Test void persistedPlantAndSoilCodecsRetainExactStateAndReagents() {
        PlantInstance plant = new PlantInstance(44, 31, 12);
        plant.plant(id("wheat"), SEED, 12);
        plant.soil(44, 31, 2, 3);
        var encodedPlant = PlantInstance.CODEC.encodeStart(NbtOps.INSTANCE, plant).getOrThrow();
        var restoredPlant = PlantInstance.CODEC.parse(NbtOps.INSTANCE, encodedPlant).getOrThrow();
        assertEquals(plant.seed(), restoredPlant.seed());
        assertEquals(plant.status(), restoredPlant.status());
        assertEquals(plant.water(), restoredPlant.water());
        assertEquals(plant.nextUpdate(), restoredPlant.nextUpdate());
        var key = ModReagents.createKey("tray_persistence_test");
        ReagentAttachment soil = new ReagentAttachment(Map.of(key, 1.23f));
        var encodedSoil = ReagentAttachment.CODEC.encodeStart(NbtOps.INSTANCE, soil).getOrThrow();
        var restoredSoil = ReagentAttachment.CODEC.parse(NbtOps.INSTANCE, encodedSoil).getOrThrow();
        assertEquals(123L, restoredSoil.snapshotUnits().get(key));
    }

    @Test void resolvedNormalAndEmptyProfileValuesAreTheInitializationInputs() {
        var json = TrayProfile.CODEC.encodeStart(JsonOps.INSTANCE, NORMAL).getOrThrow();
        TrayProfile decoded = TrayProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(100, decoded.initialWater());
        assertEquals(100, decoded.initialNutrition());
        TrayProfile empty = new TrayProfile(decoded.pollDelayTicks(), decoded.cycleDelayTicks(), 0, 0,
                decoded.drawWarnings(), decoded.soilCapacityCents(), decoded.refillMaximumCents());
        assertEquals(0, empty.initialWater());
        assertEquals(0, empty.initialNutrition());
        assertEquals(20000, empty.soilCapacityCents());
    }
}
