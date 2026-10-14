package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"singularity-visual-catalog", "pa-client-sync"})
@PrefixGameTestTemplate(false)
public final class SingularityVisualCatalogGameTests {
    private SingularityVisualCatalogGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty")
    public static void normalServerReloadPublishesAllSingularityVisualProfiles(GameTestHelper helper) {
        var catalog = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                .snapshot(ModSingularityVisuals.TYPE);
        if (catalog.asMap().size() != 6) throw new GameTestAssertException(
                "normal server resource reload must publish six singularity visual profiles; found " + catalog.asMap().size());
        for (int level = 1; level <= 6; level++) {
            var profile = catalog.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "moonstation14", "level_" + level));
            if (profile == null || profile.level() != level || profile.frameWidthPixels() <= 0)
                throw new GameTestAssertException("missing/invalid server singularity visual level " + level);
        }
        helper.succeed();
    }
}
