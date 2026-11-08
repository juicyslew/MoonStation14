package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.AtmosphereRoomGameTests;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Shared finite, in-range tray exposure for ordinary botany native tests. */
public final class BotanyTrayGameTestEnvironment {
    private BotanyTrayGameTestEnvironment() { }

    static BlockPos prepare(GameTestHelper helper, Consumer<HydroponicsTrayBlockEntity> ready) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos trayPos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos exposure = trayPos.above();
        try {
            AtmosphereRoomGameTests.buildSealedHotspotCell(helper, 3, 3);
            helper.setBlock(new BlockPos(3, 1, 3), ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState());
            BoundedGameTestWait.safeDelay(helper, 2, () -> {
                try {
                    var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(trayPos);
                    require(tray != null && tray.plantState() != null, "resolved ordinary tray initialized");
                    var mixture = new GasMixture(Map.of(GasType.OXYGEN, 8.0, GasType.NITROGEN, 30.0), 293.15);
                    require(AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level,
                                    List.of(trayPos, exposure), mixture),
                            "trusted setup initializes only the sealed tray/exposure cells");
                    for (BlockPos cell : List.of(trayPos, exposure)) {
                        var sample = AtmosphereService.INSTANCE.sample(level, cell).orElse(null);
                        require(sample != null && sample.totalMoles() > 0
                                        && sample.pressureKpa(1.0) >= 81 && sample.pressureKpa(1.0) <= 121
                                        && Math.abs(sample.temperatureKelvin() - 293.15) < 0.01,
                                "finite breathable mixture sample required at " + cell + "; sample=" + sample
                                        + " tick=" + level.getGameTime());
                    }
                    ready.accept(tray);
                } catch (Throwable failure) { helper.testInfo.fail(failure); }
            });
        } catch (Throwable failure) { helper.testInfo.fail(failure); }
        return trayPos;
    }

    /** A larger sealed room keeps the authenticated actor and harvest drops inside the finite atmosphere. */
    public static BlockPos prepareAuthenticatedRoom(GameTestHelper helper, Consumer<HydroponicsTrayBlockEntity> ready) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos trayPos = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos exposure = trayPos.above();
        try {
            // Shared room owner: interior x/z 2..6, y 1..3; roof at y=4 clears the actor.
            AtmosphereRoomGameTests.buildSealedRoom(helper, 0, 2, 6, 1, 3);
            helper.setBlock(new BlockPos(4, 1, 4), ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState());
            BoundedGameTestWait.safeDelay(helper, 2, () -> {
                try {
                    var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(trayPos);
                    require(tray != null && tray.plantState() != null, "resolved authenticated tray initialized");
                    var mixture = new GasMixture(Map.of(GasType.OXYGEN, 8.0, GasType.NITROGEN, 30.0), 293.15);
                    var cells = java.util.stream.IntStream.rangeClosed(2, 6).boxed().flatMap(x ->
                            java.util.stream.IntStream.rangeClosed(1, 3).boxed().flatMap(y ->
                                    java.util.stream.IntStream.rangeClosed(2, 6)
                                            .mapToObj(z -> helper.absolutePos(new BlockPos(x, y, z))))).toList();
                    require(AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level, cells, mixture),
                            "trusted setup initializes only the sealed authenticated botany room");
                    require(cells.size() == 75 && new java.util.HashSet<>(cells).size() == 75 && cells.contains(exposure),
                            "complete unique 5x5x3 room cells include exposure exactly once");
                    int roofY = helper.absolutePos(new BlockPos(4, 4, 4)).getY();
                    require(trayPos.getY() + 1 + 1.8 < roofY,
                            "authenticated actor's feet plus standing height clear the y=4 roof");
                    var sample = AtmosphereService.INSTANCE.sample(level, exposure).orElse(null);
                    require(sample != null && sample.pressureKpa(1.0) >= 81 && sample.pressureKpa(1.0) <= 121
                                    && Math.abs(sample.temperatureKelvin() - 293.15) < 0.01,
                            "authenticated room has known breathable finite atmosphere; sample=" + sample);
                    ready.accept(tray);
                } catch (Throwable failure) { helper.testInfo.fail(failure); }
            });
        } catch (Throwable failure) { helper.testInfo.fail(failure); }
        return trayPos;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
