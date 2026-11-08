package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused admission checks for the live, read-only exterior boundary proof. */
@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class ExteriorVentilationGameTests {
    private ExteriorVentilationGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "exterior_ventilation", timeoutTicks = 100,
            templateNamespace = "moonstation14")
    public static void loadedPassableExteriorHasPlanetaryAmbientBoundary(GameTestHelper helper) {
        var level = helper.getLevel();
        AtmosphereService atmosphere = AtmosphereService.INSTANCE;
        BlockPos exterior = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
        var lease = FixtureChunkTickets.around(helper, exterior, exterior, 1);
        atmosphere.activateCellAndNeighbors(level, exterior);
        BoundedGameTestWait.await(helper, 100,
                () -> "fixture cell did not receive normal exterior ownership admission",
                () -> atmosphere.exteriorBoundaryForImmediateTransfer(level, exterior).isPresent(), () -> {
                    var boundary = atmosphere.exteriorBoundaryForImmediateTransfer(level, exterior);
                    require(boundary.isPresent(), "admitted loaded passable exterior has boundary proof");
                    require(boundary.orElseThrow().kind() == AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT,
                            "non-space dimension classifies exterior as planetary ambient");
                    require(boundary.orElseThrow().validate(), "boundary remains valid while loaded and unchanged");
                    helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
                    require(!boundary.orElseThrow().validate(), "boundary invalidates when cell becomes blocked");
                    lease.close();
                    helper.succeed();
                });
    }

    /**
     * A second boundary admission scenario checks the real world-surface predicate rather
     * than relying on the arbitrary air cell in the template. Actual powered export remains
     * covered by the paid-flow owner; this fixture deliberately does not counterfeit its
     * private power/pipe setup.
     */
    @GameTestTags({"exterior-exchange-runtime"})
    @GameTest(template = "atmos_large_empty", batch = "exterior_ventilation", timeoutTicks = 100,
            templateNamespace = "moonstation14")
    public static void loadedSurfaceCellAdmitsPlanetaryAmbient(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 0, 2));
        FixtureChunkTickets.around(helper, anchor, anchor, 1);
        int x = anchor.getX();
        int z = anchor.getZ();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 2;
        BlockPos surfaceAir = new BlockPos(x, y, z);
        require(level.hasChunkAt(surfaceAir), "surface proof uses its already loaded column");
        require(level.getBlockState(surfaceAir).isAir(), "heightmap+2 is passable air");
        var boundary = AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level, surfaceAir);
        require(boundary.isPresent(), "loaded motion-blocking surface air admits exterior proof");
        require(boundary.orElseThrow().kind() == AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT,
                "surface exterior is planetary ambient");
        require(boundary.orElseThrow().validate(), "surface boundary proof validates unchanged");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
