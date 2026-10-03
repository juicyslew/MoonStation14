package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereLoopRoomGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtmosphereLoopRoomGameTests.class);
    private static final int MIN = 2;
    private static final int MAX = 13;
    private static final double TEMPERATURE = 293.15;
    private static final int CLASSIFICATION_LIMIT = 80;

    private AtmosphereLoopRoomGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_circular_hallway", timeoutTicks = 260)
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
        int ownershipSteps = 0;
        while (ownershipSteps < CLASSIFICATION_LIMIT && !allClaimed(level, worldCells)) {
            ownershipSteps++;
            service.tick(level, (long) ownershipSteps * AtmosphereService.TICK_CADENCE);
            for (BlockPos cell : worldCells) service.sample(level, cell);
        }
        require(allClaimed(level, worldCells), "all " + worldCells.size()
                + " corridor cells need finite claims; ownership due steps=" + ownershipSteps);

        for (BlockPos cell : worldCells)
            require(service.addBreathableAir(level, cell, 41.6, TEMPERATURE), "seed nominal air at " + cell);
        GasMixture initialNear = service.sample(level, near).orElseThrow();
        require(Math.abs(initialNear.pressureKpa(1.0) - 101.5) < 0.2, "near cell must begin at nominal pressure");
        double initialMoles = sum(service, level, worldCells, null);
        double initialEnergy = sumEnergy(service, level, worldCells);

        double injected = 0.0;
        int producerSteps = 0;
        while (producerSteps < 80 && service.sample(level, near).orElseThrow().pressureKpa(1.0) < 202.65) {
            producerSteps++;
            GasMixture source = service.sample(level, near).orElseThrow();
            double dose = 2.0;
            if (source.withGasDelta(GasType.TRITIUM, dose, TEMPERATURE).pressureKpa(1.0) > 202.65) {
                double low = 0.0, high = dose;
                for (int i = 0; i < 80; i++) {
                    double middle = (low + high) * 0.5;
                    if (source.withGasDelta(GasType.TRITIUM, middle, TEMPERATURE).pressureKpa(1.0) <= 202.65) low = middle;
                    else high = middle;
                }
                dose = low;
            }
            if (dose > 0 && service.addGas(level, near, GasType.TRITIUM, dose, TEMPERATURE)) injected += dose;
            service.tick(level, (long) (ownershipSteps + producerSteps) * AtmosphereService.TICK_CADENCE);
        }
        require(producerSteps >= 30, "producer reached cap before 30 steps: " + producerSteps);
        GasMixture producerEnd = service.sample(level, near).orElseThrow();
        String producerSnapshot = "producer end (finite): source=" + describe(service, level, near)
                + "; source-route-neighbor=" + describe(service, level, nearRouteNeighbor)
                + "; far=" + describe(service, level, far)
                + "; far-route-neighbor=" + describe(service, level, farRouteNeighbor)
                + "; injected=" + injected + " mol; source pressure=" + producerEnd.pressureKpa(1.0) + " kPa";
        double[] nearPressure = new double[81], farPressure = new double[81];
        double[] nearMoles = new double[81], farMoles = new double[81];
        double[] nearTritium = new double[81], farTritium = new double[81];
        double[] nearTemperature = new double[81], farTemperature = new double[81];
        for (int step = 1; step <= 80; step++) {
            service.tick(level, (long) (ownershipSteps + producerSteps + step) * AtmosphereService.TICK_CADENCE);
            if (step == 10 || step == 30 || step == 80) {
                GasMixture n = service.sample(level, near).orElseThrow(), f = service.sample(level, far).orElseThrow();
                nearPressure[step] = n.pressureKpa(1.0); farPressure[step] = f.pressureKpa(1.0);
                nearMoles[step] = n.totalMoles(); farMoles[step] = f.totalMoles();
                nearTritium[step] = n.moles(GasType.TRITIUM); farTritium[step] = f.moles(GasType.TRITIUM);
                nearTemperature[step] = n.temperatureKelvin(); farTemperature[step] = f.temperatureKelvin();
            }
        }
        String snapshots = "1s near/far=" + nearPressure[10] + "kPa/" + nearMoles[10] + "mol/"
                + nearTemperature[10] + "K/" + nearTritium[10] + "Trit; " + farPressure[10] + "kPa/"
                + farMoles[10] + "mol/" + farTemperature[10] + "K/" + farTritium[10] + "Trit; 3s near/far="
                + nearPressure[30] + "kPa/" + nearMoles[30] + "mol/" + nearTemperature[30] + "K/"
                + nearTritium[30] + "Trit; " + farPressure[30] + "kPa/" + farMoles[30] + "mol/"
                + farTemperature[30] + "K/" + farTritium[30] + "Trit; 8s near/far="
                + nearPressure[80] + "kPa/" + nearMoles[80] + "mol/" + nearTemperature[80] + "K/"
                + nearTritium[80] + "Trit; " + farPressure[80] + "kPa/" + farMoles[80] + "mol/"
                + farTemperature[80] + "K/" + farTritium[80] + "Trit; producer steps=" + producerSteps;
        LOGGER.info("Atmosphere loop producer checkpoint: {}; post-shutoff snapshots: {}", producerSnapshot, snapshots);
        require(nearPressure[80] > 101.5 && farPressure[80] > 101.5 && farTritium[80] > 0.0,
                "sealed loop response/tritium routing missing; " + snapshots);
        // Pressure equalization conserves total cell inventory; do not mistake species routing for bulk-mole drift.
        require(Math.abs(nearMoles[80] - farMoles[80]) <= 1.0e-6,
                "post-shutoff near/far total-mole parity failed; " + snapshots);
        for (GasType gas : GasType.values()) {
            double expected = gas == GasType.TRITIUM ? injected : gas == GasType.OXYGEN ? initialMoles * 0.21
                    : gas == GasType.NITROGEN ? initialMoles * 0.79 : 0.0;
            require(Math.abs(sum(service, level, worldCells, gas) - expected) < 1.0e-5,
                    "sealed-loop species conservation failed for " + gas + "; " + snapshots);
        }
        double expectedEnergy = initialEnergy + injected * GasType.TRITIUM.molarHeatCapacity() * TEMPERATURE;
        require(Math.abs(sumEnergy(service, level, worldCells) - expectedEnergy) < Math.max(1.0e-5, expectedEnergy * 1e-9),
                "sealed-loop thermal energy conservation failed; " + snapshots);
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
