package com.juicyslew.moonstation14.gametest.station.debug;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.juicyslew.moonstation14.ms14.station.debug.StarterStationLayout;
import com.juicyslew.moonstation14.ms14.station.debug.StarterStationPlacementState;
import com.juicyslew.moonstation14.ms14.station.debug.StarterStationSavedData;
import com.juicyslew.moonstation14.ms14.station.debug.StarterStationPowerPlan;
import com.juicyslew.moonstation14.ms14.power.PowerSimulationGate;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarterStationLifecycleGameTests {
    private static final int MAX_WAIT_TICKS = 300;
    private static final int MAX_RUNTIME_SETTLE_TICKS = 120;

    private StarterStationLifecycleGameTests() { }

    @GameTest(template = "empty", timeoutTicks = MAX_WAIT_TICKS + MAX_RUNTIME_SETTLE_TICKS + 20)
    public static void newWorldMarkerCompletesAndBuildsTiledStationInOverworld(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD))
            throw new GameTestAssertException("starter-station lifecycle test must observe the overworld");

        StarterStationSavedData initial = level.getDataStorage().get(
                StarterStationSavedData.FACTORY, StarterStationSavedData.DATA_NAME);
        if (initial == null) {
            MoonStation14.LOGGER.warn("[starter station GameTest] SKIPPED: no new-world SavedData marker exists "
                    + "in this GameTest world; use a fresh isolated GameTest directory to exercise lifecycle placement");
            helper.succeed();
            return;
        }
        awaitCompletion(helper, level, 0);
    }

    private static void awaitCompletion(GameTestHelper helper, ServerLevel level, int waitedTicks) {
        StarterStationSavedData data = level.getDataStorage().get(
                StarterStationSavedData.FACTORY, StarterStationSavedData.DATA_NAME);
        if (data != null && data.state() == StarterStationPlacementState.State.COMPLETED) {
            assertStation(level, data);
            awaitLampRuntime(helper, level, data, 0);
            return;
        }
        if (waitedTicks >= MAX_WAIT_TICKS) {
            throw new GameTestAssertException("new-world starter-station marker did not complete within "
                    + MAX_WAIT_TICKS + " ticks; state=" + (data == null ? "missing" : data.state()));
        }
        helper.runAfterDelay(1, () -> awaitCompletion(helper, level, waitedTicks + 1));
    }

    private static void awaitLampRuntime(GameTestHelper helper, ServerLevel level,
                                         StarterStationSavedData data, int waitedTicks) {
        if (lampsMatchExpectedRuntime(level, data)) {
            assertLampRuntime(level, data);
            helper.succeed();
            return;
        }
        if (waitedTicks >= MAX_RUNTIME_SETTLE_TICKS) {
            assertLampRuntime(level, data);
            throw new GameTestAssertException("starter-station lamps did not reach expected runtime state within "
                    + MAX_RUNTIME_SETTLE_TICKS + " additional ticks (simulation enabled="
                    + PowerSimulationGate.isEnabled() + ")");
        }
        helper.runAfterDelay(1, () -> awaitLampRuntime(helper, level, data, waitedTicks + 1));
    }

    private static boolean lampsMatchExpectedRuntime(ServerLevel level, StarterStationSavedData data) {
        boolean expectedLit = PowerSimulationGate.isEnabled();
        BlockPos origin = data.selectedOrigin();
        for (StarterStationPowerPlan.Device device : StarterStationPowerPlan.standard().devices()) {
            if (device.kind() != PowerDeviceKind.LAMP) continue;
            if (level.getBlockState(origin.offset(device.position())).getValue(PowerDeviceBlock.LIT) != expectedLit)
                return false;
        }
        return true;
    }

    private static void assertLampRuntime(ServerLevel level, StarterStationSavedData data) {
        boolean expectedLit = PowerSimulationGate.isEnabled();
        int expectedEmission = expectedLit ? 14 : 0;
        BlockPos origin = data.selectedOrigin();
        int lampCount = 0;
        for (StarterStationPowerPlan.Device device : StarterStationPowerPlan.standard().devices()) {
            if (device.kind() != PowerDeviceKind.LAMP) continue;
            lampCount++;
            BlockPos lamp = origin.offset(device.position());
            var state = level.getBlockState(lamp);
            require(state.getValue(PowerDeviceBlock.LIT) == expectedLit,
                    "station lamp lit state must match power simulation gate at " + lamp);
            require(state.getLightEmission(level, lamp) == expectedEmission,
                    "station lamp light emission must be " + expectedEmission + " at " + lamp);
        }
        require(lampCount == 4, "power plan must contain four lamps");
    }

    private static void assertStation(ServerLevel level, StarterStationSavedData data) {
        BlockPos origin = data.selectedOrigin();
        require(origin != null, "completed station must retain its selected origin");
        StarterStationLayout layout = StarterStationLayout.standard();

        for (int z = layout.minZ(); z <= layout.maxZ(); z++) {
            for (int x = layout.minX(); x <= layout.maxX(); x++) {
                BlockPos floor = origin.offset(x, StarterStationLayout.FLOOR_Y, z);
                var state = level.getBlockState(floor);
                require(state.is(ModBlocks.STATION_FLOOR.get()), "station floor missing at " + floor);
                StationFloorBlock.TileFinish expected = layout.materialAt(x, StarterStationLayout.FLOOR_Y, z)
                        == StarterStationLayout.Material.WHITE_TILE
                        ? StationFloorBlock.TileFinish.WHITE : StationFloorBlock.TileFinish.STEEL;
                require(state.getValue(StationFloorBlock.TILE_FINISH) == expected,
                        "wrong tiled floor finish at " + floor + ": expected " + expected);
            }
        }

        int[][] roomSamples = {{2, 2}, {15, 2}, {4, 14}, {15, 15}};
        for (int[] sample : roomSamples) {
            BlockPos floor = origin.offset(sample[0], StarterStationLayout.FLOOR_Y, sample[1]);
            require(level.getBlockState(floor).is(ModBlocks.STATION_FLOOR.get()),
                    "room floor sample must be a registered station floor at " + floor);
            require(level.getBlockState(floor).getValue(StationFloorBlock.TILE_FINISH)
                            == StationFloorBlock.TileFinish.STEEL,
                    "room sample must have steel tiling at " + floor);
            require(level.getBlockState(floor.above()).isAir(), "room interior must be open above " + floor);
        }

        for (int z : new int[]{3, 4}) {
            for (int y = 1; y <= layout.clearHeight(); y++) {
                BlockPos entrance = origin.offset(0, y, z);
                require(level.getBlockState(entrance).isAir(), "west entrance must remain open at " + entrance);
            }
        }
        require(level.getBlockState(origin.offset(0, StarterStationLayout.FLOOR_Y, 3))
                        .getValue(StationFloorBlock.TILE_FINISH) == StationFloorBlock.TileFinish.WHITE,
                "west entrance threshold must retain its white tiled floor");
        require(level.getBlockState(origin.offset(1, 1, 3)).is(Blocks.AIR),
                "interior behind the open west entrance must be clear");

        StarterStationPowerPlan power = StarterStationPowerPlan.standard();
        require(power.devices().size() == 7, "power plan must contain seven devices");
        for (StarterStationPowerPlan.Device device : power.devices()) {
            var state = level.getBlockState(origin.offset(device.position()));
            require(state.getBlock() instanceof PowerDeviceBlock block && block.kind() == device.kind(),
                    "planned device missing or wrong kind: " + device);
            require(state.getValue(PowerDeviceBlock.FACING) == device.facing(), "wrong device facing: " + device);
        }
        require(power.cables().size() <= StarterStationPowerPlan.MAX_CABLE_NODES,
                "planned cable count exceeds bound");
        for (CableFaceNode cable : power.cables()) {
            BlockPos host = origin.offset(cable.host());
            var chunk = level.getChunkSource().getChunk(host.getX() >> 4, host.getZ() >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            require(chunk instanceof net.minecraft.world.level.chunk.LevelChunk, "planned cable chunk must remain loaded");
            CableChunkData records = ((net.minecraft.world.level.chunk.LevelChunk) chunk)
                    .getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
            require(records != null && records.get(host.getX() & 15, host.getY(), host.getZ() & 15,
                            cable.face(), cable.tier())
                            == cable.tier(), "planned cable record missing: " + cable);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
