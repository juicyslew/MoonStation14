package com.juicyslew.moonstation14.gametest.power.runtime;

import com.mojang.authlib.GameProfile;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerLiveLoop;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestTags({"power"})
@PrefixGameTestTemplate(false)
public final class ApcOverloadGameTests {
    private ApcOverloadGameTests() { }

    @GameTestTags({"suite-stabilization", "repair-debug-overload"})
    @GameTest(template = "power_receiver_tall_clear", timeoutTicks = 2600, templateNamespace = "moonstation14", batch = "apc_debug_overload")
    public static void twoWiredDebugLampsTripAndGoDarkThenReclose(GameTestHelper helper) {
        DebugFixture initialFixture = debugFixture(helper, false);
        initialFixture.apc().setEnergyJoules(0);
        DebugFixture fixture = addSecondNativeSourceToDebugFixture(helper, initialFixture);
        assertDebugFixtureInsideBounds(helper, fixture);
        // A ticket being requested is not proof that the graph's candidate-neighbor
        // chunks are entity ticking. In particular, lease/wait the north halo where
        // the HV graph probe can otherwise report an unloaded potential neighbor.
        FixtureChunkTickets.Lease fixtureChunks = FixtureChunkTickets.acquire(helper, helper.getBounds().inflate(1));
        BlockPos northHalo = new BlockPos(net.minecraft.util.Mth.floor(helper.getBounds().minX),
                helper.absolutePos(BlockPos.ZERO).getY() + 1,
                net.minecraft.util.Mth.floor(helper.getBounds().minZ) - 1);
        FixtureChunkTickets.awaitEntityTicking(helper, fixtureChunks, 100, () -> {
        fixture.lit(0); // Validate the authored first load before adding the second real load.
        double totalDebugDemand = 2 * PowerDeviceKind.DEBUG_LOAD_LAMP.lampDemandWatts();
        double ratedSubstationOutput = 30_000 * .9;
        require(Math.min(ratedSubstationOutput, fixture.sources().size() * PowerRuntime.SOURCE_WATTS * .9)
                        >= totalDebugDemand,
                "native source composition continuously supports both full debug loads without battery discharge: "
                        + fixture.sources().size() + " sources * " + PowerRuntime.SOURCE_WATTS
                         + " W, substation-rated output=" + ratedSubstationOutput + " W, demand=" + totalDebugDemand + " W");
        awaitDebugFixture(helper, fixture, () -> {
            requireConnectedDebugFixture(fixture);
            require(fixture.apc().breakerClosed() && !fixture.apc().tripLatched() && fixture.lit(0),
                    "one known 12 kW fixture load is powered before adding overload demand: " + debugState(fixture));
            placeDevice(fixture.level(), fixture.lamps().get(1), PowerDeviceKind.DEBUG_LOAD_LAMP, Direction.NORTH);
            requireDebugLampsPresent(fixture, "second native debug lamp placement");
            awaitFullDebugDelivery(helper, fixture, () -> {
                requireConnectedDebugFixture(fixture);
                require(fixture.apc().breakerClosed() && !fixture.apc().tripLatched(),
                        "fresh known graph admits the two-lamp 24 kW overload interval: " + debugState(fixture));
                long fullDeliveryGameTime = fixture.level().getGameTime();
                long fullDeliveryHelperTick = helper.getTick();
                double batteryBeforeOverloadWindow = fixture.apc().energyJoules();
                awaitOverloadTrip(helper, fixture, fullDeliveryGameTime, fullDeliveryHelperTick,
                        batteryBeforeOverloadWindow, () -> {
                require(fixture.apc().energyJoules() >= batteryBeforeOverloadWindow,
                        "fully delivered two-lamp interval did not consume APC battery support: before="
                                + batteryBeforeOverloadWindow + " after=" + fixture.apc().energyJoules()
                                + " " + debugState(fixture));
                require(bothDebugLampsDark(fixture), "post-trip solve darkens both lamps: " + debugState(fixture));
                Player player = new Player(fixture.level(), fixture.apcPos(), 0,
                        new GameProfile(UUID.randomUUID(), "debug-load-reclose")) {
                    @Override public boolean isCreative() { return true; }
                    @Override public boolean isSpectator() { return false; }
                };
                player.getAbilities().instabuild = true;
                require(fixture.apc().toggleBreaker(player, 0), "authorized reclose succeeds");
                awaitFullDebugDelivery(helper, fixture, () -> {
                    require(fixture.apc().breakerClosed() && !fixture.apc().tripLatched(),
                            "reclose restores APC supply: " + debugState(fixture));
                    helper.succeed();
                });
                });
            });
        });
        }, northHalo,
                helper.absolutePos(new BlockPos(4, 1, 1)),
                helper.absolutePos(new BlockPos(4, 1, 2)),
                helper.absolutePos(new BlockPos(4, 1, 3)),
                helper.absolutePos(new BlockPos(4, 1, 4)));
    }

    private static void awaitFullDebugDelivery(GameTestHelper helper, DebugFixture fixture, Runnable ready) {
        PowerFixtureReadiness.await(helper, fixture.level(), 600,
                () -> debugFixturePortsKnown(fixture)
                        && PowerRuntime.apcVisualState(fixture.level(), fixture.apc())
                        != com.juicyslew.moonstation14.ms14.power.ui.ApcVisualState.UNKNOWN
                        && bothDebugLampsFullyLit(fixture),
                () -> "awaiting known full 24 kW lamp delivery, " + debugState(fixture), ready);
    }

    private static boolean bothDebugLampsFullyLit(DebugFixture fixture) {
        for (int i = 0; i < 2; i++) {
            var state = fixture.level().getBlockState(fixture.lamps().get(i));
            if (!(state.getBlock() instanceof PowerDeviceBlock block)
                    || block.kind() != PowerDeviceKind.DEBUG_LOAD_LAMP
                    || !state.getValue(PowerDeviceBlock.LIT)) return false;
        }
        return true;
    }

    private static boolean bothDebugLampsDark(DebugFixture fixture) {
        for (int i = 0; i < 2; i++) {
            var state = fixture.level().getBlockState(fixture.lamps().get(i));
            if (!(state.getBlock() instanceof PowerDeviceBlock block)
                    || block.kind() != PowerDeviceKind.DEBUG_LOAD_LAMP
                    || state.getValue(PowerDeviceBlock.LIT)) return false;
        }
        return true;
    }

    /** Once a known full-delivery sample has been observed, await the actual protection outcome boundedly. */
    private static void awaitOverloadTrip(GameTestHelper helper, DebugFixture fixture, long fullGameTime,
                                          long fullHelperTick, double batteryBefore, Runnable tripped) {
        if (!fixture.apc().breakerClosed() && fixture.apc().tripLatched() && bothDebugLampsDark(fixture)) {
            tripped.run();
            return;
        }
        long elapsedHelperTicks = helper.getTick() - fullHelperTick;
        long elapsedLevelTicks = fixture.level().getGameTime() - fullGameTime;
        if (elapsedHelperTicks >= 600 || elapsedLevelTicks >= 600)
            throw new GameTestAssertException("real delivered 24 kW fixture did not trip before bounded actual-outcome deadline; "
                    + "fullDeliveryGameTime=" + fullGameTime + " currentGameTime=" + fixture.level().getGameTime()
                    + " elapsedHelperTicks=" + elapsedHelperTicks + "/600 elapsedLevelTicks=" + elapsedLevelTicks
                    + "/600 batteryBefore=" + batteryBefore + " batteryNow=" + fixture.apc().energyJoules()
                    + " state=" + debugState(fixture));
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> awaitOverloadTrip(helper, fixture, fullGameTime, fullHelperTick,
                batteryBefore, tripped));
    }

    private static void awaitDebugFixture(GameTestHelper helper, DebugFixture fixture, Runnable ready) {
        PowerFixtureReadiness.await(helper, fixture.level(), 600,
                () -> debugFixtureFrontierChunksFull(helper, fixture)
                        && debugFixturePortsKnown(fixture)
                        && PowerRuntime.apcVisualState(fixture.level(), fixture.apc())
                        != com.juicyslew.moonstation14.ms14.power.ui.ApcVisualState.UNKNOWN
                        && fixture.lit(0),
                () -> debugState(fixture) + ", topologyReady=" + PowerGraphService.topologyReady(fixture.level()), ready);
    }

    /** The complete one-block candidate-edge halo must be loaded before graph knownness is authoritative. */
    private static boolean debugFixtureFrontierChunksFull(GameTestHelper helper, DebugFixture fixture) {
        // The authored fixture footprint is relative x=2..5,z=2..7. Its one-block
        // candidate-host halo is x=1..6,z=1..8; check every covered chunk, including
        // seams at either end, without loading chunks from this readiness predicate.
        BlockPos first = helper.absolutePos(new BlockPos(1, 0, 1));
        BlockPos last = helper.absolutePos(new BlockPos(6, 1, 8));
        int minChunkX = Math.min(first.getX(), last.getX()) >> 4;
        int maxChunkX = Math.max(first.getX(), last.getX()) >> 4;
        int minChunkZ = Math.min(first.getZ(), last.getZ()) >> 4;
        int maxChunkZ = Math.max(first.getZ(), last.getZ()) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                var chunk = fixture.level().getChunkSource().getChunkNow(chunkX, chunkZ);
                // In this MC API getChunkNow returns only loaded LevelChunks (FULL), or null.
                if (chunk == null) return false;
            }
        return true;
    }

    private static boolean debugFixturePortsKnown(DebugFixture fixture) {
        var level = fixture.level();
        var ports = new java.util.ArrayList<CableFaceNode>(fixture.sources());
        ports.addAll(List.of(fixture.subHvPort(), fixture.subMvPort(), fixture.apcMvPort(), fixture.output()));
        for (BlockPos lamp : fixture.lamps()) {
            if (!(level.getBlockState(lamp).getBlock() instanceof PowerDeviceBlock block)
                    || block.kind() != PowerDeviceKind.DEBUG_LOAD_LAMP) continue;
            CableFaceNode receiver = PowerGraphService.nearestLampNode(level, lamp);
            if (receiver == null) return false;
            ports.add(receiver);
        }
        return PowerFixtureReadiness.topologyReadyWithKnownPorts(level, ports.toArray(CableFaceNode[]::new));
    }

    @GameTest(template = "power_receiver_tall_clear", timeoutTicks = 200, templateNamespace = "moonstation14")
    public static void oneWiredDebugLampStaysBelowTripThreshold(GameTestHelper helper) {
        DebugFixture fixture = debugFixture(helper, false);
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 100,
                    () -> "debug fixture graph readiness timed out: " + debugState(fixture),
                    () -> debugFixtureReady(fixture), () -> {
                        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 25, () -> {
                            require(fixture.lit(0), "single 12 kW lamp is powered: " + debugState(fixture));
                            require(fixture.apc().breakerClosed() && !fixture.apc().tripLatched(),
                                    "single lamp stays below 20 kW for more than three seconds: " + debugState(fixture));
                            helper.succeed();
                        });
                    });
    }

    private record DebugFixture(ServerLevel level, BlockPos apcPos, PowerDeviceBlockEntity apc,
                                List<CableFaceNode> sources, CableFaceNode subHvPort, CableFaceNode subMvPort,
                                CableFaceNode apcMvPort, CableFaceNode output, List<BlockPos> lamps) {
        boolean lit(int index) {
            var state = level.getBlockState(lamps.get(index));
            require(state.getBlock() instanceof PowerDeviceBlock block
                            && block.kind() == PowerDeviceKind.DEBUG_LOAD_LAMP,
                    "expected authored debug lamp at " + lamps.get(index) + " but found " + state);
            return state.getValue(PowerDeviceBlock.LIT);
        }
    }

    private static boolean debugFixtureReady(DebugFixture fixture) {
        var source = PowerGraphService.state(fixture.level(), fixture.sources().get(0));
        var subHv = PowerGraphService.state(fixture.level(), fixture.subHvPort());
        var subMv = PowerGraphService.state(fixture.level(), fixture.subMvPort());
        var apcMv = PowerGraphService.state(fixture.level(), fixture.apcMvPort());
        var output = PowerGraphService.state(fixture.level(), fixture.output());
        CableFaceNode receiver = PowerGraphService.nearestLampNode(fixture.level(), fixture.lamps().get(0));
        var lamp = receiver == null ? null : PowerGraphService.state(fixture.level(), receiver);
        return source.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                        && subHv.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                        && subMv.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                        && apcMv.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                        && output.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                        && lamp != null && lamp.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                        && source.componentId() == subHv.componentId()
                        && subMv.componentId() == apcMv.componentId()
                        && output.componentId() == lamp.componentId();
    }

    private static void requireConnectedDebugFixture(DebugFixture fixture) {
        require(debugFixtureReady(fixture),
                "debug fixture source -> HV -> substation -> MV -> APC -> lamp graph: " + debugState(fixture));
    }

    private static String debugState(DebugFixture fixture) {
        var level = fixture.level();
        CableFaceNode receiver = PowerGraphService.nearestLampNode(level, fixture.lamps().get(0));
        return "src=" + fixture.sources().stream().map(port -> shortState(PowerGraphService.diagnostics(level, port))).toList()
                + ",subHV=" + shortState(PowerGraphService.diagnostics(level, fixture.subHvPort()))
                + ",subMV=" + PowerGraphService.state(level, fixture.subMvPort())
                + ",APCMV=" + PowerGraphService.state(level, fixture.apcMvPort())
                + ",out=" + PowerGraphService.state(level, fixture.output())
                + ",receiver=" + (receiver == null ? "none" : PowerGraphService.state(level, receiver))
                + ",missingReal=" + loadedChunk(level, fixture.sources().get(0).host()) + "/"
                + loadedChunk(level, fixture.subHvPort().host()) + "/" + loadedChunk(level, fixture.lamps().get(0))
                + ",apc=" + fixture.apc().breakerClosed() + "/" + fixture.apc().tripLatched()
                + ",lamps=" + lampDiagnostic(fixture, 0) + "/" + lampDiagnostic(fixture, 1)
                + ",pending=" + PowerGraphService.diagnosticSnapshot(level);
    }

    private static String shortState(LoadedPowerGraph.NodeDiagnostics d) {
        return d.state().knowledge() + "/" + d.state().componentId() + "[rebuild=" + d.rebuilding()
                + ",indexed=" + d.nodeIndexed() + ",missing=" + d.unloadedPotentialNeighborChunks() + "]";
    }

    private static String loadedChunk(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? "not-loaded" : "FULL:" + chunk.getPos();
    }

    /** Compact version of the established source -> HV -> substation -> MV -> APC -> floor-cable path. */
    private static DebugFixture debugFixture(GameTestHelper helper, boolean twoLamps) {
        // Topology discovery probes one block beyond cable hosts; keep that candidate-neighbor halo resident too.
        var ticket = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(2, 0, 2)),
                helper.absolutePos(new BlockPos(5, 1, 7)), 1);
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = debugPos(helper, 3, 1, 2);
        BlockPos sub = debugPos(helper, 3, 1, 4);
        BlockPos apc = debugPos(helper, 3, 1, 6);
        for (int z = 2; z <= 4; z++) {
            BlockPos host = debugPos(helper, 4, 1, z);
            level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
            require(CableStorage.place(level, host, Direction.WEST, CableTier.HV), "HV cable " + z);
        }
        for (int z = 4; z <= 6; z++) {
            BlockPos host = debugPos(helper, 2, 1, z);
            level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
            require(CableStorage.place(level, host, Direction.EAST, CableTier.MV), "MV cable " + z);
        }
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, sub, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        BlockPos outputHost = debugPos(helper, 4, 1, 6);
        level.setBlock(outputHost, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, outputHost, Direction.WEST, CableTier.APC), "APC output lead");
        for (int x = 3; x <= 5; x++) for (int z = 6; z <= 7; z++) {
            BlockPos host = debugPos(helper, x, 0, z);
            level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
            require(CableStorage.place(level, host, Direction.UP, CableTier.APC), "floor LV cable " + host);
        }
        BlockPos first = debugPos(helper, 4, 1, 7);
        BlockPos second = debugPos(helper, 5, 1, 7);
        placeDevice(level, first, PowerDeviceKind.DEBUG_LOAD_LAMP, Direction.NORTH);
        if (twoLamps) placeDevice(level, second, PowerDeviceKind.DEBUG_LOAD_LAMP, Direction.NORTH);
        else require(level.getBlockState(second).isAir(), "single-lamp fixture has no second load");
        PowerDeviceBlockEntity entity = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
        entity.setEnergyJoules(100_000);
        return new DebugFixture(level, apc, entity,
                List.of(new CableFaceNode(debugPos(helper, 4, 1, 2), Direction.WEST, CableTier.HV)),
                new CableFaceNode(debugPos(helper, 4, 1, 4), Direction.WEST, CableTier.HV),
                new CableFaceNode(debugPos(helper, 2, 1, 4), Direction.EAST, CableTier.MV),
                new CableFaceNode(debugPos(helper, 2, 1, 6), Direction.EAST, CableTier.MV),
                new CableFaceNode(outputHost, Direction.WEST, CableTier.APC), List.of(first, second));
    }

    /** Adds a second ordinary source on the same already-connected HV cable spine. */
    private static DebugFixture addSecondNativeSourceToDebugFixture(GameTestHelper helper, DebugFixture fixture) {
        BlockPos source = debugPos(helper, 3, 1, 3);
        placeDevice(fixture.level(), source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        var sources = new java.util.ArrayList<>(fixture.sources());
        sources.add(new CableFaceNode(debugPos(helper, 4, 1, 3), Direction.WEST, CableTier.HV));
        return new DebugFixture(fixture.level(), fixture.apcPos(), fixture.apc(), List.copyOf(sources),
                fixture.subHvPort(), fixture.subMvPort(), fixture.apcMvPort(), fixture.output(), fixture.lamps());
    }

    private static void requireDebugLampsPresent(DebugFixture fixture, String phase) {
        for (BlockPos pos : fixture.lamps()) {
            var state = fixture.level().getBlockState(pos);
            require(state.getBlock() instanceof PowerDeviceBlock block
                            && block.kind() == PowerDeviceKind.DEBUG_LOAD_LAMP,
                    phase + " expected debug lamp at " + pos + " state=" + state + " bounds diagnostics="
                            + fixture.level().getBlockState(fixture.apcPos()));
        }
    }

    private static String lampDiagnostic(DebugFixture fixture, int index) {
        BlockPos pos = fixture.lamps().get(index);
        var state = fixture.level().getBlockState(pos);
        if (state.getBlock() instanceof PowerDeviceBlock block
                && block.kind() == PowerDeviceKind.DEBUG_LOAD_LAMP)
            return pos + ":lit=" + state.getValue(PowerDeviceBlock.LIT);
        return pos + ":not-present=" + state;
    }

    private static void assertDebugFixtureInsideBounds(GameTestHelper helper, DebugFixture fixture) {
        var bounds = helper.getBounds();
        var positions = new java.util.ArrayList<BlockPos>(fixture.lamps());
        positions.add(fixture.apcPos());
        positions.add(debugPos(helper, 3, 1, 2));
        positions.add(debugPos(helper, 3, 1, 3));
        positions.add(debugPos(helper, 3, 1, 4));
        positions.add(debugPos(helper, 3, 1, 6));
        positions.add(fixture.subHvPort().host());
        positions.add(fixture.subMvPort().host());
        positions.add(fixture.apcMvPort().host());
        positions.add(fixture.output().host());
        for (int z = 2; z <= 4; z++) positions.add(debugPos(helper, 4, 1, z));
        for (int z = 4; z <= 6; z++) positions.add(debugPos(helper, 2, 1, z));
        for (int x = 3; x <= 5; x++) for (int z = 6; z <= 7; z++)
            positions.add(debugPos(helper, x, 0, z));
        for (BlockPos pos : positions) {
            var center = new net.minecraft.world.phys.Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            require(bounds.contains(center), "debug overload fixture coordinate outside declared template bounds: pos="
                    + pos + " relative=" + pos.subtract(helper.absolutePos(BlockPos.ZERO)) + " bounds=" + bounds);
        }
    }

    /** All fixture-local y coordinates are raised one block to keep supports inside the declared 12x4x12 volume. */
    private static BlockPos debugPos(GameTestHelper helper, int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y + 1, z));
    }

    /** Transition guard proof using synthetic meter observations, independent of the wire fixture. */
    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void breakerTransitionsIgnoreRejectedActionsAndRepeatedTripSamples(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        require(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level, pos, Direction.NORTH), "place supported APC");
        PowerDeviceBlockEntity apc = (PowerDeviceBlockEntity) level.getBlockEntity(pos);
        Player player = new Player(level, pos, 0, new GameProfile(UUID.randomUUID(), "switch-test")) {
            @Override public boolean isCreative() { return true; }
            @Override public boolean isSpectator() { return false; }
        };
        player.getAbilities().instabuild = true;

        long initialRevision = apc.breakerRevision();
        require(!apc.toggleBreaker(player, 100_000), "out-of-range action is rejected");
        apc.setEnergyJoules(10_000);
        apc.observeActualApcOutput(1, 1, false, 30_000);
        require(apc.breakerClosed() && apc.breakerRevision() == initialRevision,
                "rejected action, energy update and unknown sample do not change the breaker");

        for (long tick = 2; tick <= 62; tick++)
            apc.observeActualApcOutput(tick, tick, true, 30_000);
        require(!apc.breakerClosed() && apc.breakerRevision() == initialRevision + 1,
                "timed trip changes breaker exactly once");
        for (long tick = 63; tick <= 125; tick++)
            apc.observeActualApcOutput(tick, tick, true, 30_000);
        require(apc.breakerRevision() == initialRevision + 1,
                "repeated trip samples while open do not change breaker again");
        require(apc.toggleBreaker(player, 0) && apc.breakerRevision() == initialRevision + 2,
                "accepted manual reclose changes breaker once");
        helper.succeed();
    }

    /** Protection proof uses deliberately synthetic BE meter observations, not a wire-path overload fixture. */
    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void protectionTripsAfterContinuousMeterAndManualRecloseClearsLatch(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        require(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level, pos, Direction.NORTH), "place supported APC");
        PowerDeviceBlockEntity apc = (PowerDeviceBlockEntity) level.getBlockEntity(pos);

        apc.observeActualApcOutput(1, 1, false, 25_000);
        require(apc.breakerClosed() && !apc.tripLatched(), "unknown high meter must not trip");
        apc.observeActualApcOutput(2, 2, true, 20_000);
        require(apc.breakerClosed() && !apc.tripLatched(), "threshold equality/demand alone must not trip");
        apc.observeActualApcOutput(3, 3, true, 0);
        require(apc.breakerClosed() && !apc.tripLatched(), "high demand with zero delivered output must not trip");
        for (long tick = 4; tick <= 64; tick++)
            apc.observeActualApcOutput(tick, tick, true, 20_001);
        require(!apc.breakerClosed() && apc.tripLatched(), "continuous actual-output meter trips after >3 seconds");

        // The pure solve contract independently verifies that an open breaker cannot deliver output.
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("apc", 1, true, 2, true, false, 500_000)),
                List.of(new PowerLiveLoop.Lamp("lamp", 2, true)), 20));
        require(result.apcOutputWatts().get("apc") == 0.0 && result.lampWatts().get("lamp") == 0.0,
                "open breaker solve must report zero APC output and lamp delivery");

        Player player = new Player(level, pos, 0, new GameProfile(UUID.randomUUID(), "overload-test")) {
            @Override public boolean isCreative() { return true; }
            @Override public boolean isSpectator() { return false; }
        };
        player.getAbilities().instabuild = true;
        require(apc.toggleBreaker(player, 0), "authorized manual reclose succeeds");
        require(apc.breakerClosed() && !apc.tripLatched(), "manual reclose clears the protection latch");
        helper.succeed();
    }

    /** Synthetic component IDs isolate projection behavior without a physical cable fixture. */
    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void trippedCircuitIsDarkWhileSeparateCircuitRemainsLit(GameTestHelper helper) {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), List.of(
                new PowerLiveLoop.Apc("tripped", 1, true, 10, true, false, 0),
                new PowerLiveLoop.Apc("healthy", 2, true, 20, true, true, 100_000)), List.of(
                new PowerLiveLoop.Lamp("tripped-lamp", 10, true),
                new PowerLiveLoop.Lamp("healthy-lamp", 20, true)), 1));

        require(result.lampWatts().get("tripped-lamp") == 0.0, "open breaker circuit must be dark");
        require(result.lampWatts().get("healthy-lamp") == 100.0,
                "unrelated healthy circuit must remain lit after a trip");
        helper.succeed();
    }

    /** An open APC sharing the APC-tier component cannot suppress another APC's battery supply. */
    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void healthyApcSuppliesLampOnSharedComponentAfterPeerTrips(GameTestHelper helper) {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), List.of(
                new PowerLiveLoop.Apc("tripped", 1, true, 10, true, false, 500_000),
                new PowerLiveLoop.Apc("healthy", 1, true, 10, true, true, 100_000)),
                List.of(new PowerLiveLoop.Lamp("shared-lamp", 10, true)), 1));

        require(result.lampWatts().get("shared-lamp") == 100.0,
                "healthy APC battery must retain shared-component lamp supply");
        require(result.apcOutputWatts().get("tripped") == 0.0,
                "tripped APC must not contribute output on the shared component");
        require(result.apcOutputWatts().get("healthy") == 100.0,
                "healthy APC must own delivered output on the shared component");
        helper.succeed();
    }

    /** Real connected HV/MV/APC graph with enough live lamps to trip the output meter. */
    @GameTestTags({"suite-stabilization", "machine-model-regression"})
    @GameTest(template = "power_overload_clear", timeoutTicks = 1200, templateNamespace = "moonstation14")
    public static void connectedCablePathLampLoadTripsApcAndManualRecloseRestoresIt(GameTestHelper helper) {
        FixtureChunkTickets.around(helper, heavyPos(helper, 2, 0, 2),
                heavyPos(helper, 12, 3, 15), 0);
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = heavyPos(helper, 3, 1, 2);
        BlockPos secondSource = heavyPos(helper, 3, 1, 3);
        BlockPos substation = heavyPos(helper, 3, 1, 4);
        BlockPos secondSubstation = heavyPos(helper, 3, 1, 5);
        BlockPos apc = heavyPos(helper, 3, 1, 6);

        // Two real 25 kW sources and two existing typed substations share the same
        // connected tiers. Each transformer has a 30 kW input cap and 90% efficiency;
        // the pair provides 45 kW MV-side capacity, while the APC's 30 kW cap can
        // satisfy the 29.8 kW demand of 298 resolved 100 W lamps.
        for (int z = 2; z <= 5; z++)
            level.setBlock(heavyPos(helper, 4, 1, z), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++)
            level.setBlock(heavyPos(helper, 2, 1, z), Blocks.STONE.defaultBlockState(), 3);
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, secondSource, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, secondSubstation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 2; z <= 5; z++)
            require(CableStorage.place(level, heavyPos(helper, 4, 1, z), Direction.WEST,
                    CableTier.HV), "place source/substation HV cable at z=" + z);
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, heavyPos(helper, 2, 1, z), Direction.EAST,
                    CableTier.MV), "place substation/APC MV cable at z=" + z);

        // The APC output turns down from its west-facing cable onto a connected 10x10
        // floor spine. Three lamp layers sit one, two, or three blocks above each node.
        BlockPos outputHost = heavyPos(helper, 4, 1, 6);
        level.setBlock(outputHost, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, outputHost, Direction.WEST, CableTier.APC),
                "place APC output lead");
        int lampCount = 0;
        for (int x = 3; x <= 12; x++) {
            for (int z = 6; z <= 15; z++) {
                BlockPos floorHost = heavyPos(helper, x, 0, z);
                level.setBlock(floorHost, Blocks.STONE.defaultBlockState(), 3);
                require(CableStorage.place(level, floorHost, Direction.UP, CableTier.APC),
                        "place APC floor spine at " + floorHost);
                for (int y = 1; y <= 3; y++) {
                    BlockPos lamp = heavyPos(helper, x, y, z);
                    if (lamp.equals(apc) || lamp.equals(outputHost)) continue;
                    placeDevice(level, lamp, PowerDeviceKind.LAMP, Direction.NORTH);
                    lampCount++;
                }
            }
        }
        require(lampCount > 200, "fixture has enough real lamps to exceed the APC rating: " + lampCount);
        double fullFixtureDemandWatts = lampCount * PowerDeviceKind.LAMP.lampDemandWatts();
        double sourceCapacityWatts = 2 * PowerRuntime.SOURCE_WATTS * .9;
        require(sourceCapacityWatts >= fullFixtureDemandWatts && 2 * 30_000 * .9 >= fullFixtureDemandWatts,
                "native source and transformer capacity sustain full fixture demand: sources="
                        + sourceCapacityWatts + " W, substations=" + (2 * 30_000 * .9)
                        + " W, demand=" + fullFixtureDemandWatts + " W (must reach at least 29.8 kW)");
        var fixtureBounds = helper.getBounds();
        for (int x = 3; x <= 12; x++) for (int z = 6; z <= 15; z++) for (int y = 1; y <= 4; y++) {
            BlockPos pos = heavyPos(helper, x, y - 1, z);
            var center = new net.minecraft.world.phys.Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            require(fixtureBounds.contains(center), "native overload fixture coordinate outside template bounds: "
                    + pos + " bounds=" + fixtureBounds);
        }
        PowerDeviceBlockEntity apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
        apcEntity.setEnergyJoules(0);

        // Start the protection window at the first fresh, known APC output sample that
        // actually powers this connected fixture; absolute GameTest time is unrelated.
        CableFaceNode output = new CableFaceNode(outputHost, Direction.WEST, CableTier.APC);
        BlockPos spineHost = heavyPos(helper, 3, 0, 6);
        CableFaceNode spine = new CableFaceNode(spineHost, Direction.UP, CableTier.APC);
        PowerFixtureReadiness.await(helper, level, 700,
                () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level, output, spine)
                        && PowerRuntime.apcOutputObservation(level, apcEntity).known()
                        && PowerRuntime.apcOutputObservation(level, apcEntity).outputWatts() >= fullFixtureDemandWatts,
                () -> "await connected overload observation: " + overloadState(level, apcEntity, output, spine),
                () -> scheduleConnectedOverloadSequence(helper, level, apcEntity, apc, output, spine,
                        heavyPos(helper, 3, 1, 7), fullFixtureDemandWatts));
    }

    private static void scheduleConnectedOverloadSequence(GameTestHelper helper, ServerLevel level,
            PowerDeviceBlockEntity apcEntity, BlockPos apcPos, CableFaceNode output, CableFaceNode spine,
            BlockPos sampleLamp, double fullFixtureDemandWatts) {
        final int postTripTicks = 25 + 25 + 85;
        final int maxTripObservationTick = 400 - postTripTicks;
        var tripTick = new java.util.concurrent.atomic.AtomicInteger(-1);
        var cutTick = new java.util.concurrent.atomic.AtomicInteger(-1);
        var maxNativeTicks = new java.util.concurrent.atomic.AtomicLong();
        var lastNativeTicks = new java.util.concurrent.atomic.AtomicLong();
        var recentSamples = new java.util.ArrayDeque<String>();
        var expectedEpoch = new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE);
        var expectedDeadline = new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);
        for (int tick = 1; tick <= 400; tick++) {
            final int checkTick = tick;
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, tick, () -> {
                // A trip intentionally zeros output and resets native protection; detect it
                // before inspecting output/knownness so that legitimate trip is not a gap.
                if (tripTick.get() < 0) {
                    if (!apcEntity.breakerClosed() && apcEntity.tripLatched()) {
                        tripTick.set(checkTick);
                        require(maxNativeTicks.get() >= 60,
                                "native overload counter reached 60 before breaker trip: max="
                                        + maxNativeTicks.get() + ",last=" + lastNativeTicks.get() + ","
                                        + overloadState(level, apcEntity, output, spine));
                    }
                }
                if (tripTick.get() < 0) {
                    var protection = apcEntity.protectionState();
                    lastNativeTicks.set(protection.overloadTicks());
                    maxNativeTicks.accumulateAndGet(protection.overloadTicks(), Math::max);
                    var observed = PowerRuntime.apcOutputObservation(level, apcEntity);
                    recentSamples.addLast("testTick=" + checkTick + ",worldTick=" + level.getGameTime()
                            + ",output=" + observed.outputWatts() + ",known=" + observed.known()
                            + ",sampleTick=" + observed.sampleTick() + ",generation="
                            + observed.topologyGeneration() + ",overloadTicks=" + protection.overloadTicks()
                            + ",lastAdvancedTick=" + protection.lastAdvancedTick());
                    while (recentSamples.size() > 8) recentSamples.removeFirst();
                    var outputState = PowerGraphService.state(level, output);
                    var spineState = PowerGraphService.state(level, spine);
                    boolean connected = outputState.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                            && spineState.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                            && outputState.componentId() == spineState.componentId();
                    var config = com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules.DEFAULT_APC_PROTECTION;
                    if (apcEntity.breakerClosed() && observed.known() && observed.outputWatts() > config.maxLoadWatts()
                            && observed.outputWatts() >= fullFixtureDemandWatts && connected) {
                        if (protection.overloadTicks() > 0 && protection.lastAdvancedTick() != Long.MIN_VALUE) {
                            long epoch = protection.lastAdvancedTick() - protection.overloadTicks() + 1;
                            if (epoch != expectedEpoch.get()) {
                                expectedEpoch.set(epoch);
                                expectedDeadline.set(epoch + (long) Math.floor(config.tripTimeSeconds() * 20) + 1);
                            }
                        } else {
                            expectedEpoch.set(Long.MIN_VALUE);
                            expectedDeadline.set(Long.MAX_VALUE);
                        }
                        require(level.getBlockState(sampleLamp).getValue(PowerDeviceBlock.LIT),
                                "full fixture demand " + fullFixtureDemandWatts
                                        + " W must light connected lamp: "
                                + overloadState(level, apcEntity, output, spine));
                    } else {
                        expectedEpoch.set(Long.MIN_VALUE);
                        expectedDeadline.set(Long.MAX_VALUE);
                    }
                    if (expectedDeadline.get() != Long.MAX_VALUE && level.getGameTime() >= expectedDeadline.get() + 2) {
                        String trace = "native overload failure trace=" + recentSamples;
                        System.out.println(trace);
                        String diagnostic = "no trip: count=" + protection.overloadTicks() + ",last="
                                + protection.lastAdvancedTick() + ",epoch=" + expectedEpoch.get() + ",deadline="
                                + expectedDeadline.get() + ",worldTick=" + level.getGameTime() + ",output="
                                + observed + ",threshold=" + config.maxLoadWatts() + "W/"
                                + config.tripTimeSeconds() + "s";
                        require(false, diagnostic.substring(0, Math.min(diagnostic.length(), 850)));
                    }
                    if (checkTick >= maxTripObservationTick && tripTick.get() < 0) {
                        require(false, "native trip exceeded absolute phase budget " + maxTripObservationTick
                                + ": native=" + protection + ",epoch=" + expectedEpoch.get()
                                + ",deadline=" + expectedDeadline.get());
                    }
                }
                if (tripTick.get() < 0) return;

                int tripped = tripTick.get();
                if (checkTick == tripped) {
                    require(!apcEntity.breakerClosed() && apcEntity.tripLatched(),
                            "actual native protection trips after counter reaches 60: "
                                    + overloadState(level, apcEntity, output, spine));
                } else if (checkTick == tripped + 25) {
                    require(!level.getBlockState(sampleLamp).getValue(PowerDeviceBlock.LIT),
                            "post-trip live solve turns connected lamp off");
                    Player player = new Player(level, apcPos, 0,
                            new GameProfile(UUID.randomUUID(), "wired-overload-test")) {
                        @Override public boolean isCreative() { return true; }
                        @Override public boolean isSpectator() { return false; }
                    };
                    player.getAbilities().instabuild = true;
                    require(apcEntity.toggleBreaker(player, 0), "authorized manual reclose succeeds");
                    require(apcEntity.breakerClosed() && !apcEntity.tripLatched(),
                            "manual reclose clears persisted trip latch");
                    var saved = apcEntity.saveWithFullMetadata(level.registryAccess());
                    require(saved.getBoolean("BreakerClosed") && !saved.getBoolean("TripLatched"),
                            "APC breaker state serializes after manual reclose");
                } else if (checkTick == tripped + 50) {
                    require(level.getBlockState(sampleLamp).getValue(PowerDeviceBlock.LIT),
                            "fresh scheduled solve restores lamp after manual reclose");
                    BlockPos mvCable = heavyPos(helper, 2, 1, 5);
                    require(CableStorage.remove(level, mvCable, Direction.EAST, CableTier.MV) == CableTier.MV,
                            "remove real MV cable after overload accumulation has begun");
                    cutTick.set(checkTick);
                } else if (cutTick.get() >= 0 && checkTick == cutTick.get() + 85) {
                    require(apcEntity.breakerClosed() && !apcEntity.tripLatched(),
                            "disconnected circuit does not trip from stale overload samples");
                    require(!level.getBlockState(sampleLamp).getValue(PowerDeviceBlock.LIT),
                            "zero-battery lamp turns off after disconnected circuit resolves");
                    helper.succeed();
                }
            });
        }
    }

    /** Converts fixture-local coordinates to authored positions one block above the template origin. */
    private static BlockPos heavyPos(GameTestHelper helper, int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y + 1, z));
    }

    private static String overloadState(ServerLevel level, PowerDeviceBlockEntity apc,
                                        CableFaceNode output, CableFaceNode spine) {
        String solve = PowerRuntime.lastSolveDiagnostic(level);
        int lamps = solve.indexOf(",lampWatts=");
        // The runtime diagnostic lists every lamp; never copy the 300-lamp map into a failure.
        String ports = solve.substring(0, Math.min(lamps < 0 ? solve.length() : lamps, 240));
        return "output=" + PowerGraphService.state(level, output)
                + ",spine=" + PowerGraphService.state(level, spine)
                + ",apcOutput=" + PowerRuntime.apcOutputObservation(level, apc)
                + ",breakerClosed=" + apc.breakerClosed()
                + ",tripLatched=" + apc.tripLatched()
                + ",indexedDevices=" + PowerRuntime.indexedDeviceCount(level)
                + ",solvePorts=" + ports + ",solveDiagnosticLength=" + solve.length();
    }

    private static void placeDevice(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        if (kind == PowerDeviceKind.APC) {
            require(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level, pos, facing), "place supported APC");
            return;
        }
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            case LAMP -> ModBlocks.POWER_LAMP.get();
            case DEBUG_LOAD_LAMP -> ModBlocks.HIGH_LOAD_TEST_LAMP.get();
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
