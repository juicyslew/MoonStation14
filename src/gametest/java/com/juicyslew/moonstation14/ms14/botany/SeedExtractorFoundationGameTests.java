package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"botany", "botany-extractor-foundation"})
@PrefixGameTestTemplate(false)
public final class SeedExtractorFoundationGameTests {
    private SeedExtractorFoundationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void registeredExtractorResolvesProfileAndRetainsPlacementIdentity(GameTestHelper helper) {
        var pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.SEED_EXTRACTOR.get());
        var placed = (SeedExtractorBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(placed != null && !placed.isPowered(), "receiver is unavailable before server profile initialization");
        helper.runAfterDelay(2, () -> {
            var entity = (SeedExtractorBlockEntity) helper.getBlockEntity(pos);
                var profile = entity.resolvedProfile();
                helper.assertTrue(profile != null && profile.extractor() && profile.minimumPacketYield() == 1
                                && profile.maximumPacketYield() == 3 && profile.chamberCapacity() == 0,
                        "resolved inherited extractor defaults are active");
                helper.assertTrue(entity.isPowered(), "zero-load receiver is available without an APC");
                var identity = entity.placementIdentity();
                var tag = entity.saveWithFullMetadata(helper.getLevel().registryAccess());
                var absolutePos = helper.absolutePos(pos);
                var restored = new SeedExtractorBlockEntity(absolutePos, helper.getLevel().getBlockState(absolutePos));
                restored.loadWithComponents(tag, helper.getLevel().registryAccess());
                helper.assertTrue(identity.equals(restored.placementIdentity()), "placement identity survives save and recreation");
                helper.assertTrue(entity.isLoadedValid(), "extractor is valid only in its loaded level");
                helper.succeed();
        });
    }
}
