package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereRuntimeTelemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere"})
public final class AtmosphereLoopRoomGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtmosphereLoopRoomGameTests.class);
    private static final int MIN = 2;
    private static final int MAX = 13;
    private static final double TEMPERATURE = 293.15;
    private static final int CLASSIFICATION_LIMIT = 96;

    private AtmosphereLoopRoomGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_circular_hallway", timeoutTicks = 260, templateNamespace = "moonstation14")
    public static void nominalCircularHallwayEqualizesAroundSolidCenter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        require(!AtmosphereService.INSTANCE.isEnabled(), "global atmosphere service must remain disabled");
        List<BlockPos> cells = buildLoop(helper);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        List<BlockPos> worldCells = cells.stream().map(helper::absolutePos).toList();
        BlockPos near = helper.absolutePos(new BlockPos(MIN + 1, 1, (MIN + MAX) / 2));
        BlockPos far = helper.absolutePos(new BlockPos(MAX - 1, 1, (MIN + MAX) / 2));
        BlockPos nearRouteNeighbor = helper.absolutePos(new BlockPos(MIN + 1, 1, (MIN + MAX) / 2 - 1));
        BlockPos farRouteNeighbor = helper.absolutePos(new BlockPos(MAX - 1, 1, (MIN + MAX) / 2 - 1));

        // Fixture construction can load chunks; after this point service ticks must not.
        for (BlockPos cell : worldCells) level.getChunk(cell);
        service.activateCellAndNeighbors(level, worldCells.get(0));
        int ownershipSteps = 0;
        while (ownershipSteps < CLASSIFICATION_LIMIT && !allClaimed(level, worldCells)) {
            ownershipSteps++;
            service.tick(level, ownershipSteps);
            for (BlockPos cell : worldCells) service.sample(level, cell);
        }
        require(allClaimed(level, worldCells), "all " + worldCells.size()
                + " corridor cells need finite claims; callback attempts=" + ownershipSteps + ", timing="
                + service.diagnosticTiming(level));

        for (BlockPos cell : worldCells)
            require(service.addBreathableAir(level, cell, 41.6, TEMPERATURE), "seed nominal air at " + cell);
        GasMixture initialNear = service.sample(level, near).orElseThrow();
        require(Math.abs(initialNear.pressureKpa(1.0) - 101.5) < 0.2, "near cell must begin at nominal pressure");
        double initialMoles = sum(service, level, worldCells, null);
        double initialEnergy = sumEnergy(service, level, worldCells);
        double initialMaxCellEnergy = maxCellEnergy(service, level, worldCells);
        double[] initialMaxCellGas = maxCellMoles(service, level, worldCells);
        AtmosphereRuntimeTelemetry.Snapshot auditStart = service.diagnosticRuntime(level);

        double injected = 0.0;
        double realizedInjectedEnergy = 0.0;
        double realizedInjectedTritium = 0.0;
        double producerEnergyUlp = 0.0;
        double producerTritiumUlp = 0.0;
        int committedProducerAdds = 0;
        int producerSteps = 0;
        while (producerSteps < 160 && service.sample(level, near).orElseThrow().pressureKpa(1.0) < 202.65) {
            producerSteps++;
            GasMixture source = service.sample(level, near).orElseThrow();
            double dose = 1.0; // 20 mol/s at 20 TPS.
            if (source.withGasDelta(GasType.TRITIUM, dose, TEMPERATURE).pressureKpa(1.0) > 202.65) {
                double low = 0.0, high = dose;
                for (int i = 0; i < 80; i++) {
                    double middle = (low + high) * 0.5;
                    if (source.withGasDelta(GasType.TRITIUM, middle, TEMPERATURE).pressureKpa(1.0) <= 202.65) low = middle;
                    else high = middle;
                }
                dose = low;
            }
            if (dose > 0) {
                GasMixture beforeAdd = service.sample(level, near).orElseThrow();
                if (service.addGas(level, near, GasType.TRITIUM, dose, TEMPERATURE)) {
                    GasMixture afterAdd = service.sample(level, near).orElseThrow();
                    double energyDelta = afterAdd.thermalEnergy() - beforeAdd.thermalEnergy();
                    double tritiumDelta = afterAdd.moles(GasType.TRITIUM) - beforeAdd.moles(GasType.TRITIUM);
                    injected += dose;
                    realizedInjectedEnergy += energyDelta;
                    realizedInjectedTritium += tritiumDelta;
                    producerEnergyUlp += Math.ulp((float) beforeAdd.thermalEnergy())
                            + Math.ulp((float) afterAdd.thermalEnergy());
                    producerTritiumUlp += Math.ulp((float) beforeAdd.moles(GasType.TRITIUM))
                            + Math.ulp((float) afterAdd.moles(GasType.TRITIUM));
                    committedProducerAdds++;
                }
            }
            service.tick(level, ownershipSteps + producerSteps);
        }
        require(producerSteps >= 30, "producer reached cap before 30 steps: " + producerSteps);
        double energyAfterProducer = sumEnergy(service, level, worldCells);
        double producerMaxCellEnergy = maxCellEnergy(service, level, worldCells);
        double[] producerMaxCellGas = maxCellMoles(service, level, worldCells);
        GasMixture producerEnd = service.sample(level, near).orElseThrow();
        String producerSnapshot = "producer end (finite): source=" + describe(service, level, near)
                + "; source-route-neighbor=" + describe(service, level, nearRouteNeighbor)
                + "; far=" + describe(service, level, far)
                + "; far-route-neighbor=" + describe(service, level, farRouteNeighbor)
                + "; injected=" + injected + " mol; source pressure=" + producerEnd.pressureKpa(1.0) + " kPa";
        double[] nearPressure = new double[161], farPressure = new double[161];
        double[] nearMoles = new double[161], farMoles = new double[161];
        double[] nearTritium = new double[161], farTritium = new double[161];
        double[] nearTemperature = new double[161], farTemperature = new double[161];
        for (int step = 1; step <= 160; step++) {
            service.tick(level, ownershipSteps + producerSteps + step);
            if (step == 12 || step == 60 || step == 160) {
                GasMixture n = service.sample(level, near).orElseThrow(), f = service.sample(level, far).orElseThrow();
                nearPressure[step] = n.pressureKpa(1.0); farPressure[step] = f.pressureKpa(1.0);
                nearMoles[step] = n.totalMoles(); farMoles[step] = f.totalMoles();
                nearTritium[step] = n.moles(GasType.TRITIUM); farTritium[step] = f.moles(GasType.TRITIUM);
                nearTemperature[step] = n.temperatureKelvin(); farTemperature[step] = f.temperatureKelvin();
            }
        }
        String snapshots = "producer checkpoints 0.6s near/far=" + nearPressure[12] + "kPa/" + nearMoles[12] + "mol/"
                + nearTemperature[12] + "K/" + nearTritium[12] + "Trit; " + farPressure[12] + "kPa/"
                + farMoles[12] + "mol/" + farTemperature[12] + "K/" + farTritium[12] + "Trit; 3s near/far="
                + nearPressure[60] + "kPa/" + nearMoles[60] + "mol/" + nearTemperature[60] + "K/"
                + nearTritium[60] + "Trit; " + farPressure[60] + "kPa/" + farMoles[60] + "mol/"
                + farTemperature[60] + "K/" + farTritium[60] + "Trit; 8s near/far="
                + nearPressure[160] + "kPa/" + nearMoles[160] + "mol/" + nearTemperature[160] + "K/"
                + nearTritium[160] + "Trit; " + farPressure[160] + "kPa/" + farMoles[160] + "mol/"
                + farTemperature[160] + "K/" + farTritium[160] + "Trit; producer callbacks=" + producerSteps;
        LOGGER.info("Atmosphere loop producer checkpoint: {}; post-shutoff snapshots: {}; producer accounting: "
                        + "requested={} mol, realized={} mol, realized energy={} J, add ULP sums E={} T={}, "
                        + "committed adds={}, total energy before/after injected window={} / {} J",
                producerSnapshot, snapshots, injected, realizedInjectedTritium, realizedInjectedEnergy,
                producerEnergyUlp, producerTritiumUlp, committedProducerAdds, initialEnergy, energyAfterProducer);
        require(nearPressure[160] > 101.5 && farPressure[160] > 101.5,
                "sealed loop pressure response missing at source checkpoints; " + snapshots);
        // Pressure equalization conserves total cell inventory; do not mistake species routing for bulk-mole drift.
        double moleUlpBudget = 8 * (Math.ulp((float) nearMoles[160]) + Math.ulp((float) farMoles[160]));
        require(Math.abs(nearMoles[160] - farMoles[160]) <= moleUlpBudget,
                "post-shutoff near/far total-mole parity failed; " + snapshots);
        // This loop's shortest route is 12 face edges. A producer-on window is not a transport
        // deadline: completed LINDA visits are separated by whole atmosphere stages and groups
        // can cool down/reset. Keep the 8 s observation above, then give the finite route a
        // geometry-derived quiet window while no more tritium is being injected.
        int quietCallbacks = 0;
        while (quietCallbacks < 720 && service.sample(level, far).orElseThrow().moles(GasType.TRITIUM) <= 0.0) {
            quietCallbacks++;
            service.tick(level, ownershipSteps + producerSteps + 160L + quietCallbacks);
        }
        farTritium[160] = service.sample(level, far).orElseThrow().moles(GasType.TRITIUM);
        require(farTritium[160] > 0.0,
                "tritium did not reach the 12-face-edge route during " + quietCallbacks
                        + " quiet callbacks after producer shutoff; " + snapshots);
        AtmosphereRuntimeTelemetry.Snapshot auditEnd = service.diagnosticRuntime(level);
        long pressureFaces = auditEnd.pressureFaceTransfers() - auditStart.pressureFaceTransfers();
        long lindaPairs = auditEnd.lindaSharedPairs() - auditStart.lindaSharedPairs();
        long groupedMembers = auditEnd.groupAveragedMembers() - auditStart.groupAveragedMembers();
        long floatOperationCount = pressureFaces + lindaPairs + groupedMembers;
        double finalMaxCellEnergy = maxCellEnergy(service, level, worldCells);
        double[] finalMaxCellGas = maxCellMoles(service, level, worldCells);
        for (GasType gas : GasType.values()) {
            double expected = gas == GasType.TRITIUM ? realizedInjectedTritium : gas == GasType.OXYGEN ? initialMoles * 0.21
                    : gas == GasType.NITROGEN ? initialMoles * 0.79 : 0.0;
            double actual = sum(service, level, worldCells, gas);
            double magnitude = 2 * Math.max(initialMaxCellGas[gas.ordinal()],
                    Math.max(producerMaxCellGas[gas.ordinal()], finalMaxCellGas[gas.ordinal()]));
            double cleanupBound = floatOperationCount * 2 * GasMixture.GAS_MIN_MOLES;
            double operationBound = floatOperationCount * 4 * Math.ulp((float) magnitude);
            if (gas == GasType.TRITIUM) operationBound += producerTritiumUlp;
            double speciesAllowance = operationBound + cleanupBound;
            LOGGER.info("Atmosphere loop species audit: gas={} expected={} actual={} signedResidual={} absResidual={} "
                            + "floatBound={} cleanupBound={} pressureFaces={} lindaPairs={} groupedMembers={}",
                    gas, expected, actual, actual - expected, Math.abs(actual - expected), operationBound,
                    cleanupBound, pressureFaces, lindaPairs, groupedMembers);
            require(Double.isFinite(actual) && Double.isFinite(speciesAllowance)
                            && Math.abs(actual - expected) <= speciesAllowance,
                    "sealed-loop species conservation exceeded operation-derived float-rounding bound for " + gas
                            + "; expected=" + expected + ", actual=" + actual + ", floatBound=" + operationBound
                            + ", cleanupBound=" + cleanupBound + ", pressureFaces=" + pressureFaces
                            + ", lindaPairs=" + lindaPairs + ", groupedMembers=" + groupedMembers
                            + "; " + snapshots);
        }
        double expectedEnergy = initialEnergy + realizedInjectedEnergy;
        double actualEnergy = sumEnergy(service, level, worldCells);
        // A merge operand can be the receiver plus one packet, each no larger than the largest
        // observed cell inventory in the closed domain; use 2x that max to cover the temporary.
        double maxMergeEnergy = 2 * Math.max(initialMaxCellEnergy,
                Math.max(producerMaxCellEnergy, finalMaxCellEnergy));
        double transportEnergyBound = floatOperationCount * 16 * Math.ulp((float) maxMergeEnergy);
        double cleanupEnergyBound = 0.0; // energy is never discarded by the 5e-8 mol cleanup gate
        double energyAllowance = producerEnergyUlp + transportEnergyBound + cleanupEnergyBound;
        LOGGER.info("Atmosphere loop energy audit: before producer={} J, after producer={} J, after quiet={} J, "
                        + "expected after quiet={} J, signed residual={} J, absolute residual={} J, "
                        + "actual operation counts pressureFaces={} lindaPairs={} groupedMembers={} total={}, "
                        + "operation float-rounding bound={} J producer-realized-add ULP bound={} J cleanup bound={} J",
                initialEnergy, energyAfterProducer, actualEnergy, expectedEnergy, actualEnergy - expectedEnergy,
                Math.abs(actualEnergy - expectedEnergy), pressureFaces, lindaPairs, groupedMembers, floatOperationCount,
                transportEnergyBound, producerEnergyUlp, cleanupEnergyBound);
        require(Float.isFinite((float) actualEnergy) && Float.isFinite((float) energyAllowance)
                        && Math.abs(actualEnergy - expectedEnergy) <= energyAllowance,
                "sealed-loop thermal energy conservation exceeded operation-derived float-rounding budget; expected="
                        + expectedEnergy + ", actual=" + actualEnergy + ", producerULP=" + producerEnergyUlp
                        + ", operationBound=" + transportEnergyBound + ", faceOps=" + pressureFaces
                        + ", lindaPairs=" + lindaPairs + ", groupedMembers=" + groupedMembers
                        + "; " + snapshots);
        require(service.boundaryGasLedger(level).isEmpty(), "sealed circular corridor must have no exterior export");
        helper.succeed();
    }

    private static List<BlockPos> buildLoop(GameTestHelper helper) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = MIN; x <= MAX; x++) for (int z = MIN; z <= MAX; z++) {
            boolean corridor = x <= MIN + 1 || x >= MAX - 1 || z <= MIN + 1 || z >= MAX - 1;
            for (int y = 1; y <= 2; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                helper.setBlock(pos, corridor ? Blocks.AIR : Blocks.STONE);
                if (corridor) cells.add(pos);
            }
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        // Close the outer shell; the center is solid and the finite corridor loops around it.
        for (int y = 1; y <= 2; y++) for (int i = MIN - 1; i <= MAX + 1; i++) {
            helper.setBlock(new BlockPos(i, y, MIN - 1), Blocks.STONE);
            helper.setBlock(new BlockPos(i, y, MAX + 1), Blocks.STONE);
            helper.setBlock(new BlockPos(MIN - 1, y, i), Blocks.STONE);
            helper.setBlock(new BlockPos(MAX + 1, y, i), Blocks.STONE);
        }
        return cells;
    }

    private static boolean allClaimed(ServerLevel level, List<BlockPos> cells) {
        for (BlockPos pos : cells) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) return false;
            var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            if (data == null || !data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15)) return false;
        }
        return true;
    }

    private static double sum(AtmosphereService service, ServerLevel level, List<BlockPos> cells, GasType type) {
        double total = 0;
        for (BlockPos pos : cells) {
            GasMixture gas = service.sample(level, pos).orElseThrow();
            total += type == null ? gas.totalMoles() : gas.moles(type);
        }
        return total;
    }

    private static double sumEnergy(AtmosphereService service, ServerLevel level, List<BlockPos> cells) {
        double total = 0;
        for (BlockPos pos : cells) total += service.sample(level, pos).orElseThrow().thermalEnergy();
        return total;
    }

    private static double maxCellEnergy(AtmosphereService service, ServerLevel level, List<BlockPos> cells) {
        double maximum = 0;
        for (BlockPos pos : cells) maximum = Math.max(maximum, service.sample(level, pos).orElseThrow().thermalEnergy());
        return maximum;
    }

    private static double[] maxCellMoles(AtmosphereService service, ServerLevel level, List<BlockPos> cells) {
        double[] maximum = new double[GasType.values().length];
        for (BlockPos pos : cells) {
            GasMixture mixture = service.sample(level, pos).orElseThrow();
            for (GasType gas : GasType.values()) maximum[gas.ordinal()] = Math.max(maximum[gas.ordinal()], mixture.moles(gas));
        }
        return maximum;
    }

    private static String describe(AtmosphereService service, ServerLevel level, BlockPos pos) {
        GasMixture gas = service.sample(level, pos).orElseThrow();
        return pos + "[FINITE,p=" + gas.pressureKpa(1.0) + "kPa,n=" + gas.totalMoles() + "mol,T="
                + gas.temperatureKelvin() + "K,trit=" + gas.moles(GasType.TRITIUM) + "mol]";
    }

    private static String diagnostic(String reason, double nearP, double farP, double farT,
                                     double nearT, int ownershipSteps, int elapsedSteps) {
        return reason + "; near pressure=" + nearP + " kPa, far pressure=" + farP + " kPa, near tritium="
                + nearT + " mol, far tritium=" + farT + " mol, finite claims=" + true
                + ", ownership due steps=" + ownershipSteps + ", elapsed due steps=" + elapsedSteps;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
