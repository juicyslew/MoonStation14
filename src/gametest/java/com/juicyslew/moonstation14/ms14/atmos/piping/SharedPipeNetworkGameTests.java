package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestTags({"ventilation", "atmosphere"})
public final class SharedPipeNetworkGameTests {
    private SharedPipeNetworkGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 40, templateNamespace = "moonstation14")
    public static void adjacentPipeOwnersShareLiveNetworkGas(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos second = first.east();
        level.setBlock(first, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        level.setBlock(second, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        require(LoadedPipeGasOwner.createVacuumLane(level, first, VisualPipeBlockEntity.PRIMARY).isPresent(), "first canonical owner admitted");
        require(LoadedPipeGasOwner.createVacuumLane(level, second, VisualPipeBlockEntity.PRIMARY).isPresent(), "second canonical owner admitted");
        PipeTopology.NodeKey a = new PipeTopology.NodeKey(first, PipeTopology.Layer.PRIMARY);
        PipeTopology.NodeKey b = new PipeTopology.NodeKey(second, PipeTopology.Layer.PRIMARY);
        LoadedPipeNetworks.invalidateTopology(level, first);
        require(LoadedPipeNetworks.reconcile(level), "dirty topology reconciled");
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeGasInventory before = saved.snapshot();
        var networkA = before.network(a).orElseThrow();
        var networkB = before.network(b).orElseThrow();
        require(networkA == networkB, "connected lanes resolve the same network object");
        require(networkA.members().equals(Set.of(a, b)), "exact physical members");
        double memberVolume = before.get(a).orElseThrow().volume() + before.get(b).orElseThrow().volume();
        require(Double.compare(networkA.mixture().volumeCubicMeters(), (double) (float) memberVolume) == 0,
                "network volume equals canonical float representation of member volume sum");
        var entryA = before.get(a).orElseThrow();
        var entryB = before.get(b).orElseThrow();
        GasMixture shared = new GasMixture(Map.of(GasType.OXYGEN, 4.0), 290.0,
                entryA.volume() + entryB.volume());
        PipeGasInventory after = before.withNetworkMixtures(Map.of(a, shared));
        require(saved.replace(before.revision(), before, after), "shared network gas write committed");
        var result = saved.snapshot();
        require(result.mixture(a) == result.mixture(b), "both node reads return the same shared mixture instance");
        require(result.mixture(b).gasMoles().equals(shared.gasMoles()), "write through first member immediately visible at second");
        require(result.get(a).orElseThrow() == entryA && result.get(b).orElseThrow() == entryB,
                "ordinary gas write preserves node metadata objects");
        require(result.network(a).orElseThrow().members() == networkA.members(), "ordinary write preserves member-set identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, templateNamespace = "moonstation14")
    public static void ownerUnloadAndReactivationRetainNetworkGas(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos second = first.east();
        level.setBlock(first, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        level.setBlock(second, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        var idA = LoadedPipeGasOwner.createVacuumLane(level, first, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        LoadedPipeGasOwner.createVacuumLane(level, second, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        PipeTopology.NodeKey a = new PipeTopology.NodeKey(first, PipeTopology.Layer.PRIMARY);
        PipeTopology.NodeKey b = new PipeTopology.NodeKey(second, PipeTopology.Layer.PRIMARY);
        LoadedPipeNetworks.invalidateTopology(level, first);
        require(LoadedPipeNetworks.reconcile(level), "topology reconciled");
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeGasInventory before = saved.snapshot();
        GasMixture gas = new GasMixture(Map.of(GasType.NITROGEN, 3.0), 310.0,
                before.get(a).orElseThrow().volume() + before.get(b).orElseThrow().volume());
        require(saved.replace(before.revision(), before, before.withNetworkMixtures(Map.of(a, gas))), "seed shared network mixture");
        var membersBefore = saved.snapshot().network(a).orElseThrow().members();
        require(LoadedPipeGasOwner.deactivate(level, first, VisualPipeBlockEntity.PRIMARY), "canonical owner deactivation");
        var dormant = saved.snapshot();
        var liveViewWhileDormant = LoadedPipeNetworks.network(level, b).orElseThrow();
        require(liveViewWhileDormant.members().equals(membersBefore), "deactivation alone retains membership");
        require(liveViewWhileDormant.mixture().gasMoles().equals(gas.gasMoles()), "active member still reads shared gas while peer is dormant");
        GasMixture changedWhileDormant = new GasMixture(Map.of(GasType.NITROGEN, 5.0), 315.0,
                liveViewWhileDormant.mixture().volumeCubicMeters());
        var changed = dormant.withNetworkMixtures(Map.of(b, changedWhileDormant));
        require(saved.replace(dormant.revision(), dormant, changed), "shared gas changed through active member while peer is dormant");
        require(LoadedPipeGasOwner.reconcileExisting(level, first, VisualPipeBlockEntity.PRIMARY), "canonical owner reactivation");
        require(LoadedPipeNetworks.network(level, a).isPresent(), "reactivation triggers topology reconciliation through normal network read");
        var restored = saved.snapshot();
        require(restored.get(a).orElseThrow().incarnation().equals(idA), "same owner incarnation restored");
        require(restored.network(a).orElseThrow().members().equals(membersBefore), "reactivation retains topology membership");
        require(restored.mixture(a).gasMoles().equals(changedWhileDormant.gasMoles())
                && Double.compare(restored.mixture(a).thermalEnergy(), changedWhileDormant.thermalEnergy()) == 0,
                "reactivation preserves the latest shared gas without refill");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60, templateNamespace = "moonstation14")
    public static void manualValveMergesAndSplitsWithoutLosingMolesOrThermalEnergy(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos valvePos = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos north = valvePos.north(), south = valvePos.south();
        level.setBlock(valvePos, ModBlocks.GAS_VALVE.get().defaultBlockState(), 3);
        level.setBlock(north, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        level.setBlock(south, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        require(LoadedPipeGasOwner.createVacuumLane(level, north, VisualPipeBlockEntity.PRIMARY).isPresent(), "north pipe owner admitted");
        require(LoadedPipeGasOwner.createVacuumLane(level, south, VisualPipeBlockEntity.PRIMARY).isPresent(), "south pipe owner admitted");
        require(level.getBlockEntity(valvePos) instanceof AtmosMachineBlockEntity, "manual valve host installed");
        AtmosMachineBlockEntity valve = (AtmosMachineBlockEntity) level.getBlockEntity(valvePos);
        valve.reconcileLoadedPorts(level);
        require(valve.applySettings(AtmosMachineSettings.Operation.SET_VALVE_OPEN,
                new AtmosMachineSettings.BooleanValue(false)), "valve closed through production settings operation");
        var ports = valve.gasPorts();
        require(ports.size() == 2, "valve has two canonical ports");
        PipeTopology.NodeKey northKey = new PipeTopology.NodeKey(north, PipeTopology.Layer.PRIMARY);
        PipeTopology.NodeKey southKey = new PipeTopology.NodeKey(south, PipeTopology.Layer.PRIMARY);
        PipeTopology.NodeKey inlet = ports.stream().filter(p -> p.id().equals("inlet")).findFirst().orElseThrow().key(valvePos);
        PipeTopology.NodeKey outlet = ports.stream().filter(p -> p.id().equals("outlet")).findFirst().orElseThrow().key(valvePos);
        LoadedPipeNetworks.invalidateTopology(level, valvePos);
        require(LoadedPipeNetworks.reconcile(level), "closed topology reconciled");
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var closed = saved.snapshot();
        require(closed.network(northKey).orElseThrow() != closed.network(southKey).orElseThrow(), "closed valve separates both pipe ends");

        Map<PipeTopology.NodeKey, GasMixture> seeds = Map.of(
                northKey, new GasMixture(Map.of(GasType.OXYGEN, 2.0), 280.0,
                        closed.network(northKey).orElseThrow().mixture().volumeCubicMeters()),
                southKey, new GasMixture(Map.of(GasType.NITROGEN, 3.0), 320.0,
                        closed.network(southKey).orElseThrow().mixture().volumeCubicMeters()));
        PipeGasInventory seeded = closed.withNetworkMixtures(seeds);
        require(saved.replace(closed.revision(), closed, seeded), "seed finite species at separate closed networks");
        double molesBefore = seeds.values().stream().mapToDouble(GasMixture::totalMoles).sum();
        double energyBefore = seeds.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        require(valve.applySettings(AtmosMachineSettings.Operation.SET_VALVE_OPEN,
                new AtmosMachineSettings.BooleanValue(true)), "valve opened through production settings operation");
        require(LoadedPipeNetworks.network(level, northKey).isPresent(), "normal network read reconciles settings-driven open topology");
        var open = saved.snapshot();
        var merged = open.network(northKey).orElseThrow();
        require(merged == open.network(southKey).orElseThrow() && merged == open.network(inlet).orElseThrow()
                && merged == open.network(outlet).orElseThrow(), "open manual valve merges both pipe ends and ports");
        require(Math.abs(merged.mixture().totalMoles() - molesBefore) < 1e-5, "merge conserves total moles");
        require(Math.abs(merged.mixture().thermalEnergy() - energyBefore) < 1e-3, "merge conserves Cp-weighted energy across temperatures");
        require(valve.applySettings(AtmosMachineSettings.Operation.SET_VALVE_OPEN,
                new AtmosMachineSettings.BooleanValue(false)), "valve closed again through production settings operation");
        require(LoadedPipeNetworks.network(level, northKey).isPresent(), "normal network read reconciles settings-driven closed topology");
        var split = saved.snapshot();
        require(split.network(northKey).orElseThrow() != split.network(southKey).orElseThrow(), "closing valve splits network");
        double splitMoles = 0, splitEnergy = 0;
        Set<java.util.UUID> counted = new java.util.HashSet<>();
        for (PipeTopology.NodeKey key : Set.of(northKey, inlet, outlet, southKey)) {
            var network = split.network(key).orElseThrow();
            if (counted.add(network.id())) {
                splitMoles += network.mixture().totalMoles();
                splitEnergy += network.mixture().thermalEnergy();
            }
        }
        require(Math.abs(splitMoles - molesBefore) < 1e-5 && Math.abs(splitEnergy - energyBefore) < 1e-3,
                "split conserves total moles and thermal energy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180, templateNamespace = "moonstation14")
    public static void unknownFrontierBlocksOnlyItsNetworkAndRecoversOnChunkLoad(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int ownerChunkX = (origin.getX() >> 4) + 128;
        int chunkZ = origin.getZ() >> 4;
        level.getChunk(ownerChunkX, chunkZ);
        BlockPos frontier = new BlockPos((ownerChunkX << 4) + 15, origin.getY() + 1, (chunkZ << 4) + 8);
        BlockPos independent = frontier.west(5);
        var full = net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
        int eastChunkX = (frontier.getX() >> 4) + 1;
        require(level.getChunkSource().getChunk(eastChunkX, chunkZ, full, false) == null,
                "remote east frontier is actually not FULL before setup");
        int fixturePlacementFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(frontier, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), fixturePlacementFlags);
        level.setBlock(independent, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), fixturePlacementFlags);
        var frontierId = LoadedPipeGasOwner.createVacuumLane(level, frontier, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        LoadedPipeGasOwner.createVacuumLane(level, independent, VisualPipeBlockEntity.PRIMARY).orElseThrow();
        PipeTopology.NodeKey blockedKey = new PipeTopology.NodeKey(frontier, PipeTopology.Layer.PRIMARY);
        PipeTopology.NodeKey independentKey = new PipeTopology.NodeKey(independent, PipeTopology.Layer.PRIMARY);
        require(level.getChunkSource().getChunk(eastChunkX, chunkZ, full, false) == null,
                "fixture setup did not force-load frontier");
        require(LoadedPipeNetworks.reconcile(level), "initial dirty topology reconcile runs");
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var before = saved.snapshot();
        var blockedOld = before.network(blockedKey).orElseThrow();
        var independentBefore = before.network(independentKey).orElseThrow();
        require(LoadedPipeNetworks.network(level, blockedKey).isEmpty(), "unknown frontier blocks its affected net");
        require(LoadedPipeNetworks.network(level, independentKey).orElseThrow() == independentBefore,
                "independent fully-known net remains available");
        require(blockedOld.mixture().gasMoles().isEmpty() && independentBefore.mixture().gasMoles().isEmpty(),
                "blocked discovery preserves both finite-gas states");
        require(level.getChunkSource().getChunk(eastChunkX, chunkZ, full, false) == null,
                "topology discovery did not force-load frontier");

        level.getChunk(eastChunkX, chunkZ); // Explicit fixture-owned load; production never force-loads it.
        require(level.getChunkSource().getChunk(eastChunkX, chunkZ, full, false) instanceof net.minecraft.world.level.chunk.LevelChunk,
                "fixture frontier chunk is now FULL");
        awaitFrontierRecovery(helper, level, saved, blockedKey, independentKey, frontierId,
                blockedOld.id(), independentBefore.id(), 0);
    }

    private static void awaitFrontierRecovery(GameTestHelper helper, ServerLevel level,
                                              PipeGasInventorySavedData saved,
                                              PipeTopology.NodeKey blockedKey, PipeTopology.NodeKey independentKey,
                                              java.util.UUID frontierId, java.util.UUID blockedNetworkId,
                                              java.util.UUID independentNetworkId, int waited) {
        helper.runAfterDelay(2, () -> {
            var recovered = LoadedPipeNetworks.network(level, blockedKey).orElse(null);
            if (recovered == null) {
                if (waited >= 120) throw new GameTestAssertException("canonical chunk-load callback did not recover blocked topology");
                awaitFrontierRecovery(helper, level, saved, blockedKey, independentKey, frontierId,
                        blockedNetworkId, independentNetworkId, waited + 2);
                return;
            }
            var inventory = saved.snapshot();
            require(inventory.get(blockedKey).orElseThrow().incarnation().equals(frontierId), "frontier owner identity unchanged");
            require(recovered.id().equals(blockedNetworkId) && recovered.members().equals(Set.of(blockedKey)),
                    "resolved frontier topology retains its original singleton net");
            require(inventory.mixture(blockedKey).gasMoles().isEmpty()
                    && inventory.mixture(blockedKey).thermalEnergy() == 0.0, "recovery neither refills nor loses gas");
            var independent = LoadedPipeNetworks.network(level, independentKey).orElseThrow();
            require(independent.id().equals(independentNetworkId)
                    && independent.mixture().gasMoles().isEmpty(), "unrelated network remains unchanged through recovery");
            level.setBlock(blockedKey.position(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(independentKey.position(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            helper.succeed();
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
