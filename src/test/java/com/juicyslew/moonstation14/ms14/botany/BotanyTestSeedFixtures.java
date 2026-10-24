package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Shared resolved main seed fixture; preserves authored mutation references and their targets. */
final class BotanyTestSeedFixtures {
    private BotanyTestSeedFixtures() { }

    static Map<ResourceLocation, JsonObject> mainCatalog() {
        var names = BotanyCatalogCandidateTest.resources().keySet().stream()
                .map(ResourceLocation::getPath).toArray(String[]::new);
        return BotanyCatalogCandidateTest.mainResources("seed_profile", names);
    }

    static PrototypeManager manager() {
        var manager = new PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        manager.reload(ModSeedProfiles.TYPE, mainCatalog());
        return manager;
    }

    static SeedProfile resolved(ResourceLocation id) {
        return manager().snapshot(ModSeedProfiles.TYPE).get(id);
    }
}
