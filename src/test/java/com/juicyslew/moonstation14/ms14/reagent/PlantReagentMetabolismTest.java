package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.PlantMetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.botany.PlantReagentMetabolism;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlantReagentMetabolismTest {
    private static final ResourceKey<ReagentData> WATER = ModReagents.createKey("water");
    private static final ResourceKey<ReagentData> NUTRIENT = ModReagents.createKey("eznutrient");

    @Test
    void resolvedInheritedWaterAndNutrientApplyFixedEffectsAndConsumeOneUnit() throws IOException {
        var catalog = resolvedCatalog();
        ReagentData water = catalog.get(id("water"));
        ReagentData nutrient = catalog.get(id("eznutrient"));
        assertTrue(water.plantMetabolism().stream().anyMatch(PlantMetabolismData.PlantAdjustWater.class::isInstance),
                "water must receive PlantAdjustWater through resolved BaseDrink ancestry");
        assertEquals(1f, ((PlantMetabolismData.PlantAdjustWater) water.plantMetabolism().stream()
                .filter(PlantMetabolismData.PlantAdjustWater.class::isInstance).findFirst().orElseThrow()).amount());
        assertEquals(2f, ((PlantMetabolismData.PlantAdjustNutrition) nutrient.plantMetabolism().getFirst()).amount());

        ReagentAttachment mixture = new ReagentAttachment(Map.of(WATER, 50f, NUTRIENT, 50f));
        Target target = new Target();
        var result = PlantReagentMetabolism.poll(mixture, key -> catalog.get(key.location()), target);
        assertTrue(result.unsupportedEffects().isEmpty());
        assertEquals(1f, target.water);
        assertEquals(2f, target.nutrition);
        assertEquals(Map.of(WATER, 49f, NUTRIENT, 49f), mixture.getMap());
    }

    @Test
    void dispatchesByResolvedMetadataAndDoesNotApplyFractionalReagent() {
        ReagentData basis = fake("water", List.of(new PlantMetabolismData.PlantAdjustNutrition(3f)));
        ReagentData otherId = fake("unrelated-id", basis.plantMetabolism());
        ResourceKey<ReagentData> custom = ModReagents.createKey("custom");
        ResourceKey<ReagentData> fraction = ModReagents.createKey("fraction");
        Map<ResourceKey<ReagentData>, ReagentData> lookup = Map.of(custom, otherId, fraction,
                fake("fraction", List.of(new PlantMetabolismData.PlantAdjustWater(9f))));
        ReagentAttachment mixture = new ReagentAttachment(Map.of(custom, 1.25f, fraction, .5f));
        Target target = new Target();
        PlantReagentMetabolism.poll(mixture, lookup::get, target);
        assertEquals(0f, target.water);
        assertEquals(3f, target.nutrition);
        assertEquals(Map.of(custom, .25f), mixture.getMap());
    }

    @Test
    void mixedEntriesConsumeExactUnitsAndReportUnsupportedEffects() {
        ResourceKey<ReagentData> water = ModReagents.createKey("w");
        ResourceKey<ReagentData> unsupported = ModReagents.createKey("other");
        ResourceKey<ReagentData> fraction = ModReagents.createKey("fraction");
        Map<ResourceKey<ReagentData>, ReagentData> lookup = new LinkedHashMap<>();
        lookup.put(water, fake("w", List.of(new PlantMetabolismData.PlantAdjustWater(1f))));
        lookup.put(unsupported, fake("other", List.of(new PlantMetabolismData.RobustHarvest())));
        lookup.put(fraction, fake("fraction", List.of()));
        ReagentAttachment mixture = new ReagentAttachment(Map.of(water, 1f, unsupported, 1f, fraction, .07f));
        Target target = new Target();
        var result = PlantReagentMetabolism.poll(mixture, lookup::get, target);
        assertEquals(1f, target.water);
        assertEquals(List.of(new PlantReagentMetabolism.UnsupportedEffect("other", "RobustHarvest")),
                result.unsupportedEffects());
        assertTrue(mixture.isEmpty(), "all entries are consumed by one unit, including fractional remainder");
    }

    @Test
    void unknownResolvedReagentFailsBeforeEffectsOrConsumption() {
        ResourceKey<ReagentData> known = ModReagents.createKey("known");
        ResourceKey<ReagentData> missing = ModReagents.createKey("missing");
        ReagentAttachment mixture = new ReagentAttachment(Map.of(known, 1f, missing, .5f));
        Map<ResourceKey<ReagentData>, Long> before = mixture.snapshotUnits();
        Target target = new Target();
        assertThrows(NullPointerException.class,
                () -> PlantReagentMetabolism.poll(mixture,
                        key -> key.equals(known) ? fake("known", List.of(new PlantMetabolismData.PlantAdjustWater(1f))) : null,
                        target));
        assertEquals(0f, target.water);
        assertEquals(before, mixture.snapshotUnits());
    }

    private static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<ReagentData> resolvedCatalog()
            throws IOException {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);
        manager.reload(ModReagents.REAGENT_TYPE, ReagentResourceSmokeTest.loadResources());
        return manager.snapshot(ModReagents.REAGENT_TYPE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static ReagentData fake(String id, List<PlantMetabolismData> effects) {
        return new ReagentData(id, id, "test", "", "", "", 0, Map.of(), 0f, 0f,
                com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum.NONE, effects, Map.of(),
                new com.juicyslew.moonstation14.component.codec.json.metamorphic.MetamorphicSpriteData(null, null),
                0f, "fill-", false, 0f);
    }

    private static final class Target implements PlantReagentMetabolism.Target {
        float water;
        float nutrition;
        @Override public void adjustWater(float amount) { water += amount; }
        @Override public void adjustNutrition(float amount) { nutrition += amount; }
    }
}
