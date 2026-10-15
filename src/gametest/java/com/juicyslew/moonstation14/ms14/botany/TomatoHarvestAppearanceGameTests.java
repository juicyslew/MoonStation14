package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Optional;
import java.util.random.RandomGenerator;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany"})
public final class TomatoHarvestAppearanceGameTests {
    private TomatoHarvestAppearanceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    @GameTestTags({"botany-tomato-harvest-appearance"})
    public static void repeatedTomatoHarvestPublishesGrowingAppearanceImmediately(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            try {
                var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(pos);
                require(tray != null && tray.plantState() != null, "resolved tray initialized");
                var seedId = ModSeedProfiles.seedId("tomato");
                var seed = ModSeedProfiles.serverLookup(seedId);
                var plant = tray.plantState();
                tray.commitPlant(seedId, seed, level.getGameTime());
                var profile = tray.resolvedTrayProfile();
                RandomGenerator steadyGrowth = new RandomGenerator() {
                    @Override public long nextLong() { return 0; }
                    @Override public int nextInt(int bound) { return bound - 1; }
                    @Override public int nextInt(int origin, int bound) { return origin; }
                    @Override public float nextFloat() { return .5f; }
                };
                // Explicitly arrange the domain lifecycle via its ordinary cycle evaluator.
                for (long now = plant.lastCycle() + profile.cycleDelayTicks(); !plant.ready() && now < 100_000;
                     now += profile.cycleDelayTicks())
                    PlantMetabolismEvaluator.cycle(plant, profile, seed, now, steadyGrowth,
                            Optional.of(new PlantMetabolismEvaluator.Environment(101, seed.biology().idealHeat())));
                require(plant.ready(), "tomato reached repeat-harvest readiness through domain cycles");
                var revision = plant.revision();
                require(PlantMetabolismEvaluator.previewHarvestYield(plant, seed, 1f) > 0, "actual tomato harvest preview");
                require(tray.commitHarvest(plant, revision, seed), "actual trusted tray harvest commit");
                require(tray.plantState() == plant && plant.seed().equals(seedId) && !plant.ready(), "repeat harvest preserves plant and clears readiness");
                require(plant.revision() > revision, "repeat harvest advances revision");
                var published = tray.getUpdateTag(level.registryAccess());
                require("GROWING".equals(published.getString("PlantAppearanceStatus"))
                                && published.getInt("PlantAppearanceStage") > 0
                                && "tomato".equals(published.getString("PlantAppearanceKey")),
                        "immediate serialized appearance is growing tomato, not harvest");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("tomato repeat harvest appearance: " + failure); }
        });
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
