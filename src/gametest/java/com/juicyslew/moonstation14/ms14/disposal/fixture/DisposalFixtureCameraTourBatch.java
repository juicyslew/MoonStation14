package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.PowerSimulationGate;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@GameTestHolder("moonstation14_camera_tour")
@PrefixGameTestTemplate(false)
public final class DisposalFixtureCameraTourBatch {
    private static boolean previousPower;
    private static boolean previousAtmos;
    private static Set<net.minecraft.resources.ResourceKey<Level>> previousVacuum;

    private DisposalFixtureCameraTourBatch() { }

    @BeforeBatch(batch = "disposals_camera_tour")
    public static void setup(ServerLevel level) {
        AtmosphereService atmos = AtmosphereService.INSTANCE;
        previousPower = PowerSimulationGate.isEnabled();
        previousAtmos = atmos.isEnabled();
        previousVacuum = atmos.vacuumDimensionsForTest();
        PowerSimulationGate.configureAtServerStart(true);
        atmos.configureAtServerStart(true, Set.of(level.dimension()));
    }

    @AfterBatch(batch = "disposals_camera_tour")
    public static void teardown(ServerLevel level) {
        PowerSimulationGate.configureAtServerStart(previousPower);
        AtmosphereService.INSTANCE.configureAtServerStart(previousAtmos, previousVacuum);
    }
}
