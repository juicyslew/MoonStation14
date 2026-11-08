package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.AtmosLayerHandsFixture;
import com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native registered-player proof of ordinary authenticated manifold block-item placement. */
@PrefixGameTestTemplate(false)
@GameTestTags({"atmos-machine-layer-tool", "interaction"})
public final class GasPipeManifoldPlacementGameTests {
    private GasPipeManifoldPlacementGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void registeredBodyPlacesManifold(GameTestHelper h) {
        var fixture = RegisteredServerPlayerFixture.register(h.getLevel(), "gas-pipe-manifold-placement");
        // atmos_large_empty.nbt is [36,5,8]; keep the complete fixture inside z=0..7.
        final int templateX = 36, templateY = 5, templateZ = 8;
        BlockPos localHost = new BlockPos(8, 2, 4);
        if (localHost.getX() < 0 || localHost.getX() >= templateX
                || localHost.getY() < 0 || localHost.getY() >= templateY
                || localHost.getZ() < 0 || localHost.getZ() >= templateZ)
            throw new GameTestAssertException("Manifold host outside atmos_large_empty bounds [36,5,8]: " + localHost);
        BlockPos host = h.absolutePos(localHost);
        var lease = FixtureChunkTickets.acquire(h, new net.minecraft.world.phys.AABB(host.getX()-2,host.getY()-1,host.getZ()-3,host.getX()+3,host.getY()+5,host.getZ()+2));
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> { if (closed.compareAndSet(false, true)) {
            if (h.getLevel().getBlockEntity(host) != null) h.getLevel().setBlock(host, Blocks.AIR.defaultBlockState(), 3);
            fixture.close(); try { lease.close(); } catch (Exception ignored) { }
        }};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a, net.minecraft.gametest.framework.GameTestInfo b, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
        });
        FixtureChunkTickets.awaitEntityTicking(h, lease, 80, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                BlockPos floor = h.absolutePos(localHost.below());
                // Own and clear only the in-bounds ray/placement corridor; neighboring template
                // structures may occupy other cells and are deliberately left untouched.
                for (int x = 7; x <= 9; x++) for (int y = 2; y <= 4; y++) for (int z = 2; z <= 5; z++)
                    h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 3);
                h.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                var manifold = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "gas_pipe_manifold"));
                if (manifold == net.minecraft.world.item.Items.AIR) manifold = ModBlocks.GAS_PIPE_MANIFOLD.get().asItem();
                ItemStack stack = new ItemStack(manifold);
                h.runAfterDelay(2, () -> {
                    try {
                        var body = owner.body();
                        body.setPos(host.getX()+.5,host.getY(),host.getZ()-1.5);
                        if (!AtmosLayerHandsFixture.set(body, stack)) throw new GameTestAssertException("manifold enters actual LiveHands owner");
                        var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
                        var hand = query.hands().getFirst();
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, h.getLevel(), floor, net.minecraft.core.Direction.UP);
                        var targetState = h.getLevel().getBlockState(host);
                        if (!targetState.canBeReplaced() || h.getLevel().getBlockEntity(host) != null
                                || !h.getLevel().getFluidState(host).isEmpty())
                            throw new GameTestAssertException("Expected clear replaceable manifold host; target=" + targetState
                                    + ", floor=" + h.getLevel().getBlockState(floor) + ", body=" + body.position()
                                    + ", eye=" + body.getEyePosition() + ", hitFace=" + hit.getDirection()
                                    + ", hit=" + hit.getBlockPos());
                        if (!hit.getBlockPos().equals(floor) || hit.getDirection() != net.minecraft.core.Direction.UP
                                || !floor.above().equals(host))
                            throw new GameTestAssertException("manifold placement must click floor top face to place at host; target="
                                    + targetState + ", floor=" + h.getLevel().getBlockState(floor) + ", body=" + body.position()
                                    + ", eye=" + body.getEyePosition() + ", hitFace=" + hit.getDirection()
                                    + ", hit=" + hit.getBlockPos());
                        var request = new BodyBlockUseRequest(owner.epoch(), 1, query.revision(), hand.id(), hand.token(), floor,
                                hit.getDirection(), false, BodyBlockUseRequest.Intent.USE);
                        var result = AuthenticatedBlockUseDriver.use(fixture.player(), request);
                        if (result != com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED)
                            throw new GameTestAssertException("ordinary authenticated body-hand USE places manifold: " + result);
                        h.runAfterDelay(2, () -> {
                            try {
                                var placed = h.getLevel().getBlockState(host);
                                 var be = h.getLevel().getBlockEntity(host);
                                 if (!placed.is(ModBlocks.GAS_PIPE_MANIFOLD.get()) || !(be instanceof AtmosMachineBlockEntity machine))
                                     throw new GameTestAssertException("authenticated placement produced the actual manifold host");
                                 machine.reconcileLoadedPorts((net.minecraft.server.level.ServerLevel) h.getLevel());
                                 if (machine.gasPorts().size() != 6)
                                     throw new GameTestAssertException("actual registered manifold block entity resolves six fixed ports");
                                  var layers = machine.gasPorts().stream().map(com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost.Port::layer).toList();
                                 if (layers.stream().filter(l -> l == com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY).count() != 2
                                         || layers.stream().filter(l -> l == com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY).count() != 2
                                         || layers.stream().filter(l -> l == com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.TERTIARY).count() != 2)
                                     throw new GameTestAssertException("resolved host has fixed two-port descriptors on all three layers");
                                h.succeed();
                            } catch (Throwable failure) { cleanup.run(); h.fail("Manifold placement postcondition: " + failure); }
                        });
                    } catch (Throwable failure) { cleanup.run(); h.fail("Manifold authenticated placement: " + failure); }
                });
            } catch (Throwable failure) { cleanup.run(); h.fail("Manifold player readiness: " + failure); }
        }, host);
    }
}
