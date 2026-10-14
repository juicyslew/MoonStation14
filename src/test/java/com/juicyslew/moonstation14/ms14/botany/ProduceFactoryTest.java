package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.component.ModDataComponents;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class ProduceFactoryTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void harvestChemistryUsesSeedFormulaAndExactCentTotal() {
        var seed = resolvedWheat();
        var contents = ProduceFactory.generatedChemistry(seed, 5);
        assertEquals(525L, contents.get(net.minecraft.resources.ResourceKey.create(
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("flour"))));
        assertEquals(125L, contents.get(net.minecraft.resources.ResourceKey.create(
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("nutriment"))));
        assertEquals(650L, com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(contents.values()));
    }

    @Test void harvestInstanceMetadataRoundTripsAndRejectsInvalidState() {
        var data = new ProduceInstanceData(id("wheat"), 30, 650);
        var encoded = ProduceInstanceData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(data, ProduceInstanceData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertThrows(IllegalArgumentException.class, () -> new ProduceInstanceData(id("wheat"), 30, -1));
    }

    @Test void reagentCurrentStateCodecPreservesPartialAndEmptyValues() {
        var seed = resolvedWheat();
        var generated = ProduceFactory.generatedChemistry(seed, 5);
        generated.replaceAll((key, value) -> value / 2);
        var remaining = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(generated);
        var encoded = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, remaining).getOrThrow();
        var decoded = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(remaining, decoded);
        var empty = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of());
        var emptyJson = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, empty).getOrThrow();
        assertEquals(empty, com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.CODEC.parse(JsonOps.INSTANCE, emptyJson).getOrThrow());
    }

    @Test void invalidHarvestInputsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> ProduceFactory.generatedChemistry(null, 1));
        assertThrows(IllegalArgumentException.class, () -> ProduceFactory.generatedChemistry(resolvedWheat(), Float.NaN));
    }
    private static SeedProfile resolvedWheat() {
        var profiles = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String name : new String[]{"defaults", "wheat"}) {
            String path = "data/moonstation14/moonstation14/seed_profile/" + name + ".json";
            try (var stream = ProduceFactoryTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                profiles.put(id(name), com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        manager.reload(ModSeedProfiles.TYPE, profiles);
        return manager.snapshot(ModSeedProfiles.TYPE).get(id("wheat"));
    }
}
