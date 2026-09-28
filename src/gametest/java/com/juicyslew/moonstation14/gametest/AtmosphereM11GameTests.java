package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereM11GameTests {
    private static final double TEMPERATURE_KELVIN = 293.15;
    private static final double CELL_MOLES = GasMixture.breathableAir().totalMoles();

    private AtmosphereM11GameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_m11_equal_pressure", timeoutTicks = 120)
    public static void equalPressureAdjacentRoomsExchangeSpeciesThroughNewOpening(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPartitionedRooms(helper);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        List<BlockPos> leftCells = roomCells(helper, 1);
        List<BlockPos> right = roomCells(helper, 4);
        classifyUntilSample(service, level, helper.absolutePos(new BlockPos(1, 1, 1)), 40);
        classifyUntilSample(service, level, right.get(0), 40);
        List<BlockPos> cells = new ArrayList<>(leftCells);
        cells.addAll(right);
        fillCells(service, level, leftCells, GasType.OXYGEN, CELL_MOLES);
        fillCells(service, level, right, GasType.NITROGEN, CELL_MOLES);
        double oxygenBeforeBreach = totalSpecies(service, level, cells, List.of(), GasType.OXYGEN);
        double nitrogenBeforeBreach = totalSpecies(service, level, cells, List.of(), GasType.NITROGEN);
        double energyBeforeBreach = 0.0;
        for (BlockPos pos : cells) energyBeforeBreach += sample(service, level, pos).thermalEnergy();

        BlockPos openingLocal = new BlockPos(3, 1, 1);
        BlockPos opening = helper.absolutePos(openingLocal);
        helper.setBlock(openingLocal, Blocks.AIR);
        service.topologyChanged(level, opening);
        classifyUntilSample(service, level, opening, 40);
        require(isFiniteClaimed(level, opening), "new opening must receive a finite ownership claim");
        double openingEnergyBeforeInjection = sample(service, level, opening).thermalEnergy();
        require(service.addGas(level, opening, GasType.OXYGEN, CELL_MOLES * 0.5, TEMPERATURE_KELVIN)
                        && service.addGas(level, opening, GasType.NITROGEN, CELL_MOLES * 0.5, TEMPERATURE_KELVIN),
                "balanced gas injection into the partition opening must succeed");

        double oxygenBefore = oxygenBeforeBreach + CELL_MOLES * 0.5;
        double nitrogenBefore = nitrogenBeforeBreach + CELL_MOLES * 0.5;
        double energyBefore = energyBeforeBreach + sample(service, level, opening).thermalEnergy()
                - openingEnergyBeforeInjection;
        double initialPressure = CELL_MOLES * 8.31446261815324 * TEMPERATURE_KELVIN / 1000.0;
        BlockPos nearLeft = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos nearRight = helper.absolutePos(new BlockPos(4, 1, 1));
        double leftNitrogenBefore = sample(service, level, nearLeft).moles(GasType.NITROGEN);
        double rightOxygenBefore = sample(service, level, nearRight).moles(GasType.OXYGEN);

        tickPasses(service, level, 40);

        GasMixture left = sample(service, level, nearLeft);
        GasMixture rightMixture = sample(service, level, nearRight);
        require(left.moles(GasType.NITROGEN) > leftNitrogenBefore + CELL_MOLES * 0.01,
                "nitrogen must diffuse from the right room into the left through the open wall");
        require(rightMixture.moles(GasType.OXYGEN) > rightOxygenBefore + CELL_MOLES * 0.01,
                "oxygen must diffuse from the left room into the right through the open wall");
        require(left.moles(GasType.NITROGEN) < CELL_MOLES * 0.9
                        && rightMixture.moles(GasType.OXYGEN) < CELL_MOLES * 0.9,
                "diffusion must remain gradual rather than instantly homogenizing each room");
        require(Math.abs(speciesMoles(service, level, cells, opening, GasType.OXYGEN) - oxygenBefore) < 1.0e-5,
                "closed communicating rooms must conserve oxygen");
        require(Math.abs(speciesMoles(service, level, cells, opening, GasType.NITROGEN) - nitrogenBefore) < 1.0e-5,
                "closed communicating rooms must conserve nitrogen");
        require(Math.abs(thermalEnergy(service, level, cells, opening) - energyBefore) < 1.0e-3,
                "closed communicating rooms must conserve thermal energy");
        for (BlockPos pos : cells) {
            double pressure = sample(service, level, pos).pressureKpa(1.0);
            require(pressure < initialPressure * 1.75,
                    "equal-pressure species diffusion must not create a significant pressure spike");
        }
        require(sample(service, level, helper.absolutePos(new BlockPos(1, 1, 1))).totalMoles() > 0.0,
                "sealed room must retain gas instead of leaking to sky/exterior");
        helper.succeed();
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_m11_pressure_transfer", timeoutTicks = 120)
    public static void openingTransfersGasDirectionallyFromHighToLowPressureRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPartitionedRooms(helper);
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        List<BlockPos> high = roomCells(helper, 1);
        List<BlockPos> low = roomCells(helper, 4);
        classifyUntilSample(service, level, high.get(0), 40);
        classifyUntilSample(service, level, low.get(0), 40);
        fillCells(service, level, high, GasType.OXYGEN, CELL_MOLES * 0.5);
        fillCells(service, level, high, GasType.NITROGEN, CELL_MOLES * 0.5);
        fillCells(service, level, low, GasType.NITROGEN, CELL_MOLES * 0.25);
        double highInitial = totalMoles(service, level, high);
        double lowInitial = totalMoles(service, level, low);
        List<BlockPos> roomCells = new ArrayList<>(high);
        roomCells.addAll(low);
        double oxygenBeforeBreach = totalSpecies(service, level, roomCells, List.of(), GasType.OXYGEN);
        double nitrogenBeforeBreach = totalSpecies(service, level, roomCells, List.of(), GasType.NITROGEN);

        BlockPos openingLocal = new BlockPos(3, 1, 1);
        BlockPos opening = helper.absolutePos(openingLocal);
        BlockPos lowNear = helper.absolutePos(new BlockPos(4, 1, 1));
        double lowNearOxygen = sample(service, level, lowNear).moles(GasType.OXYGEN);
        helper.setBlock(openingLocal, Blocks.AIR);
        service.topologyChanged(level, opening);
        classifyUntilSample(service, level, opening, 40);
        require(isFiniteClaimed(level, opening), "pressure-transfer opening must be claimed finite");
        double openingNitrogenBeforeInjection = sample(service, level, opening).moles(GasType.NITROGEN);
        require(service.addGas(level, opening, GasType.NITROGEN, CELL_MOLES * 0.25, TEMPERATURE_KELVIN),
                "opening should accept a sample at the low-room pressure");

        double oxygenInitial = oxygenBeforeBreach;
        double nitrogenInitial = nitrogenBeforeBreach + sample(service, level, opening).moles(GasType.NITROGEN)
                - openingNitrogenBeforeInjection;

        tickPasses(service, level, 40);

        require(totalMoles(service, level, high) < highInitial - CELL_MOLES * 0.05,
                "high-pressure room must lose gas through its opened partition");
        require(totalMoles(service, level, low) > lowInitial + CELL_MOLES * 0.05,
                "low-pressure room must gain gas through its opened partition");
        require(sample(service, level, lowNear).moles(GasType.OXYGEN) > lowNearOxygen + CELL_MOLES * 0.01,
                "oxygen carried from the high-pressure mixture must enter the low-pressure room");
        require(Math.abs(speciesMoles(service, level, high, low, opening, GasType.OXYGEN) - oxygenInitial) < 1.0e-5,
                "sealed pressure equalization must conserve oxygen");
        require(Math.abs(speciesMoles(service, level, high, low, opening, GasType.NITROGEN) - nitrogenInitial) < 1.0e-5,
                "sealed pressure equalization must conserve nitrogen");
        helper.succeed();
    }

    private static void buildPartitionedRooms(GameTestHelper helper) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int x = 0; x <= 6; x++) {
                helper.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(0, y, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(0, y, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(6, y, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(6, y, 2), Blocks.STONE);
            for (int z = 1; z <= 2; z++) helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
        }
    }

    private static List<BlockPos> roomCells(GameTestHelper helper, int xStart) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = xStart; x < xStart + 2; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 2; z++) cells.add(helper.absolutePos(new BlockPos(x, y, z)));
            }
        }
        return cells;
    }

    private static void fillCells(AtmosphereService service, ServerLevel level, List<BlockPos> cells,
                                  GasType gas, double moles) {
        for (BlockPos pos : cells) {
            require(service.addGas(level, pos, gas, moles, TEMPERATURE_KELVIN),
                    "classified finite room cell must accept gas injection");
        }
    }

    private static GasMixture classifyUntilSample(AtmosphereService service, ServerLevel level,
                                                   BlockPos pos, int maxPasses) {
        for (int pass = 1; pass <= maxPasses; pass++) {
            service.tick(level, (long) pass * AtmosphereService.TICK_CADENCE);
            var mixture = service.sample(level, pos);
            if (mixture.isPresent()) return mixture.get();
        }
        throw new GameTestAssertException("room ownership did not classify within " + maxPasses + " atmosphere passes");
    }

    private static void tickPasses(AtmosphereService service, ServerLevel level, int passes) {
        for (int pass = 1; pass <= passes; pass++) {
            service.tick(level, (long) (40 + pass) * AtmosphereService.TICK_CADENCE);
        }
    }

    private static GasMixture sample(AtmosphereService service, ServerLevel level, BlockPos pos) {
        return service.sample(level, pos).orElseThrow(() -> new GameTestAssertException("room cell is unclassified: " + pos));
    }

    private static boolean isFiniteClaimed(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = (LevelChunk) level.getChunk(pos);
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        return data != null && data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
    }

    private static double totalMoles(AtmosphereService service, ServerLevel level, List<BlockPos> cells) {
        return cells.stream().mapToDouble(pos -> sample(service, level, pos).totalMoles()).sum();
    }

    private static double speciesMoles(AtmosphereService service, ServerLevel level, List<BlockPos> cells,
                                       BlockPos opening, GasType gas) {
        return totalSpecies(service, level, cells, List.of(opening), gas);
    }

    private static double speciesMoles(AtmosphereService service, ServerLevel level, List<BlockPos> first,
                                       List<BlockPos> second, BlockPos opening, GasType gas) {
        List<BlockPos> all = new ArrayList<>(first);
        all.addAll(second);
        return totalSpecies(service, level, all, List.of(opening), gas);
    }

    private static double totalSpecies(AtmosphereService service, ServerLevel level, List<BlockPos> cells,
                                       List<BlockPos> extra, GasType gas) {
        double total = 0.0;
        for (BlockPos pos : cells) total += sample(service, level, pos).moles(gas);
        for (BlockPos pos : extra) total += sample(service, level, pos).moles(gas);
        return total;
    }

    private static double thermalEnergy(AtmosphereService service, ServerLevel level, List<BlockPos> cells,
                                        BlockPos opening) {
        double total = sample(service, level, opening).thermalEnergy();
        for (BlockPos pos : cells) total += sample(service, level, pos).thermalEnergy();
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
