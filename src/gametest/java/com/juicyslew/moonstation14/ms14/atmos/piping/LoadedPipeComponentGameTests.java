package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LoadedPipeComponentGameTests {
    private LoadedPipeComponentGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void discoverySeparatesLanesAndDoesNotMutateGas(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos east = center.relative(Direction.EAST);
        BlockPos west = center.relative(Direction.WEST);
        BlockPos north = center.relative(Direction.NORTH);
        BlockPos south = center.relative(Direction.SOUTH);
        BlockPos above = center.above();

        place(level, center, VisualPipeBlockEntity.SECONDARY | VisualPipeBlockEntity.TERTIARY);
        place(level, east, VisualPipeBlockEntity.SECONDARY);
        place(level, west, VisualPipeBlockEntity.SECONDARY);
        place(level, north, VisualPipeBlockEntity.TERTIARY);
        place(level, south, VisualPipeBlockEntity.TERTIARY);
        place(level, above, VisualPipeBlockEntity.SECONDARY);

        for (BlockPos pos : new BlockPos[]{center, east, west, north, south, above}) {
            VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
            for (int lane : new int[]{VisualPipeBlockEntity.SECONDARY, VisualPipeBlockEntity.TERTIARY}) {
                if (visual.hasLayer(lane))
                    require(LoadedPipeGasOwner.createVacuumLane(level, pos, lane).isPresent(), "trusted lane owner created at " + pos + " lane " + lane);
            }
        }

        PipeGasInventorySavedData data = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeTopology.NodeKey secondaryKey = key(center, PipeTopology.Layer.SECONDARY);
        PipeTopology.NodeKey tertiaryKey = key(center, PipeTopology.Layer.TERTIARY);
        PipeGasInventory before = data.snapshot();
        PipeGasInventory.Entry secondary = before.get(secondaryKey).orElseThrow();
        PipeGasInventory.Entry tertiary = before.get(tertiaryKey).orElseThrow();
        double secondaryEnergy = new GasMixture(Map.of(GasType.OXYGEN, 2.5), 315.0).thermalEnergy();
        double tertiaryEnergy = new GasMixture(Map.of(GasType.PLASMA, 1.25), 275.0).thermalEnergy();
        PipeGasInventory secondarySeeded = before.withMixture(secondaryKey, secondary.incarnation(), secondary.version(),
                new GasMixture(Map.of(GasType.OXYGEN, 2.5), 315.0));
        require(data.replace(before.revision(), before, secondarySeeded), "seed secondary center lane mixture");

        PipeGasInventory afterSecondarySeed = data.snapshot();
        PipeGasInventory.Entry tertiaryAfterSecondarySeed = afterSecondarySeed.get(tertiaryKey).orElseThrow();
        PipeGasInventory tertiarySeeded = afterSecondarySeed.withMixture(tertiaryKey,
                tertiaryAfterSecondarySeed.incarnation(), tertiaryAfterSecondarySeed.version(),
                new GasMixture(Map.of(GasType.PLASMA, 1.25), 275.0));
        require(data.replace(afterSecondarySeed.revision(), afterSecondarySeed, tertiarySeeded),
                "seed tertiary center lane mixture");
        PipeGasInventory stable = data.snapshot();

        var secondaryComponent = LoadedPipeComponent.discover(level, center, VisualPipeBlockEntity.SECONDARY)
                .orElseThrow(() -> new GameTestAssertException("secondary component discovered"));
        require(secondaryComponent.nodes().size() == 4, "secondary has center, east, west, and above only");
        require(keys(secondaryComponent).equals(Set.of(secondaryKey, key(east, PipeTopology.Layer.SECONDARY),
                key(west, PipeTopology.Layer.SECONDARY), key(above, PipeTopology.Layer.SECONDARY))), "secondary membership excludes other lanes");
        require(secondaryComponent.nodes().stream().allMatch(node -> node.volume() == 0.2), "secondary nodes use SS14 0.2 m3 volume");

        var tertiaryComponent = LoadedPipeComponent.discover(level, center, VisualPipeBlockEntity.TERTIARY)
                .orElseThrow(() -> new GameTestAssertException("tertiary component discovered"));
        require(tertiaryComponent.nodes().size() == 3, "tertiary has center, north, and south only");
        require(keys(tertiaryComponent).equals(Set.of(tertiaryKey, key(north, PipeTopology.Layer.TERTIARY),
                key(south, PipeTopology.Layer.TERTIARY))), "tertiary membership excludes other lanes");
        require(tertiaryComponent.nodes().stream().allMatch(node -> node.volume() == 0.2), "tertiary nodes use SS14 0.2 m3 volume");

        require(LoadedPipeComponent.discover(level, east, VisualPipeBlockEntity.TERTIARY).isEmpty(),
                "wrong-lane start at secondary-only neighbor is empty");
        PipeGasInventory after = data.snapshot();
        require(after == stable && after.revision() == stable.revision(), "read-only discovery preserves inventory identity and revision");
        assertMixture(after.get(secondaryKey).orElseThrow(), GasType.OXYGEN, 2.5, 315.0, secondaryEnergy, "secondary center gas");
        assertMixture(after.get(tertiaryKey).orElseThrow(), GasType.PLASMA, 1.25, 275.0, tertiaryEnergy, "tertiary center gas");
        helper.succeed();
    }

    private static void place(ServerLevel level, BlockPos pos, int layers) {
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual
                && visual.setLayersForTest(layers), "visual pipe lanes configured at " + pos);
    }

    private static PipeTopology.NodeKey key(BlockPos pos, PipeTopology.Layer layer) {
        return new PipeTopology.NodeKey(pos, layer);
    }

    private static Set<PipeTopology.NodeKey> keys(LoadedPipeComponent.Snapshot snapshot) {
        return snapshot.nodes().stream().map(LoadedPipeComponent.Node::key).collect(java.util.stream.Collectors.toSet());
    }

    private static void assertMixture(PipeGasInventory.Entry entry, GasType gas, double moles,
                                     double temperature, double energy, String message) {
        require(entry.mixture().gasMoles().equals(Map.of(gas, moles)), message + " species and moles are unchanged");
        require(entry.mixture().temperatureKelvin() == temperature, message + " temperature is unchanged");
        require(entry.mixture().thermalEnergy() == energy, message + " thermal energy is unchanged");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
