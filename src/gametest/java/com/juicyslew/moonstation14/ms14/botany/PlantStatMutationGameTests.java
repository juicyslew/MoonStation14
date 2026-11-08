package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/** Native tray-tick proof for mutation-level chemistry and the next ordinary genetic cycle. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-genetics", "botany-stat-mutation"})
public final class PlantStatMutationGameTests {
    private PlantStatMutationGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 950)
    public static void unstableMutagenPollFeedsFirstCycleAndResetsMutationLevel(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        AtomicBoolean finished = new AtomicBoolean();
        BotanyTrayGameTestEnvironment.prepare(helper, tray -> {
                BlockPos pos = tray.getBlockPos();
                try {
                    var plant = tray.plantState();
                    var wheatId = ModSeedProfiles.seedId("wheat");
                    var wheat = ModSeedProfiles.serverLookup(wheatId);
                    require(wheat != null, "resolved wheat profile exists");
                    var gas = AtmosphereService.INSTANCE.sample(level, pos.above()).orElseThrow();
                    var environment = new PlantMetabolismEvaluator.Environment((float) gas.pressureKpa(1.0),
                            (float) gas.temperatureKelvin());
                    var warnings = PlantMetabolismEvaluator.improperEnvironment(wheat, environment);
                    require(!warnings.pressure() && !warnings.temperature(), "mutagen tray starts within actual wheat atmosphere bounds: cell="
                            + pos.above() + " tick=" + level.getGameTime() + " pressureKPa=" + environment.pressureKpa()
                            + " expected=[" + wheat.biology().lowPressure() + "," + wheat.biology().highPressure()
                            + "] temperatureK=" + environment.temperatureK() + " ideal=" + wheat.biology().idealHeat()
                            + " tolerance=" + wheat.biology().heatTolerance());
                    // Fixture-only holder modifier makes the real 1-level reagent poll count as 3;
                    // this is deliberately not an ordinary-dose or direct-genetic-edit claim.
                    tray.commitPlant(wheatId, wheat, level.getGameTime());
                    plant.mutationModifier(3f);
                    tray.commitMutation();
                    var mutagen = ModReagents.createKey("unstablemutagen");
                    var oldSoil = tray.soilSolution();
                    var soil = new ReagentAttachment(oldSoil.toComponent());
                    require(soil.admitUnits(mutagen, 1000, tray.resolvedTrayProfile().soilCapacityCents()) == 1000,
                            "fixture admits 10 units of resolved unstable mutagen");
                    require(tray.tryReplaceSoil(tray.placementIdentity(), plant, plant.revision(), oldSoil, soil),
                            "authenticated tray soil replacement accepts arranged reagent dose");
                    var initialGenetics = plant.genetics();
                    long initialCycle = plant.lastCycle();
                    int cycleDelay = tray.resolvedTrayProfile().cycleDelayTicks();
                    require(cycleDelay > 0, "resolved tray has positive cycle delay");
                    awaitFirstCycle(helper, tray, pos, level, mutagen, initialGenetics, initialCycle, cycleDelay, 0, false, finished);
                } catch (Throwable failure) { finish(helper, finished, "fixture setup", failure); }
        });
    }

    private static void awaitFirstCycle(GameTestHelper helper, HydroponicsTrayBlockEntity tray, BlockPos pos,
            ServerLevel level, net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> mutagen,
            GeneticValue initialGenetics, long initialCycle, int cycleDelay, int elapsed, boolean sawPositiveLevel, AtomicBoolean finished) {
        if (finished.get()) return;
        BoundedGameTestWait.safeDelay(helper, 20, () -> {
            if (finished.get()) return;
            try {
                var plant = tray.plantState();
                var gas = AtmosphereService.INSTANCE.sample(level, pos.above()).orElse(null);
                require(gas != null, "ordinary plant poll has known atmosphere exposure at " + pos.above()
                        + " tick=" + level.getGameTime() + " cycle=" + plant.lastCycle()
                        + " autoHarvest=" + tray.autoHarvestOutcome());
                boolean observedPositive = sawPositiveLevel || plant.mutationLevel() > 0;
                long remaining = tray.soilSolution().snapshotUnits().getOrDefault(mutagen, 0L);
                if (plant.lastCycle() > initialCycle) {
                    require(observedPositive, "real reagent polling raised mutation level before the first cycle");
                    require(elapsed + 20 >= cycleDelay, "first observed cycle respects resolved ordinary cycle timing");
                    require(plant.genetics().profile().lifecycle().viable() == false,
                            "first ordinary cycle applies the saturated Unviable mutation from prior mutagen polls");
                    require(!plant.genetics().equals(initialGenetics), "cycle changes live genetics from resolved wheat snapshot");
                    require(plant.mutationLevel() == 0f, "ordinary mutation cycle consumes accumulated mutation level");
                    require(remaining <= 500, "at least five real 100-cent mutagen polls were consumed; remaining=" + remaining);
                    finish(helper, finished, "first ordinary cycle", null);
                    return;
                }
                require(remaining < 1000 || plant.mutationLevel() == 0,
                        "ordinary tray ticks have begun reagent polling before cycle; remaining=" + remaining);
                if (elapsed >= 880) throw new AssertionError("first tray cycle not observed within bounded fixture; age="
                        + plant.age() + " cycle=" + plant.lastCycle() + " initial=" + initialCycle + " level="
                        + plant.mutationLevel() + " mutagenRemaining=" + remaining);
                awaitFirstCycle(helper, tray, pos, level, mutagen, initialGenetics, initialCycle, cycleDelay, elapsed + 20,
                        observedPositive, finished);
            } catch (Throwable failure) { finish(helper, finished, "ordinary tray cycle", failure); }
        });
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void finish(GameTestHelper helper, AtomicBoolean finished, String stage, Throwable failure) {
        if (!finished.compareAndSet(false, true)) return;
        if (failure == null) helper.succeed();
        else {
            failure.addSuppressed(new AssertionError("native plant stat mutation failure stage: " + stage));
            helper.testInfo.fail(failure);
        }
    }
}
