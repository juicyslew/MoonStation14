package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestTags({"gas-pipe-manifold"})
public final class GasPipeManifoldGameTests {
    private GasPipeManifoldGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 160, templateNamespace = "moonstation14")
    public static void manifoldIsOnePassiveThreeLayerVolume(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos local = new BlockPos(5, 1, 5);
        BlockPos pos = helper.absolutePos(local);
        level.setBlock(pos, ModBlocks.GAS_PIPE_MANIFOLD.get().defaultBlockState(), 3);
        var lease = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(15, 9, 15)), 0);
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 40,
                () -> level.getBlockEntity(pos) instanceof PipeGasPortHost,
                "manifold host entity-ticking", () -> {
        var host = level.getBlockEntity(pos);
        require(host instanceof PipeGasPortHost, "manifold installs a gas-port host");
        require(host instanceof AtmosMachineBlockEntity, "manifold is an atmosphere-machine host");
        AtmosMachineBlockEntity machine = (AtmosMachineBlockEntity) host;
        machine.reconcileLoadedPorts(level);
        PipeGasPortHost portsHost = machine;
        var ports = portsHost.gasPorts();
        require(ports.size() == 6, "manifold declares six ports");
        var cachedNetwork = LoadedPipeNetworks.network(level, ports.getFirst().key(pos)).orElseThrow(
                () -> new GameTestAssertException("normal network lookup resolves manifold after port admission"));
        var expectedKeys = ports.stream().map(port -> port.key(pos)).collect(java.util.stream.Collectors.toSet());
        require(cachedNetwork.members().equals(expectedKeys), "admitted manifold ports resolve as the complete six-port roster");
        for (var port : ports) {
            var network = LoadedPipeNetworks.network(level, port.key(pos)).orElseThrow(
                    () -> new GameTestAssertException("every admitted port has a ready network"));
            require(network.equals(cachedNetwork) && network.members().equals(expectedKeys),
                    "all six ports share the exact resolved network without an extra invalidation");
        }
        require(LoadedPipeNetworks.reconcile(level), "resolve manifold's complete six-port network");
        Map<PipeTopology.Layer, Integer> counts = new LinkedHashMap<>();
        for (var port : ports) {
            counts.merge(port.layer(), 1, Integer::sum);
            require(Double.compare(port.volume(), 0.05) == 0, "each manifold port is 0.05 m3");
            require(port.directions().stream().allMatch(d -> d == net.minecraft.core.Direction.NORTH
                    || d == net.minecraft.core.Direction.SOUTH), "manifold ports expose only north/south edges");
        }
        require(counts.equals(Map.of(PipeTopology.Layer.PRIMARY, 2, PipeTopology.Layer.SECONDARY, 2,
                PipeTopology.Layer.TERTIARY, 2)), "fixed two-port allocation on each layer");
        var discovered = LoadedPipeComponent.discover(level, ports.getFirst().key(pos)).orElseThrow(
                () -> new GameTestAssertException("manifold component is discoverable"));
        require(discovered.nodes().size() == 6 && Math.abs(discovered.totalVolume() - 0.3) < 1e-9,
                "all manifold layers join one six-port 0.3 m3 passive component");

        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var seedMixtures = new java.util.ArrayList<GasMixture>();
        var seed = new java.util.ArrayList<TrustedPipeFixtureInitialization.PortSeed>();
        for (int i = 0; i < ports.size(); i++) {
            var port = ports.get(i);
            if (i != 0 && i != 2) continue;
            var mixture = i == 0 ? new GasMixture(Map.of(GasType.OXYGEN, 3.0), 320.0, port.volume())
                    : new GasMixture(Map.of(GasType.NITROGEN, 1.0), 280.0, port.volume());
            seedMixtures.add(mixture);
            seed.add(new TrustedPipeFixtureInitialization.PortSeed(pos, port.id(), port.incarnation(), mixture));
        }
        var initialized = TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, seed);
        require(initialized.successful() && initialized.completedPorts() == 2,
                "trusted finite inventory initialization: " + initialized.failure() + "/" + initialized.detail());
        var work = LoadedPipeGasWork.capture(level, ports.stream().map(p -> new LoadedPipeGasWork.Port(p.key(pos))).toList())
                .orElseThrow(() -> new GameTestAssertException("manifold gas work captured"));
        var component = work.componentFor(ports.getFirst().key(pos)).orElseThrow();
        var aggregate = component.aggregate();
        require(Math.abs(aggregate.volumeCubicMeters()-0.3)<1e-6&&aggregate.moles(GasType.OXYGEN)==3.0
                        &&aggregate.moles(GasType.NITROGEN)==1.0,
                "shared six-port network contains each finite seed contribution exactly once");
        require(LoadedPipeGasWork.commit(level, work, Map.of(component.root(), aggregate), 0)
                        == LoadedPipeGasWork.Outcome.UNCHANGED, "passive manifold already has shared network state");
        var after = saved.snapshot();
        double oxygen = 0, nitrogen = 0, energy = 0;
        for (var port : ports) {
            var mix = after.nodeShare(port.key(pos));
            oxygen += mix.gasMoles().getOrDefault(GasType.OXYGEN, 0.0);
            nitrogen += mix.gasMoles().getOrDefault(GasType.NITROGEN, 0.0);
            energy += mix.thermalEnergy();
            require(Math.abs(mix.gasMoles().getOrDefault(GasType.OXYGEN, 0.0)-0.5)<1e-6
                    && Math.abs(mix.gasMoles().getOrDefault(GasType.NITROGEN, 0.0)-1.0/6.0)<1e-6,
                    "each physical 0.05 m3 port projects its one-sixth shared-network share");
        }
        require(PipeGasLedger.conservedSpecies(3.0, oxygen) && PipeGasLedger.conservedSpecies(1.0, nitrogen),
                "species amounts conserved");
        double initialEnergy = seedMixtures.stream().mapToDouble(GasMixture::thermalEnergy).sum();
        require(PipeGasLedger.conservedEnergy(initialEnergy, energy), "thermal energy conserved");

        helper.succeed();
        }, pos);
    }

    @GameTest(template = "empty", timeoutTicks = 180, templateNamespace = "moonstation14")
    public static void ordinaryMachineTickEqualizesManifold(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 1, 5));
        level.setBlock(pos, ModBlocks.GAS_PIPE_MANIFOLD.get().defaultBlockState(), 3);
        var lease = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(15, 9, 15)), 0);
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 40,
                () -> level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity,
                "manifold host entity-ticking", () -> {
        require(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity, "registered manifold machine host");
        AtmosMachineBlockEntity machine = (AtmosMachineBlockEntity) level.getBlockEntity(pos);
        machine.reconcileLoadedPorts(level);
        var ports = machine.gasPorts();
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        require(LoadedPipeNetworks.reconcile(level), "resolve manifold network before finite seed");
        var before = saved.snapshot();
            require(ports.size() == 6 && ports.stream().allMatch(p -> before.get(p.key(pos))
                .map(e -> e.active() && e.incarnation().equals(p.incarnation())).orElse(false))
                    && before.mixture(ports.getFirst().key(pos)).totalMoles()==0
                    && before.mixture(ports.getFirst().key(pos)).thermalEnergy()==0,
                "six exact matching-incarnation owners begin active with vacuum");
        var seeds = new java.util.ArrayList<TrustedPipeFixtureInitialization.PortSeed>();
        double initialEnergy;
        var oxygen = new GasMixture(Map.of(GasType.OXYGEN, 3.0), 320.0, ports.get(0).volume());
        var nitrogen = new GasMixture(Map.of(GasType.NITROGEN, 1.0), 280.0, ports.get(2).volume());
        seeds.add(new TrustedPipeFixtureInitialization.PortSeed(pos, ports.get(0).id(), ports.get(0).incarnation(), oxygen));
        seeds.add(new TrustedPipeFixtureInitialization.PortSeed(pos, ports.get(2).id(), ports.get(2).incarnation(), nitrogen));
        require(TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, seeds).successful(), "seed two finite ports");
        var seeded = saved.snapshot();
        var seededNetwork=seeded.mixture(ports.getFirst().key(pos));
        require(seededNetwork.gasMoles().equals(Map.of(GasType.OXYGEN,3.0,GasType.NITROGEN,1.0))
                        &&Math.abs(seededNetwork.volumeCubicMeters()-0.3)<1e-6,
                "two per-port seed contributions form one full-volume shared network inventory");
        initialEnergy = oxygen.thermalEnergy() + nitrogen.thermalEnergy();
        long interval = AtmosMachineProfile.DEFAULT_SIMULATION.intervalTicks();
        helper.runAfterDelay(Math.min(120, interval * 3), () -> {
            var after = saved.snapshot();
            double o2 = 0, n2 = 0, energy = 0;
            for (var port : ports) {
                var mixture = after.nodeShare(port.key(pos));
                o2 += mixture.gasMoles().getOrDefault(GasType.OXYGEN, 0.0);
                n2 += mixture.gasMoles().getOrDefault(GasType.NITROGEN, 0.0);
                energy += mixture.thermalEnergy();
                require(Math.abs(mixture.moles(GasType.OXYGEN)-0.5)<1e-6
                        && Math.abs(mixture.moles(GasType.NITROGEN)-1.0/6.0)<1e-6, "tick observes each port's projection of shared gas");
            }
            require(PipeGasLedger.conservedSpecies(3, o2) && PipeGasLedger.conservedSpecies(1, n2), "tick conserves total moles");
            require(PipeGasLedger.conservedEnergy(initialEnergy, energy), "tick conserves thermal energy");
            helper.succeed();
        });
        }, pos);
    }

    @GameTest(template = "empty", timeoutTicks = 160, templateNamespace = "moonstation14")
    public static void ordinaryVisualPipeDiscoversEachLaneSeparately(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 2, 8));
        var lease = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(15, 9, 15)), 0);
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 40,
                () -> level.hasChunkAt(center), "visual pipe chunk loaded", () -> {
        BlockPos east = center.east(), west = center.west(), north = center.north(), south = center.south();
        placeVisual(level, center, VisualPipeBlockEntity.PRIMARY);
        addFunctionalLane(level, center, VisualPipeBlockEntity.SECONDARY);
        addFunctionalLane(level, center, VisualPipeBlockEntity.TERTIARY);
        placeVisual(level, east, VisualPipeBlockEntity.PRIMARY);
        placeVisual(level, west, VisualPipeBlockEntity.PRIMARY);
        placeVisual(level, north, VisualPipeBlockEntity.SECONDARY);
        placeVisual(level, south, VisualPipeBlockEntity.TERTIARY);

        for (var layer : PipeTopology.Layer.values()) {
            int flag = switch (layer) {
                case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
                case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
                case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
            };
            var component = LoadedPipeComponent.discover(level, center, flag).orElseThrow(
                    () -> new GameTestAssertException("ordinary visual lane discovered: " + layer));
            int expectedSize = layer == PipeTopology.Layer.PRIMARY ? 3 : 2;
            require(component.nodes().size() == expectedSize && component.nodes().stream().allMatch(n -> n.key().layer() == layer),
                    "ordinary host keeps " + layer + " lane isolated without a manifold");
        }
        helper.succeed();
        }, center);
    }

    @GameTest(template = "empty", timeoutTicks = 160, templateNamespace = "moonstation14")
    public static void manifoldRemovalInvalidatesCapturedWorkAndRetiresOnlyItsPorts(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos unrelated = pos.east();
        level.setBlock(pos, ModBlocks.GAS_PIPE_MANIFOLD.get().defaultBlockState(), 3);
        level.setBlock(unrelated, ModBlocks.GAS_PIPE_MANIFOLD.get().defaultBlockState(), 3);
        var host = (PipeGasPortHost) level.getBlockEntity(pos);
        var otherHost = (PipeGasPortHost) level.getBlockEntity(unrelated);
        var lease = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(15, 9, 15)), 0);
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 40,
                () -> level.getBlockEntity(pos) instanceof PipeGasPortHost
                        && level.getBlockEntity(unrelated) instanceof PipeGasPortHost,
                "both manifold hosts entity-ticking", () -> {
        require(level.getBlockEntity(pos) == host && level.getBlockEntity(unrelated) == otherHost,
                "absolute manifold host probes remain present");
        ((AtmosMachineBlockEntity) host).reconcileLoadedPorts(level);
        ((AtmosMachineBlockEntity) otherHost).reconcileLoadedPorts(level);
        var ports = host.gasPorts();
        var otherPorts = otherHost.gasPorts();
        var saved = PipeGasInventorySavedData.existing(level).orElseThrow();
        var retirementSeeds = new java.util.ArrayList<TrustedPipeFixtureInitialization.PortSeed>();
        for (int i = 0; i < ports.size(); i++) {
            var port = ports.get(i);
            var gas = new GasMixture(Map.of(i % 2 == 0 ? GasType.OXYGEN : GasType.NITROGEN, 1.0 + i), 290.0 + i, port.volume());
            retirementSeeds.add(new TrustedPipeFixtureInitialization.PortSeed(pos, port.id(), port.incarnation(), gas));
        }
        require(TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, retirementSeeds).successful(), "seed all retiring ports with finite gas");
        var work = LoadedPipeGasWork.capture(level, ports.stream().map(p -> new LoadedPipeGasWork.Port(p.key(pos))).toList())
                .orElseThrow(() -> new GameTestAssertException("live manifold gas work captured"));
        var otherIdentities = otherPorts.stream().map(p -> p.key(unrelated)).toList();
        var component = work.componentFor(ports.getFirst().key(pos)).orElseThrow();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        require(LoadedPipeGasWork.commit(level, work, Map.of(component.root(), component.aggregate()), 0)
                == LoadedPipeGasWork.Outcome.STALE, "removed manifold rejects captured gas work");
        var after = saved.snapshot();
        require(ports.stream().allMatch(p -> after.get(p.key(pos)).isEmpty()), "all six removed owner entries retired");
        require(ports.stream().allMatch(p -> after.retiredSnapshot().containsKey(p.incarnation())), "finite owner incarnations archived");
        double seededEnergy = retirementSeeds.stream().mapToDouble(s -> s.mixture().thermalEnergy()).sum();
        double archivedEnergy = retirementSeeds.stream().mapToDouble(s -> after.retiredSnapshot().get(s.incarnation()).mixture().thermalEnergy()).sum();
        require(PipeGasLedger.conservedEnergy(seededEnergy, archivedEnergy), "retirement archive conserves finite thermal energy");
        require(otherIdentities.stream().allMatch(k -> after.get(k).map(PipeGasInventory.Entry::active).orElse(false)),
                "unrelated manifold lane entries remain active");
        helper.succeed();
        }, pos, unrelated);
    }

    @GameTest(template = "empty", timeoutTicks = 180, templateNamespace = "moonstation14")
    public static void externalConnectionMaskTracksNeighborsAndUpdateTags(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 1, 5));
        level.setBlock(pos, ModBlocks.GAS_PIPE_MANIFOLD.get().defaultBlockState(), 3);
        var lease = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(0, 0, 0)),
                helper.absolutePos(new BlockPos(15, 9, 15)), 0);
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 40,
                () -> level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity,
                "manifold host entity-ticking", () -> {
            var machine = (AtmosMachineBlockEntity) level.getBlockEntity(pos);
            machine.reconcileLoadedPorts(level);
            var ports = machine.gasPorts();
            require(ports.size() == 6 && ports.stream().allMatch(p -> p.volume() == 0.05),
                    "manifold source catalogue remains six 0.05 m3 ports");
            require(machine.manifoldAppearanceMask() == 0, "no initial external connections");
            BlockPos north = pos.north(), south = pos.south();
            placeVisual(level, north, VisualPipeBlockEntity.PRIMARY);
            addFunctionalLane(level, north, VisualPipeBlockEntity.SECONDARY);
            addFunctionalLane(level, north, VisualPipeBlockEntity.TERTIARY);
            placeVisual(level, south, VisualPipeBlockEntity.PRIMARY);
            addFunctionalLane(level, south, VisualPipeBlockEntity.SECONDARY);
            addFunctionalLane(level, south, VisualPipeBlockEntity.TERTIARY);
            helper.runAfterDelay(2, () -> {
                require(machine.manifoldAppearanceMask() == 63, "all six neighbor endpoints appear");
                assertMaskUpdateTag(machine, level, 63);
                level.setBlock(north, Blocks.AIR.defaultBlockState(), 3);
                helper.runAfterDelay(2, () -> {
                    require(machine.manifoldAppearanceMask() == 56, "removing north host clears only north bits");
                    assertMaskUpdateTag(machine, level, 56);
                    var connected = LoadedPipeComponent.discover(level, ports.getFirst().key(pos)).orElseThrow(
                            () -> new GameTestAssertException("manifold component remains discoverable"));
                    var expectedPortKeys = ports.stream().map(p -> p.key(pos)).toList();
                    var expectedPortKeySet = new LinkedHashSet<>(expectedPortKeys);
                    var connectedKeys = connected.nodes().stream().map(LoadedPipeComponent.Node::key)
                            .collect(java.util.stream.Collectors.toSet());
                    require(expectedPortKeys.size() == 6 && expectedPortKeySet.size() == 6
                                    && connectedKeys.containsAll(expectedPortKeySet),
                            "appearance endpoint removal does not split internal manifold network; expected six distinct host port keys "
                                    + expectedPortKeySet + ", actual component keys " + connectedKeys);
                    helper.succeed();
                });
            });
        }, pos);
    }

    private static void assertMaskUpdateTag(AtmosMachineBlockEntity machine, ServerLevel level, int expected) {
        var tag = machine.getUpdateTag(level.registryAccess());
        var replica = new AtmosMachineBlockEntity(machine.getBlockPos(), level.getBlockState(machine.getBlockPos()));
        replica.loadWithComponents(tag, level.registryAccess());
        require(replica.manifoldAppearanceMask() == expected, "update tag transports appearance mask " + expected);
    }

    private static void placeVisual(ServerLevel level, BlockPos pos, int layers) {
        level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity, "visual pipe placed at " + pos);
        var pipe = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(pipe.setLayersForPlacement(layers), "install visible functional layer");
        require(LoadedPipeGasOwner.createVacuumLane(level, pos, layers).isPresent(), "visual owner admitted");
        refreshConnections(level, pos);
    }

    private static void addFunctionalLane(ServerLevel level, BlockPos pos, int lane) {
        var pipe = (VisualPipeBlockEntity) level.getBlockEntity(pos);
        require(pipe.addFunctionalPlacementLayer(lane), "install visible functional lane");
        require(LoadedPipeGasOwner.createVacuumLane(level, pos, lane).isPresent(), "admit lane through production API");
        refreshConnections(level, pos);
    }

    private static void refreshConnections(ServerLevel level, BlockPos pos) {
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
