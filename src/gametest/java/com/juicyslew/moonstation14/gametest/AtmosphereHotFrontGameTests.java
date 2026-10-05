package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere"})
public final class AtmosphereHotFrontGameTests {
    private AtmosphereHotFrontGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_hot_front_regression", timeoutTicks = 420, templateNamespace = "moonstation14")
    public static void sealedHotFrontMakesThermalProgressWithoutOngoingWrites(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (AtmosphereService.INSTANCE.isEnabled())
            throw new GameTestAssertException("isolated hot-front test requires global atmosphere disabled");

        // 4x3x1 sealed finite room; its source is one corner and the far probe is diagonally opposite.
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        }
        for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(1, 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(6, 1, z), Blocks.STONE);
        }
        for (int x = 2; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.STONE);
        }
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);

        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos source = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos adjacent = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos far = helper.absolutePos(new BlockPos(5, 1, 4));
        int due = 0;
        while (due < 96) {
            boolean classified = true;
            for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++)
                classified &= service.sample(level, helper.absolutePos(new BlockPos(x, 1, z))).isPresent();
            if (classified) break;
            service.tick(level, ++due);
        }
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++) {
            BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
            if (service.sample(level, cell).isEmpty()) throw new GameTestAssertException("finite room classification timed out");
            double moles = x == 2 && z == 2 ? 43.0 : 45.0;
            double temperature = x == 2 && z == 2 ? 200000.0 : 349.0;
            if (!service.addGas(level, cell, GasType.NITROGEN, moles, temperature))
                throw new GameTestAssertException("finite room gas seed rejected at " + cell);
        }
        double initialEnergy = energy(service, level, helper);
        double adjacentInitial = sample(service, level, adjacent).temperatureKelvin();
        double farInitial = sample(service, level, far).temperatureKelvin();
        boolean[] progressed = {false};
        helper.startSequence().thenExecuteFor(360, () -> {
            service.tick(level, level.getGameTime());
            GasMixture near = sample(service, level, adjacent);
            GasMixture distant = sample(service, level, far);
            if (near.temperatureKelvin() > adjacentInitial + 1.0
                    && distant.temperatureKelvin() > farInitial + 1.0) {
                progressed[0] = true;
                helper.succeed();
            }
        }).thenExecute(() -> {
            if (!progressed[0]) throw new GameTestAssertException("hot-front thermal progress missing after 360 real server callbacks; adjacent="
                    + sample(service, level, adjacent).temperatureKelvin() + "K, far="
                    + sample(service, level, far).temperatureKelvin() + "K, timing=" + service.diagnosticTiming(level));
            double finalEnergy = energy(service, level, helper);
            double exported = service.boundaryEnergyLedger(level);
            if (Math.abs(initialEnergy - finalEnergy - exported) > energyUlpBudget(service, level, helper))
                throw new GameTestAssertException("sealed-room thermal energy changed");
            if (Math.abs(service.boundaryGasLedger(level).values().stream().mapToDouble(Double::doubleValue).sum()) > 1e-9)
                throw new GameTestAssertException("sealed hot-front room unexpectedly exported gas");
        });
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_hot_front_regression", timeoutTicks = 420, templateNamespace = "moonstation14")
    public static void ongoingMoleChangesDoNotStarveThermalSharing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (AtmosphereService.INSTANCE.isEnabled())
            throw new GameTestAssertException("isolated hot-front test requires global atmosphere disabled");
        buildRoom(helper);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos source = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos adjacent = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos far = helper.absolutePos(new BlockPos(5, 1, 4));
        int due = classify(service, level, helper);
        seed(service, level, helper);
        double initialEnergy = energy(service, level, helper);
        double initialN2 = nitrogen(service, level, helper);
        double adjacentInitial = sample(service, level, adjacent).temperatureKelvin();
        double farInitial = sample(service, level, far).temperatureKelvin();
        double[] withdrawnEnergy = {0.0};
        double[] withdrawnMoles = {0.0};
        helper.startSequence().thenExecuteFor(360, () -> {
            GasMixture before = sample(service, level, source);
            service.exchangeBreath(level, source, 0.1, GasMixture.vacuum()).orElseThrow(
                    () -> new GameTestAssertException("controlled external sink exchange failed"));
            GasMixture after = sample(service, level, source);
            withdrawnEnergy[0] += before.thermalEnergy() - after.thermalEnergy();
            withdrawnMoles[0] += before.totalMoles() - after.totalMoles();
            service.tick(level, level.getGameTime());
            long i = helper.getTick();
            if (i == 10 || i == 20 || i == 40 || i == 100 || i == 200 || i == 360)
                MoonStation14.LOGGER.info("Hot-front pulse diagnostic due=" + i + ", source="
                        + describe(service, level, source) + ", adjacent=" + describe(service, level, adjacent)
                        + ", far=" + describe(service, level, far) + ", timing=" + service.diagnosticTiming(level));
        }).thenExecute(() -> {
        GasMixture near = sample(service, level, adjacent);
        GasMixture distant = sample(service, level, far);
        if (!(near.temperatureKelvin() > adjacentInitial + 1.0)
                || !(distant.temperatureKelvin() > farInitial + 1.0))
            throw new GameTestAssertException("ongoing 0.1 mol source sink starved hot-front thermal progress; adjacent="
                    + near.temperatureKelvin() + "K (start=" + adjacentInitial + "K), far=" + distant.temperatureKelvin()
                    + "K (start=" + farInitial + "K), source=" + describe(service, level, source)
                    + ", timing=" + service.diagnosticTiming(level));
        assertClose(initialN2 - withdrawnMoles[0], nitrogen(service, level, helper),
                8 * nitrogenUlpBudget(service, level, helper),
                "nitrogen inventory after measured external sink");
        double finalEnergy = energy(service, level, helper);
        double energyResidual = initialEnergy - withdrawnEnergy[0] - finalEnergy;
        if (!Double.isFinite(finalEnergy) || finalEnergy <= 0.0)
            throw new GameTestAssertException("sealed hot-front energy inventory must remain finite and positive; final="
                    + finalEnergy);
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++) {
            double temperature = sample(service, level, helper.absolutePos(new BlockPos(x, 1, z)))
                    .temperatureKelvin();
            if (!Double.isFinite(temperature) || temperature < 2.7 || temperature > 262144.0)
                throw new GameTestAssertException("sealed hot-front produced out-of-range temperature at " + x + ",1," + z
                        + ": " + temperature + "K");
        }
        MoonStation14.LOGGER.info("Hot-front measured-sink inventory: initial={} J, measured withdrawal={} J, "
                        + "final={} J, source-operation residual={} J",
                initialEnergy, withdrawnEnergy[0], finalEnergy, energyResidual);
        if (Math.abs(service.boundaryEnergyLedger(level)) > 1e-6
                || !service.boundaryGasLedger(level).isEmpty())
            throw new GameTestAssertException("sealed pulse room unexpectedly exported to boundary");
        helper.succeed();
        });
    }

    private static void buildRoom(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        }
        for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(1, 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(6, 1, z), Blocks.STONE);
        }
        for (int x = 2; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.STONE);
        }
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
    }

    private static int classify(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        BlockPos seed = helper.absolutePos(new BlockPos(2, 1, 2));
        service.activateCellAndNeighbors(level, seed);
        int due = 0;
        while (due < 96) {
            boolean classified = true;
            for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++)
                classified &= service.diagnosticCell(level, helper.absolutePos(new BlockPos(x, 1, z)))
                        .status().equals("finite");
            if (classified) return due;
            service.tick(level, ++due);
        }
        throw new GameTestAssertException("finite room classification timed out");
    }

    private static void seed(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++) {
            BlockPos cell = helper.absolutePos(new BlockPos(x, 1, z));
            double moles = x == 2 && z == 2 ? 43.0 : 45.0;
            double temperature = x == 2 && z == 2 ? 200000.0 : 349.0;
            if (!service.addGas(level, cell, GasType.NITROGEN, moles, temperature))
                throw new GameTestAssertException("finite room gas seed rejected at " + cell);
        }
    }

    private static double nitrogen(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        double total = 0;
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++)
            total += sample(service, level, helper.absolutePos(new BlockPos(x, 1, z))).moles(GasType.NITROGEN);
        return total;
    }

    private static double nitrogenUlpBudget(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        double budget = 0;
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++)
            budget += Math.ulp((float) sample(service, level, helper.absolutePos(new BlockPos(x, 1, z)))
                    .moles(GasType.NITROGEN));
        return budget;
    }

    private static String describe(AtmosphereService service, ServerLevel level, BlockPos pos) {
        GasMixture gas = sample(service, level, pos);
        return "T=" + gas.temperatureKelvin() + "K,n=" + gas.totalMoles() + "mol,E=" + gas.thermalEnergy() + "J";
    }

    private static void assertClose(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance)
            throw new GameTestAssertException(label + " mismatch: expected=" + expected + ", actual=" + actual);
    }

    private static GasMixture sample(AtmosphereService service, ServerLevel level, BlockPos pos) {
        return service.sample(level, pos).orElseThrow(() -> new GameTestAssertException("room cell unclassified: " + pos));
    }

    private static double energy(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        double result = 0;
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++)
            result += sample(service, level, helper.absolutePos(new BlockPos(x, 1, z))).thermalEnergy();
        return result;
    }

    private static double energyUlpBudget(AtmosphereService service, ServerLevel level, GameTestHelper helper) {
        double budget = 0;
        for (int x = 2; x <= 5; x++) for (int z = 2; z <= 4; z++)
            budget += Math.ulp((float) sample(service, level, helper.absolutePos(new BlockPos(x, 1, z)))
                    .thermalEnergy());
        return 8 * budget;
    }
}
