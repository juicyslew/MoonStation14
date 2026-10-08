package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry;
import com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneData;
import com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneExamination;
import com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-world scene-visibility fixture. It reads current time and never changes shared catalogs or world clocks. */
@PrefixGameTestTemplate(false)
@GameTestTags("examine_scene")
public final class CelestialSceneGameTests {
    private CelestialSceneGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", timeoutTicks=20)
    public static void currentVisibleSceneAndLoadedShapeLosAreUsed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var catalog = ModCelestialScenes.catalog(level);
        double time = level.getTimeOfDay(1);
        boolean visible = java.util.stream.Stream.of(ModCelestialScenes.SUN, ModCelestialScenes.EARTH)
                .map(catalog::get).filter(java.util.Objects::nonNull)
                .anyMatch(profile -> CelestialSceneGeometry.available(level, profile, false)
                        && CelestialSceneGeometry.visibleDirection(profile, time) != null);
        require(visible, "resolved current Overworld scene geometry has an above-horizon body");

        BlockPos line = helper.absolutePos(new BlockPos(2, 2, 4));
        Vec3 from = Vec3.atCenterOf(line), to = from.add(24, 0, 0);
        require(ExamineTargetResolver.visibilityAlongRay(level, from, to).visible(),
                "loaded unobstructed virtual-scene LOS direction is accepted");
        var sourceProfile = catalog.get(ModCelestialScenes.SUN);
        var shortVirtualScene = new CelestialSceneData(sourceProfile.dimension(), sourceProfile.side(),
                sourceProfile.radius(), 2, sourceProfile.red(), sourceProfile.green(), sourceProfile.blue(),
                sourceProfile.metadata());
        BlockPos nearBeyondScene = line.east(4);
        level.setBlock(nearBeyondScene, Blocks.STONE.defaultBlockState(), 3);
        var shortSceneLos = CelestialSceneExamination.rayVisibility(level, from, new Vec3(1, 0, 0));
        require(!shortSceneLos.visible() && "collision".equals(shortSceneLos.reason()),
                "scene with virtual draw distance " + shortVirtualScene.skyDistance()
                        + " still detects a loaded opaque wall at four world units");
        level.setBlock(nearBeyondScene, Blocks.AIR.defaultBlockState(), 3);
        BlockPos wall = line.east(18);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        require(!ExamineTargetResolver.visibilityAlongRay(level, from, to).visible(),
                "loaded opaque geometry blocks virtual-scene fallback");
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
        Vec3 unloaded = new Vec3(100000, from.y, from.z);
        require(!ExamineTargetResolver.visibilityAlongRay(level, from, unloaded).visible(),
                "unloaded scene LOS fails closed without forcing chunks");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
