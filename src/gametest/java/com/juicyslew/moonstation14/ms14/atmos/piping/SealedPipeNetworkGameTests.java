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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedPipeNetworkGameTests {
    private SealedPipeNetworkGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sealedWallConnectsOnlyMatchingLaneAndAxis(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos wall = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos west = wall.west();
        BlockPos east = wall.east();
        BlockPos north = wall.north();
        BlockPos south = wall.south();

        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        require(LoadedSealedWallSegment.prepareOne(level, wall, Direction.EAST,
                PipeTopology.Layer.SECONDARY, UUID.randomUUID()).isPresent(), "secondary east-west wall segment admitted");
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, wall,
                PipeTopology.Layer.SECONDARY), "secondary sealed-wall gas owner created");
        require(LoadedSealedWallSegment.prepareOne(level, wall, Direction.EAST,
                PipeTopology.Layer.TERTIARY, UUID.randomUUID()).isPresent(), "tertiary east-west wall segment admitted");
        require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, wall,
                PipeTopology.Layer.TERTIARY), "tertiary sealed-wall gas owner created");

        place(level, west, VisualPipeBlockEntity.SECONDARY);
        place(level, east, VisualPipeBlockEntity.SECONDARY);
        place(level, north, VisualPipeBlockEntity.TERTIARY);
        place(level, south, VisualPipeBlockEntity.TERTIARY);
        for (BlockPos pos : new BlockPos[]{west, east, north, south}) {
            VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(pos);
            require(LoadedPipeGasOwner.createVacuumLane(level, pos,
                    visual.hasLayer(VisualPipeBlockEntity.SECONDARY) ? VisualPipeBlockEntity.SECONDARY : VisualPipeBlockEntity.TERTIARY).isPresent(),
                    "visual pipe gas owner created at " + pos);
        }

        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeTopology.NodeKey westKey = key(west, PipeTopology.Layer.SECONDARY);
        PipeGasInventory beforeSeed = saved.snapshot();
        PipeGasInventory.Entry westEntry = beforeSeed.get(westKey).orElseThrow();
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 6.0), 310.0);
        PipeGasInventory seeded = beforeSeed.withMixture(westKey, westEntry.incarnation(), westEntry.version(), source);
        require(saved.replace(beforeSeed.revision(), beforeSeed, seeded), "seed west secondary pipe with oxygen");

        var component = LoadedPipeComponent.discover(level, west, VisualPipeBlockEntity.SECONDARY)
                .orElseThrow(() -> new GameTestAssertException("secondary component discovered"));
        require(component.nodes().size() == 3, "secondary component contains west, sealed wall, and east exactly");
        require(component.nodes().stream().map(node -> node.key()).collect(Collectors.toSet()).equals(
                java.util.Set.of(westKey, key(wall, PipeTopology.Layer.SECONDARY), key(east, PipeTopology.Layer.SECONDARY))),
                "secondary component membership");

        var tertiaryNorth = LoadedPipeComponent.discover(level, north, VisualPipeBlockEntity.TERTIARY)
                .orElseThrow(() -> new GameTestAssertException("tertiary north component discovered"));
        var tertiarySouth = LoadedPipeComponent.discover(level, south, VisualPipeBlockEntity.TERTIARY)
                .orElseThrow(() -> new GameTestAssertException("tertiary south component discovered"));
        require(tertiaryNorth.nodes().size() == 1 && tertiarySouth.nodes().size() == 1,
                "north-south tertiary pipes do not traverse the east-west sealed port");
        require(LoadedPipeComponent.discover(level, wall, VisualPipeBlockEntity.TERTIARY).orElseThrow().nodes().size() == 1,
                "tertiary wall owner remains isolated from secondary neighbors");

        double initialEnergy = component.nodes().stream().mapToDouble(node -> node.mixture().thermalEnergy()).sum();
        var plan = PipeComponentEqualization.plan(component.nodes().stream()
                .map(node -> new PipeComponentEqualization.Node(node.key(), node.incarnation(), node.entry().version(),
                        node.volume(), node.mixture())).toList());
        PipeGasInventory beforeEqualization = saved.snapshot();
        PipeGasInventory afterEqualization = beforeEqualization.withBatchMixtures(plan);
        require(saved.replace(beforeEqualization.revision(), beforeEqualization, afterEqualization), "apply conserved component equalization");
        for (BlockPos pos : new BlockPos[]{west, wall, east}) {
            var entry = saved.snapshot().get(key(pos, PipeTopology.Layer.SECONDARY)).orElseThrow();
            require(Math.abs(entry.mixture().gasMoles().getOrDefault(GasType.OXYGEN, 0.0) - 2.0) < 1e-12,
                    "equalized node contains two moles of oxygen at " + pos);
        }
        double finalEnergy = List.of(west, wall, east).stream().mapToDouble(pos -> saved.snapshot()
                .get(key(pos, PipeTopology.Layer.SECONDARY)).orElseThrow().mixture().thermalEnergy()).sum();
        require(Math.abs(finalEnergy - initialEnergy) <= Math.max(1e-9, Math.abs(initialEnergy) * 1e-12),
                "equalization conserves thermal energy");

        long oxygenVersion = saved.snapshot().get(key(west, PipeTopology.Layer.SECONDARY)).orElseThrow().version();
        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        var afterReplacement = LoadedPipeComponent.discover(level, west, VisualPipeBlockEntity.SECONDARY)
                .orElseThrow(() -> new GameTestAssertException("west component remains discoverable after host replacement"));
        require(afterReplacement.nodes().size() == 1, "replacement wall immediately removes stale sealed edge");
        var retained = saved.snapshot().get(key(west, PipeTopology.Layer.SECONDARY)).orElseThrow();
        require(retained.version() == oxygenVersion && Math.abs(retained.mixture().gasMoles().getOrDefault(GasType.OXYGEN, 0.0) - 2.0) < 1e-12,
                "stale sealed-wall invalidation leaves owned gas inert and unchanged");
        require(saved.snapshot().get(key(wall, PipeTopology.Layer.SECONDARY)).orElseThrow().mixture().gasMoles()
                .getOrDefault(GasType.OXYGEN, 0.0) == 2.0, "sealed wall gas remains saved after invalidation");
        helper.succeed();
    }

    private static void place(ServerLevel level, BlockPos pos, int layers) {
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual && visual.setLayersForTest(layers),
                "visual pipe lanes configured at " + pos);
    }

    private static PipeTopology.NodeKey key(BlockPos pos, PipeTopology.Layer layer) {
        return new PipeTopology.NodeKey(pos, layer);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
