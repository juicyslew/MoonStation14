package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "atmosphere", "power"})
@PrefixGameTestTemplate(false)
public final class DisposalFixtureCameraTourBatch {
    private static Set<net.minecraft.resources.ResourceKey<Level>> previousVacuum;

    private DisposalFixtureCameraTourBatch() { }

    @BeforeBatch(batch = "disposals_camera_tour")
    public static void setup(ServerLevel level) {
        AtmosphereService atmos = AtmosphereService.INSTANCE;
        previousVacuum = atmos.vacuumDimensionsForTest();
        atmos.configureAtServerStart(Set.of(level.dimension()), atmos.spaceDimensionsForTest());
    }

    @AfterBatch(batch = "disposals_camera_tour")
    public static void teardown(ServerLevel level) {
        AtmosphereService.INSTANCE.configureAtServerStart(previousVacuum, AtmosphereService.INSTANCE.spaceDimensionsForTest());
    }
}
