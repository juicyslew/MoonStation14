package com.juicyslew.moonstation14.ms14.item_processing;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtractableMaterialSourceTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void registeredMaterialProfilesResolveAsJuiceOnlyWholeUnitSources() {
        var manager = new PrototypeManager();
        manager.register(ModExtractableProfiles.TYPE);
        var raw = BotanyCatalogCandidateTest.mainResources("extractable_profile", "cotton_material", "pyrotton_material");
        var before = manager.snapshot();
        var resolved = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        manager.stage(Map.of(ModExtractableProfiles.TYPE, raw), catalogs ->
                catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, value) ->
                        resolved.put(key, ExtractableProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow())));
        assertSame(before, manager.snapshot());

        var cotton = resolved.get(id("cotton_material"));
        assertNotNull(cotton);
        assertNull(cotton.grindableSolutionName(), "material must not inherit extractable_base food grindability");
        assertEquals(Map.of(reagent("fiber"), 300L), cotton.juiceSolution().centContents());

        var pyrotton = resolved.get(id("pyrotton_material"));
        assertNotNull(pyrotton);
        assertNull(pyrotton.grindableSolutionName(), "material has no declared grindable solution");
        assertEquals(Map.of(reagent("fiber"), 300L, reagent("phlogiston"), 300L),
                pyrotton.juiceSolution().centContents());
    }

    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }
}
