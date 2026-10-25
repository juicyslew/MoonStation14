package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.AtmosphereRoomGameTests;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-genetics", "botany-gas-mutation", "botany-auto-harvest", "atmosphere"})
public final class PlantGasMetabolismGameTests {
    private static final double EXUDED_CO2_MOLES = 2.0; // 0.2 profile units * potency 10 / divisor 1.
    private static final double EXPECTED_OXYGEN_CONSUMPTION = 0.25;
    private PlantGasMetabolismGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void trustedSetupAcceptsBoundedAuthoredRoomAboveLegacyCellLimit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var cells = new java.util.ArrayList<BlockPos>();
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 14; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++) {
                boolean interior = x >= 2 && x <= 12 && z >= 2 && z <= 13;
                helper.setBlock(new BlockPos(x, y, z), interior ? Blocks.AIR : Blocks.STONE);
                if (interior) cells.add(helper.absolutePos(new BlockPos(x, y, z)));
            }
        }
        require(cells.size() == 264, "authored room exceeds legacy cell cap");
        var mixture = new GasMixture(Map.of(GasType.OXYGEN, 2d), 293.15);
        require(AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level, cells, mixture),
                "bounded connected fully enclosed authored room above 128 cells is initialized");
        require(cells.stream().allMatch(cell -> AtmosphereRoomGameTests.isFiniteClaimed(level, cell)),
                "all proven supplied room cells receive finite claims");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 1000)
    public static void ordinaryTrayCycleExchangesProfileGasesInSealedFiniteChamber(GameTestHelper helper) {
        runOrdinaryCycle(helper, false);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 1000)
    public static void insufficientRequiredGasDoesNotPartiallyDrainInSealedFiniteChamber(GameTestHelper helper) {
        runOrdinaryCycle(helper, true);
    }

    private static void runOrdinaryCycle(GameTestHelper helper, boolean insufficient) {
        ServerLevel level = helper.getLevel();
        AtmosphereRoomGameTests.buildSealedHotspotCell(helper, 2, 2);
        BlockPos trayPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos exposure = trayPos.above();
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState());
        AtmosphereService service = AtmosphereService.INSTANCE;
        BlockPos lower = trayPos, upper = exposure;
        // Tray entity initialization occurs on ordinary BE ticking, not synchronously at placement.
        BoundedGameTestWait.safeDelay(helper, 2, () -> {
            try {
                var tray = (HydroponicsTrayBlockEntity) level.getBlockEntity(trayPos);
                require(tray != null && tray.plantState() != null, "resolved tray initialized after ordinary ticks");
                var initialMixture = new GasMixture(Map.of(GasType.OXYGEN, 20.8d, GasType.NITROGEN, 20.8d), 293.15);
                require(service.initializeEnclosedFiniteMixtureForTrustedSetup(level, java.util.List.of(lower, upper), initialMixture),
                        "trusted setup replaces both chamber cells with the exact authored mixture (not ambient addition)");
                require(AtmosphereRoomGameTests.isFiniteClaimed(level, lower)
                                && AtmosphereRoomGameTests.isFiniteClaimed(level, upper),
                        "authoritative setup claims both finite chamber cells");
                var base = ModSeedProfiles.serverLookup(ModSeedProfiles.seedId("wheat"));
                var life = base.lifecycle();
                var lifecycle = new SeedProfile.Lifecycle(life.growthStages(), life.lifespan(), life.maturation(),
                        life.production(), life.yield(), 10, life.harvest(), true, false);
                var profile = new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(), lifecycle,
                        base.chemicals(), Map.of(GasType.OXYGEN, insufficient ? 100f : (float) EXPECTED_OXYGEN_CONSUMPTION),
                        Map.of(GasType.CARBON_DIOXIDE, 0.2f), base.mutationTargets(), base.scream(), base.sourceName());
                for (BlockPos cell : new BlockPos[]{lower, upper}) {
                    var mixture = service.sample(level, cell).orElseThrow();
                    require(Math.abs(mixture.moles(GasType.OXYGEN) - 20.8) < 1e-4
                                    && Math.abs(mixture.moles(GasType.NITROGEN) - 20.8) < 1e-4,
                            "trusted setup replaces each of exactly two cells with the authored O2/N2 amounts: " + mixture.gasMoles());
                    require(mixture.pressureKpa(1.0) >= 81 && mixture.pressureKpa(1.0) <= 121
                                    && Math.abs(mixture.temperatureKelvin() - 293.15) < 0.01,
                            "chamber bounds: cell=" + cell + " gases=" + mixture.gasMoles()
                                    + " pressureKPa=" + mixture.pressureKpa(1.0) + " temperatureK="
                                    + mixture.temperatureKelvin() + " volumeM3=1.0 gameTime=" + level.getGameTime());
                }
                tray.commitPlant(new GeneticValue(ModSeedProfiles.seedId("wheat"), profile), level.getGameTime());
                double oxygenBefore = total(service, level, lower, upper, GasType.OXYGEN);
                double nitrogenBefore = total(service, level, lower, upper, GasType.NITROGEN);
                double co2Before = total(service, level, lower, upper, GasType.CARBON_DIOXIDE);
                long cycle = tray.plantState().lastCycle();
                AtomicBoolean finished = new AtomicBoolean();
                waitCycle(helper, tray, service, level, lower, upper, cycle, oxygenBefore, nitrogenBefore,
                        co2Before, insufficient, 0, finished);
            } catch (Throwable failure) { helper.testInfo.fail(failure); }
        });
    }

    private static void waitCycle(GameTestHelper helper, HydroponicsTrayBlockEntity tray, AtmosphereService service,
            ServerLevel level, BlockPos lower, BlockPos upper, long initialCycle, double oxygen, double nitrogen,
            double co2, boolean insufficient, int elapsed, AtomicBoolean finished) {
        if (finished.get()) return;
        BoundedGameTestWait.safeDelay(helper, 20, () -> {
            try {
                if (tray.plantState().lastCycle() > initialCycle) {
                    require(AtmosphereRoomGameTests.isFiniteClaimed(level, lower)
                                    && AtmosphereRoomGameTests.isFiniteClaimed(level, upper), "both chamber cells remain finite");
                    var report = tray.gasExchangeReport();
                    require(report.attempted() && report.failedExudationGases().isEmpty(),
                            "ordinary gas exchange attempted and exudation succeeded: " + report);
                    require(report.missingGasCount() == (insufficient ? 1 : 0)
                                    && report.unmetRequiredGases().equals(insufficient ? java.util.List.of(GasType.OXYGEN) : java.util.List.of()),
                            "gas report identifies precisely the required oxygen outcome: " + report);
                    double expectedOxygen = oxygen - (insufficient ? 0 : EXPECTED_OXYGEN_CONSUMPTION);
                    require(Math.abs(total(service, level, lower, upper, GasType.OXYGEN) - expectedOxygen) < 1e-4,
                            insufficient ? "insufficient cycle does not partially drain oxygen" : "ordinary cycle consumes exact oxygen");
                    require(Math.abs(total(service, level, lower, upper, GasType.CARBON_DIOXIDE)
                                    - (co2 + EXUDED_CO2_MOLES)) < 1e-4,
                            "ordinary cycle exudes exact CO2 amount across both cells");
                    require(Math.abs(total(service, level, lower, upper, GasType.NITROGEN) - nitrogen) < 1e-4,
                            "unrelated nitrogen conserved");
                    for (BlockPos cell : new BlockPos[]{lower, upper})
                        require(Math.abs(service.sample(level, cell).orElseThrow().temperatureKelvin() - 293.15) < 0.01,
                                "each chamber cell remains at exchange temperature");
                    if (finished.compareAndSet(false, true)) helper.succeed();
                    return;
                }
                if (elapsed >= 900) throw new AssertionError("ordinary tray cycle timeout; lastCycle=" + tray.plantState().lastCycle());
                waitCycle(helper, tray, service, level, lower, upper, initialCycle, oxygen, nitrogen, co2, insufficient,
                        elapsed + 20, finished);
            } catch (Throwable failure) {
                if (finished.compareAndSet(false, true)) helper.testInfo.fail(failure);
            }
        });
    }

    private static double total(AtmosphereService service, ServerLevel level, BlockPos a, BlockPos b, GasType type) {
        return service.sample(level, a).orElseThrow().moles(type) + service.sample(level, b).orElseThrow().moles(type);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
