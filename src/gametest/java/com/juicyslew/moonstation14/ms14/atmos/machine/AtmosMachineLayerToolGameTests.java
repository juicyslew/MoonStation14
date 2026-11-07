package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.hands.live.AtmosLayerHandsFixture;
import com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/** Registered-player proof of the admitted screwdriver path and shared timed cancellation. */
@PrefixGameTestTemplate(false)
@GameTestTags({"atmos-machine-layer-tool"})
public final class AtmosMachineLayerToolGameTests {
    private AtmosMachineLayerToolGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void registeredScrewdriverCompletesTimedLayerRelocation(GameTestHelper h) {
        run(h, "layer-tool-complete", (fixture, body, owner, pos, be, inventory, snapshot, requestSeed) -> {
            require(AuthenticatedBlockUseDriver.use(fixture.player(), request(fixture, owner, body, pos, requestSeed, 1))
                    == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE, "admitted held tool dispatched");
            require(active(body), "shared TimedInteractionSystem owns active progress after admission");
            require(be.selectedPipeLayer() == PipeTopology.Layer.PRIMARY, "start does not immediately change layer");
            requireUnchangedAtHost(snapshot, inventory.snapshot(), pos, PipeTopology.Layer.SECONDARY);
            h.runAfterDelay(10, () -> {
                try {
                    require(be.selectedPipeLayer() == PipeTopology.Layer.PRIMARY, "no early layer change before resolved 20 ticks");
                    requireUnchangedAtHost(snapshot, inventory.snapshot(), pos, PipeTopology.Layer.SECONDARY);
                    h.runAfterDelay(15, () -> {
                        try {
                            require(be.selectedPipeLayer() == PipeTopology.Layer.SECONDARY, "shared timer completed layer change");
                            requireGasPreserved(snapshot, inventory.snapshot(), pos, PipeTopology.Layer.SECONDARY);
                            require(!active(body), "completed timed action is no longer active");
                            require(com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineMenuService.session(fixture.player(),pos)==null,
                                    "held-tool layer action did not admit a settings session");
                            h.succeed();
                        } catch (Throwable failure) { h.fail("Layer-tool timed completion: " + failure); }
                    });
                } catch (Throwable failure) { h.fail("Layer-tool pre-duration assertion: " + failure); }
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void registeredScrewdriverCancelsWhenActiveHandChanges(GameTestHelper h) {
        run(h, "layer-tool-cancel", (fixture, body, owner, pos, be, inventory, snapshot, requestSeed) -> {
            require(AuthenticatedBlockUseDriver.use(fixture.player(), request(fixture, owner, body, pos, requestSeed, 1))
                    == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE, "admitted held tool dispatched");
            require(active(body), "shared TimedInteractionSystem owns cancellable progress");
            require(AtmosLayerHandsFixture.replace(body, new net.minecraft.world.item.ItemStack(ModItems.WRENCH.get()),
                    "layer-cancel-replacement"), "real hand owner changed through trusted fixture seam");
            h.runAfterDelay(25, () -> {
                try {
                    require(be.selectedPipeLayer() == PipeTopology.Layer.PRIMARY, "changed held token cancels without host mutation");
                    requireUnchangedAtHost(snapshot, inventory.snapshot(), pos, PipeTopology.Layer.SECONDARY);
                    require(!active(body), "cancelled operation is no longer active");
                    h.succeed();
                } catch (Throwable failure) { h.fail("Layer-tool cancellation: " + failure); }
            });
        });
    }

    private static void run(GameTestHelper h, String name, Scenario scenario) {
        var fixture = RegisteredServerPlayerFixture.register(h.getLevel(), name);
        BlockPos local = new BlockPos(8, 2, 8), pos = h.absolutePos(local);
        var lease = FixtureChunkTickets.acquire(h, new net.minecraft.world.phys.AABB(pos.getX()-2,pos.getY()-1,pos.getZ()-3,pos.getX()+3,pos.getY()+5,pos.getZ()+2));
        AtomicBoolean closed = new AtomicBoolean();
        java.util.List<BlockPos> floors = new java.util.ArrayList<>();
        Runnable cleanup = () -> { if (closed.compareAndSet(false, true)) {
            if (h.getLevel().getBlockEntity(pos) != null) h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos floor : floors) if (h.getLevel().getBlockState(floor).is(Blocks.STONE)) h.getLevel().setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
            fixture.close(); try { lease.close(); } catch (Exception ignored) { }
        } };
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a, net.minecraft.gametest.framework.GameTestInfo b, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
        });
        FixtureChunkTickets.awaitEntityTicking(h, lease, 80, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                for (int x=-1;x<=1;x++) for (int z=-2;z<=0;z++) {
                    BlockPos floor = pos.offset(x,-1,z); floors.add(floor);
                    h.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(),3);
                }
                h.setBlock(local, ModBlocks.GAS_MIXER.get());
                h.runAfterDelay(2, () -> {
                    try {
                        var be = (AtmosMachineBlockEntity) h.getLevel().getBlockEntity(pos);
                        var body = owner.body(); body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);
                        be.reconcileLoadedPorts(h.getLevel());
                        var saved = PipeGasInventorySavedData.existing(h.getLevel()).orElseThrow();
                        var initial = saved.snapshot();
                        var seeds = be.gasPorts().stream().map(port -> {
                            var entry = initial.get(port.key(pos)).orElseThrow();
                            return new com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization.PortSeed(
                                    pos, port.id(), port.incarnation(), new GasMixture(
                                    java.util.Map.of(GasType.OXYGEN, 2.0, GasType.NITROGEN, 1.0), 293.15, entry.volume()));
                        }).toList();
                        var initialized = com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(h.getLevel(), seeds);
                        require(initialized.successful() && initialized.completedPorts() == be.gasPorts().size(), "one-time finite gas seed");
                        var seeded = saved.snapshot();
                        require(AtmosLayerHandsFixture.set(body, new net.minecraft.world.item.ItemStack(ModItems.SCREWDRIVER.get())), "screwdriver in actual LiveHands owner");
                        var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
                        String hand = query.hands().getFirst().id();
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, h.getLevel(), pos);
                        var req = new BodyBlockUseRequest(owner.epoch(), 1, query.revision(), hand,
                                query.hands().getFirst().token(), pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        scenario.run(fixture, body, owner, pos, be, saved, seeded, req);
                    } catch (Throwable failure) { cleanup.run(); h.fail("Layer-tool fixture setup: " + failure); }
                });
            } catch (Throwable failure) { cleanup.run(); h.fail("Layer-tool player readiness: " + failure); }
        }, pos);
    }

    private static BodyBlockUseRequest request(RegisteredServerPlayerFixture fixture,
            RegisteredPlayerReadyDriver.Active owner, net.minecraft.world.entity.LivingEntity body,
            BlockPos pos, BodyBlockUseRequest seed, long sequence) {
        var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), sequence + 2));
        var hit = RegisteredPlayerReadyDriver.aimAt(body, (net.minecraft.server.level.ServerLevel) body.level(), pos);
        return new BodyBlockUseRequest(owner.epoch(), sequence, query.revision(), query.hands().getFirst().id(),
                query.hands().getFirst().token(), pos, hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
    }

    private static boolean active(net.minecraft.world.entity.LivingEntity body) {
        var state = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        return state != null && state.operations().stream().anyMatch(op -> op.phase() == TimedOperation.Phase.ACTIVE);
    }

    private static void requireGasPreserved(PipeGasInventory before, PipeGasInventory after, BlockPos pos, PipeTopology.Layer layer) {
        for (var old : hostEntries(before, pos, PipeTopology.Layer.PRIMARY)) {
            var destination = new PipeTopology.NodeKey(pos, layer, old.node().portId());
            require(after.get(old.node()).isEmpty(), "old host key retired: " + old.node());
            var moved = after.get(destination).orElseThrow();
            var oldGas = before.nodeShare(old.node());
            var movedGas = after.nodeShare(destination);
            require(moved.incarnation().equals(old.incarnation()) && Double.compare(moved.volume(), old.volume()) == 0
                    && movedGas.gasMoles().equals(oldGas.gasMoles())
                    && Double.compare(movedGas.thermalEnergy(), oldGas.thermalEnergy()) == 0
                    && moved.version() == old.version() + 1, "same UUID/volume/species/energy and one local version advance at " + destination);
        }
        require(hostEntries(before, pos, PipeTopology.Layer.PRIMARY).size() == 3, "all three mixer ports relocated");
    }

    private static void requireUnchangedAtHost(PipeGasInventory before, PipeGasInventory after, BlockPos pos,
                                                PipeTopology.Layer destinationLayer) {
        for (var original : hostEntries(before, pos, PipeTopology.Layer.PRIMARY)) {
            var current = after.get(original.node()).orElse(null);
            require(current != null && sameEntry(original, current),
                    "original host key/version unchanged: " + original.node());
            var beforeGas = before.nodeShare(original.node());
            var afterGas = after.nodeShare(original.node());
            require(beforeGas.gasMoles().equals(afterGas.gasMoles())
                            && Double.compare(beforeGas.thermalEnergy(), afterGas.thermalEnergy()) == 0,
                    "original host gas/energy unchanged: " + original.node());
        }
        require(hostEntries(after, pos, destinationLayer).isEmpty(), "no destination-layer host keys before commit/cancel");
    }

    private static java.util.List<PipeGasInventory.Entry> hostEntries(PipeGasInventory inventory, BlockPos pos,
                                                                       PipeTopology.Layer layer) {
        return inventory.snapshot().values().stream().filter(entry -> entry.node().position().equals(pos)
                && entry.node().layer() == layer).toList();
    }

    private static boolean sameEntry(PipeGasInventory.Entry left, PipeGasInventory.Entry right) {
        return left.node().equals(right.node()) && left.incarnation().equals(right.incarnation())
                && Double.compare(left.volume(), right.volume()) == 0 && left.version() == right.version()
                && left.active() == right.active();
    }
    @FunctionalInterface private interface Scenario {
        void run(RegisteredServerPlayerFixture fixture, net.minecraft.world.entity.LivingEntity body,
                 RegisteredPlayerReadyDriver.Active owner, BlockPos pos, AtmosMachineBlockEntity be,
                 PipeGasInventorySavedData inventory, PipeGasInventory snapshot, BodyBlockUseRequest request);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
