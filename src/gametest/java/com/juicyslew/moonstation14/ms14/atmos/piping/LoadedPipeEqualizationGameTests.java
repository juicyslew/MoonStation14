package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;

@GameTestTags({"ventilation", "atmosphere"})
@PrefixGameTestTemplate(false)
public final class LoadedPipeEqualizationGameTests {
    private LoadedPipeEqualizationGameTests() { }

@GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void equalizesOnlySelectedCrossingLaneAndFailsClosedWhenDormant(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos west = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos east = helper.absolutePos(new BlockPos(5, 1, 4));
        BlockPos north = helper.absolutePos(new BlockPos(4, 1, 3));
        BlockPos south = helper.absolutePos(new BlockPos(4, 1, 5));
        BlockPos devicePos = helper.absolutePos(new BlockPos(6, 1, 4));

        placePipe(level, west, VisualPipeBlockEntity.SECONDARY);
        placePipe(level, center, VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY);
        placePipe(level, east, VisualPipeBlockEntity.SECONDARY);
        placePipe(level, north, VisualPipeBlockEntity.TERTIARY);
        placePipe(level, south, VisualPipeBlockEntity.TERTIARY);
        LoadedPipeGasOwner.createVacuumLane(level, west, VisualPipeBlockEntity.SECONDARY).orElseThrow();
        LoadedPipeGasOwner.createVacuumLane(level, center, VisualPipeBlockEntity.SECONDARY).orElseThrow();
        LoadedPipeGasOwner.createVacuumLane(level, east, VisualPipeBlockEntity.SECONDARY).orElseThrow();
        LoadedPipeGasOwner.createVacuumLane(level, center, VisualPipeBlockEntity.TERTIARY).orElseThrow();
        LoadedPipeGasOwner.createVacuumLane(level, north, VisualPipeBlockEntity.TERTIARY).orElseThrow();
        LoadedPipeGasOwner.createVacuumLane(level, south, VisualPipeBlockEntity.TERTIARY).orElseThrow();

        level.setBlock(devicePos, ModBlocks.GAS_SCRUBBER.get().defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.EAST), 3);
        var device = (VentilationDeviceBlockEntity) level.getBlockEntity(devicePos);
        require(device != null && device.pipePortPos().equals(east), "device port points west to lane end");
        var initialSettings = device.config().settings();
        var configured = new VentilationDeviceConfig.Settings(initialSettings.revision(), true,
                initialSettings.externalPressureBoundKPa(), initialSettings.targetPressureChangeKPaPerSecond(),
                initialSettings.targetPressureKPa(), initialSettings.volumeRateM3PerSecond(),
                initialSettings.scrubberMode(), initialSettings.filteredGases(), VentilationDeviceConfig.Layer.SECONDARY);
        require(device.compareAndSetSettings(initialSettings.revision(), configured), "select enabled secondary lane");

        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var before = saved.snapshot();
        seed(saved, before, west, VisualPipeBlockEntity.SECONDARY,
                new GasMixture(Map.of(GasType.OXYGEN, 6.0), 320.0));
        var afterFirst = saved.snapshot();
        seed(saved, afterFirst, center, VisualPipeBlockEntity.TERTIARY,
                new GasMixture(Map.of(GasType.PLASMA, 2.0), 300.0));
        var beforeEqualize = saved.snapshot();
        require(LoadedPipeEqualization.equalize(level, device), "equalization succeeds");
        var result = saved.snapshot();
        for (BlockPos pos : new BlockPos[]{west, center, east}) {
            var mixture = result.get(key(pos, PipeTopology.Layer.SECONDARY)).orElseThrow().mixture();
            require(Double.compare(mixture.gasMoles().getOrDefault(GasType.OXYGEN, 0.0), 2.0) == 0
                    && mixture.gasMoles().size() == 1 && Double.compare(mixture.temperatureKelvin(), 320.0) == 0,
                    "selected lane evenly shares oxygen and conserves temperature");
        }
        var isolated = result.get(key(center, PipeTopology.Layer.TERTIARY)).orElseThrow().mixture();
        require(isolated.gasMoles().equals(Map.of(GasType.PLASMA, 2.0))
                && Double.compare(isolated.temperatureKelvin(), 300.0) == 0, "crossing lane untouched");
        for (BlockPos pos : new BlockPos[]{west, center, east, north, south})
            require(result.get(key(pos, PipeTopology.Layer.PRIMARY)).isEmpty(), "no primary ownership created");
        require(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 3))).is(Blocks.AIR), "world blocks untouched");

        long revision = result.revision();
        require(LoadedPipeEqualization.equalize(level, device), "already equal lane remains valid");
        require(saved.snapshot().revision() == revision, "repeat equalization does not revise gas inventory");
        require(LoadedPipeGasOwner.deactivate(level, east, VisualPipeBlockEntity.SECONDARY), "deactivate selected lane endpoint");
        var dormantBefore = saved.snapshot();
        require(!LoadedPipeEqualization.equalize(level, device), "incomplete live lane rejected");
        require(saved.snapshot() == dormantBefore, "dormant component causes no gas mutation");

        level.setBlock(devicePos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(devicePos, ModBlocks.GAS_SCRUBBER.get().defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.EAST), 3);
        var replacement = (VentilationDeviceBlockEntity) level.getBlockEntity(devicePos);
        var beforeStaleCall = saved.snapshot();
        require(replacement != null && replacement != device, "device block entity was replaced");
        require(!LoadedPipeEqualization.equalize(level, device), "stale replaced device is rejected");
        require(saved.snapshot() == beforeStaleCall, "stale device call leaves gas inventory unchanged");
        helper.succeed();
    }

    private static void placePipe(ServerLevel level, BlockPos pos, int layers) {
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        var visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(visual != null && visual.setLayersForTest(layers), "set pipe lane mask");
    }

    private static void seed(PipeGasInventorySavedData saved, PipeGasInventory before, BlockPos pos,
                             int lane, GasMixture mixture) {
        var layer = switch (lane) {
            case VisualPipeBlockEntity.SECONDARY -> PipeTopology.Layer.SECONDARY;
            case VisualPipeBlockEntity.TERTIARY -> PipeTopology.Layer.TERTIARY;
            default -> throw new IllegalArgumentException("unsupported lane");
        };
        var key = key(pos, layer);
        var entry = before.get(key).orElseThrow();
        var after = before.withMixture(key, entry.incarnation(), entry.version(), mixture);
        require(saved.replace(before.revision(), before, after), "seed finite test gas");
    }

    private static PipeTopology.NodeKey key(BlockPos pos, PipeTopology.Layer layer) {
        return new PipeTopology.NodeKey(pos, layer);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
