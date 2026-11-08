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
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestTags({"ventilation", "atmosphere", "atmos-pipe-retirement-boundary"})
@PrefixGameTestTemplate(false)
public final class SealedPipeNetworkGameTests {
    private SealedPipeNetworkGameTests() { }

@GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
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
        require(LoadedPipeNetworks.reconcile(level), "resolve sealed and exposed network topology before seeding");

        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        PipeTopology.NodeKey westKey = key(west, PipeTopology.Layer.SECONDARY);
        PipeGasInventory beforeSeed = saved.snapshot();
        PipeGasInventory.Entry westEntry = beforeSeed.get(westKey).orElseThrow();
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 6.0), 310.0, 0.6);
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

        double initialEnergy = saved.snapshot().mixture(westKey).thermalEnergy();
        var seededNetwork = saved.snapshot().network(westKey).orElseThrow();
        require(seededNetwork.members().equals(component.nodes().stream().map(node -> node.key()).collect(Collectors.toSet())),
                "projection expectation covers complete authoritative component");
        require(component.nodes().stream().allMatch(node -> Double.compare(
                        saved.snapshot().nodeShare(node.key()).moles(GasType.OXYGEN),
                        seededNetwork.mixture().moles(GasType.OXYGEN)
                                * saved.snapshot().get(node.key()).orElseThrow().volume()
                                / seededNetwork.mixture().volumeCubicMeters()) == 0),
                "resolved shared network projects canonical physical-volume shares to each node");
        for (BlockPos pos : new BlockPos[]{west, wall, east}) {
            var mixture = saved.snapshot().mixture(key(pos, PipeTopology.Layer.SECONDARY));
            require(Math.abs(mixture.gasMoles().getOrDefault(GasType.OXYGEN, 0.0) - 6.0) < 1e-12,
                    "each connected port resolves the full shared network inventory at " + pos);
        }
        double finalEnergy = saved.snapshot().mixture(westKey).thermalEnergy();
        require(Math.abs(finalEnergy - initialEnergy) <= Math.max(1e-9, Math.abs(initialEnergy) * 1e-12),
                "equalization conserves thermal energy");

        long oxygenVersion = saved.snapshot().get(key(west, PipeTopology.Layer.SECONDARY)).orElseThrow().version();
        var retiredWallKey = key(wall, PipeTopology.Layer.SECONDARY);
        var retiredWallEntry = saved.snapshot().get(retiredWallKey)
                .orElseThrow(() -> new GameTestAssertException("old sealed-wall incarnation exists before retirement"));
        var retiringNetwork = saved.snapshot().network(retiredWallKey).orElseThrow();
        var retirementMembers = retiringNetwork.members().stream().sorted(java.util.Comparator.comparing(member ->
                member.position().getX()+","+member.position().getY()+","+member.position().getZ()+","+member.layer().name()+","+member.portId())).toList();
        double[] retirementVolumes = retirementMembers.stream().mapToDouble(member -> saved.snapshot().get(member).orElseThrow().volume()).toArray();
        var retirementShares = PipeGasLedger.split(retiringNetwork.mixture(), retirementVolumes);
        int retiredIndex = retirementMembers.indexOf(retiredWallKey);
        var expectedRetiredShare = new GasMixture(retirementShares.get(retiredIndex).gasMoles(),
                retirementShares.get(retiredIndex).temperatureKelvin(), retiredWallEntry.volume());
        var survivorShares = new java.util.ArrayList<>(retirementShares);
        survivorShares.remove(retiredIndex);
        var mergedSurvivors = PipeGasLedger.merge(survivorShares);
        double survivorVolume = retirementMembers.stream().filter(member -> !member.equals(retiredWallKey))
                .mapToDouble(member -> saved.snapshot().get(member).orElseThrow().volume()).sum();
        var expectedSurvivor = new GasMixture(mergedSurvivors.gasMoles(), mergedSurvivors.temperatureKelvin(), survivorVolume);
        var eastKey = key(east, PipeTopology.Layer.SECONDARY);
        double westVolume = saved.snapshot().get(westKey).orElseThrow().volume();
        double eastVolume = saved.snapshot().get(eastKey).orElseThrow().volume();
        var remappedSurvivors = PipeGasLedger.split(expectedSurvivor, new double[]{westVolume, eastVolume});
        var retiredTertiaryKey = key(wall, PipeTopology.Layer.TERTIARY);
        var retiredTertiaryEntry = saved.snapshot().get(retiredTertiaryKey)
                .orElseThrow(() -> new GameTestAssertException("tertiary wall owner exists before retirement"));
        var northEntry = saved.snapshot().get(key(north, PipeTopology.Layer.TERTIARY))
                .orElseThrow(() -> new GameTestAssertException("north tertiary owner exists before retirement"));
        var southEntry = saved.snapshot().get(key(south, PipeTopology.Layer.TERTIARY))
                .orElseThrow(() -> new GameTestAssertException("south tertiary owner exists before retirement"));
        var northNetwork = saved.snapshot().network(northEntry.node()).orElseThrow();
        var southNetwork = saved.snapshot().network(southEntry.node()).orElseThrow();
        var untouchedGas=saved.snapshot();
        level.setBlock(wall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(wall, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        var afterReplacement = LoadedPipeComponent.discover(level, west, VisualPipeBlockEntity.SECONDARY)
                .orElseThrow(() -> new GameTestAssertException("west component remains discoverable after host replacement"));
        require(afterReplacement.nodes().size() == 1, "replacement wall immediately removes stale sealed edge");
        require(LoadedPipeNetworks.network(level, westKey).isPresent(), "normal west network read reconciles topology");
        var reconciledWest = saved.snapshot().mixture(westKey);
        var reconciledEast = LoadedPipeNetworks.network(level, eastKey).orElseThrow().mixture();
        require(sameStored(reconciledWest, remappedSurvivors.get(0))
                        && sameStored(reconciledEast, remappedSurvivors.get(1)),
                "normal network reads reconcile both survivors to ledger split results");
        var retainedWest = saved.snapshot().get(westKey).orElseThrow();
        var retainedEast = saved.snapshot().get(eastKey).orElseThrow();
        require(retainedWest.version() == oxygenVersion && retainedWest.incarnation().equals(
                        untouchedGas.get(westKey).orElseThrow().incarnation())
                        && retainedEast.incarnation().equals(untouchedGas.get(eastKey).orElseThrow().incarnation())
                        && retainedEast.version() == untouchedGas.get(eastKey).orElseThrow().version(),
                "surviving endpoint owner identities and versions remain unchanged");
        require(saved.snapshot().get(retiredWallKey).isEmpty(), "retired wall incarnation leaves active inventory");
        require(saved.snapshot().get(retiredTertiaryKey).isEmpty(), "replacement retires every wall-owned lane");
        var afterSnapshot = saved.snapshot();
        var northAfter = afterSnapshot.get(key(north, PipeTopology.Layer.TERTIARY))
                .orElseThrow(() -> new GameTestAssertException("north owner remains active after wall replacement"));
        var southAfter = afterSnapshot.get(key(south, PipeTopology.Layer.TERTIARY))
                .orElseThrow(() -> new GameTestAssertException("south owner remains active after wall replacement"));
        require(northAfter.incarnation().equals(northEntry.incarnation()) && northAfter.version() == northEntry.version()
                        && sameNetworkIdentity(afterSnapshot.network(northAfter.node()).orElseThrow(), northNetwork)
                        && afterSnapshot.mixture(northAfter.node()).equals(untouchedGas.mixture(northAfter.node())), "unrelated north owner is unchanged");
        require(southAfter.incarnation().equals(southEntry.incarnation()) && southAfter.version() == southEntry.version()
                        && sameNetworkIdentity(afterSnapshot.network(southAfter.node()).orElseThrow(), southNetwork)
                        && afterSnapshot.mixture(southAfter.node()).equals(untouchedGas.mixture(southAfter.node())), "unrelated south owner is unchanged");
        var archivedWall = saved.snapshot().retiredSnapshot().get(retiredWallEntry.incarnation());
        require(archivedWall != null && archivedWall.node().equals(retiredWallKey)
                        && Double.compare(archivedWall.volume(), retiredWallEntry.volume()) == 0
                        && sameStored(archivedWall.mixture(), expectedRetiredShare),
                "retired wall finite gas is conserved under exact incarnation using ledger split");
        require(afterSnapshot.retiredSnapshot().get(retiredTertiaryEntry.incarnation()) == null
                        && untouchedGas.mixture(retiredTertiaryKey).gasMoles().isEmpty()
                        && untouchedGas.mixture(retiredTertiaryKey).thermalEnergy() == 0.0,
                "empty tertiary wall owner retires without creating a vacuum archive");
        for (GasType gas : GasType.values())
            require(PipeGasLedger.conservedSpecies(retiringNetwork.mixture().moles(gas),
                            archivedWall.mixture().moles(gas) + reconciledWest.moles(gas) + reconciledEast.moles(gas)),
                    "retirement conserves " + gas);
        require(PipeGasLedger.conservedEnergy(retiringNetwork.mixture().thermalEnergy(),
                        archivedWall.mixture().thermalEnergy() + reconciledWest.thermalEnergy()
                                + reconciledEast.thermalEnergy()),
                "exact retirement archive and both remapped survivors conserve source network");
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

    private static boolean sameStored(GasMixture a, GasMixture b) {
        return a.gasMoles().equals(b.gasMoles())
                && Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) == 0
                && Double.compare(a.volumeCubicMeters(), b.volumeCubicMeters()) == 0;
    }

    private static boolean sameNetworkIdentity(PipeGasInventory.Network a, PipeGasInventory.Network b) {
        return a.id().equals(b.id()) && a.members().equals(b.members())
                && a.topologyGeneration() == b.topologyGeneration() && a.gasRevision() == b.gasRevision();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
