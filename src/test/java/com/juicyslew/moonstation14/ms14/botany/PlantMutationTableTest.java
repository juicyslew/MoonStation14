package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlantMutationTableTest {
    @Test void sourceMutationTableLoadsOrderedAndTyped() {
        String path = "data/moonstation14/moonstation14/plant_mutation_table/random_plant_mutations.json";
        JsonObject json;
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        PrototypeRuntime.serverManager().reload(ModPlantMutations.TYPE,
                Map.of(ModPlantMutations.tableId("random_plant_mutations"), json));
        var catalog = ModPlantMutations.serverCatalog();
        var table = catalog.get(ModPlantMutations.tableId("random_plant_mutations"));
        assertNotNull(table);
        assertEquals(27, table.mutations().size());
        assertEquals(List.of("Sentient", "Slippery", "ChangeSpecies", "Unviable", "ChangeWaterConsumption",
                "ChangeNutrientConsumption", "ChangeIdealHeat", "ChangeHeatTolerance", "ChangeToxinsTolerance",
                "ChangeLowPressureTolerance", "ChangeHighPressureTolerance", "ChangePestTolerance", "ChangeWeedTolerance",
                "ChangeEndurance", "ChangeYield", "ChangeLifespan", "ChangeMaturation", "ChangeProduction", "ChangePotency",
                "ChangeSeedless", "Lignification", "Kudzufication", "ChangeScreaming", "ChangeChemicals",
                "ChangeExudeGasses", "ChangeConsumeGasses", "ChangeHarvest"), table.mutations().stream().map(PlantMutationTable.Entry::name).toList());
        var entries = table.mutations();
        assertFalse(entries.get(0).appliesToProduce()); assertFalse(entries.get(0).persists());
        assertFalse(entries.get(1).appliesToPlant()); assertTrue(entries.get(1).persists());
        assertFalse(entries.get(2).appliesToProduce()); assertFalse(entries.get(2).persists());
        for (int i=3;i<27;i++) { assertTrue(entries.get(i).appliesToPlant()); assertTrue(entries.get(i).appliesToProduce()); assertFalse(entries.get(i).persists()); }
        assertEquals(PlantStatMutation.Target.YIELD, entries.get(14).stat().orElseThrow().target());
        assertEquals(PlantMutationTable.EffectKind.STAT, entries.get(20).effectKind());
        assertEquals(PlantStatMutation.Target.LIGNEOUS, entries.get(20).stat().orElseThrow().target());
        assertEquals(.036f, entries.get(20).baseOdds()); assertFalse(entries.get(20).persists());
        assertEquals(PlantMutationTable.EffectKind.CHEMICALS, entries.get(23).effectKind());
        assertEquals(PlantMutationTable.EffectKind.GAS, entries.get(24).effectKind());
        assertEquals(PlantGasMutation.Target.EXUDE, entries.get(24).gas().orElseThrow().target());
        assertEquals(PlantGasMutation.Target.CONSUME, entries.get(25).gas().orElseThrow().target());
        assertEquals(PlantMutationTable.EffectKind.HARVEST, entries.get(26).effectKind());
        assertNotNull(entries.get(26).harvest().orElseThrow());
        assertEquals(.01f, entries.get(24).gas().orElseThrow().minimum());
        assertEquals(.5f, entries.get(25).gas().orElseThrow().maximum());
        assertEquals(PlantMutationTable.EffectKind.STAT, entries.get(22).effectKind());
        assertEquals(PlantStatMutation.Target.CAN_SCREAM, entries.get(22).stat().orElseThrow().target());
        assertEquals("moonstation14:randompickbotanyreagent", entries.get(23).chemicalTable().orElseThrow().toString());
        var invalidYield = new com.google.gson.JsonObject();
        invalidYield.addProperty("target", "YIELD"); invalidYield.addProperty("minimum", 3.5f);
        invalidYield.addProperty("maximum", 10); invalidYield.addProperty("steps", 5);
        assertThrows(IllegalArgumentException.class, () -> PlantStatMutation.CODEC.parse(JsonOps.INSTANCE, invalidYield),
                "shared stat codec rejects rather than truncates malformed integer yield bounds");
    }
}
