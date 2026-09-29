package com.juicyslew.moonstation14.gametest.power.runtime;

import com.mojang.authlib.GameProfile;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApcOverloadGameTests {
    private ApcOverloadGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 320)
    public static void twoWiredDebugLampsTripAndGoDarkThenReclose(GameTestHelper helper) {
        DebugFixture fixture = debugFixture(helper, true);
        helper.runAfterDelay(100, () -> {
            require(PowerGraphService.state(fixture.level(), fixture.output()).knowledge()
                    == LoadedPowerGraph.Knowledge.KNOWN, "debug fixture output graph must be known");
            helper.runAfterDelay(100, () -> {
                require(!fixture.apc().breakerClosed() && fixture.apc().tripLatched(),
                        "24 kW actual delivery trips after more than three seconds");
                require(!fixture.lit(0) && !fixture.lit(1), "post-trip solve darkens both lamps");
                Player player = new Player(fixture.level(), fixture.apcPos(), 0,
                        new GameProfile(UUID.randomUUID(), "debug-load-reclose")) {
                    @Override public boolean isCreative() { return true; }
                    @Override public boolean isSpectator() { return false; }
                };
                player.getAbilities().instabuild = true;
                require(fixture.apc().toggleBreaker(player, 0), "authorized reclose succeeds");
                helper.runAfterDelay(25, () -> {
                    require(fixture.lit(0) && fixture.lit(1), "reclose restores powered lamps");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void oneWiredDebugLampStaysBelowTripThreshold(GameTestHelper helper) {
        DebugFixture fixture = debugFixture(helper, false);
        helper.runAfterDelay(125, () -> {
            require(fixture.lit(0), "single 12 kW lamp is powered");
            require(fixture.apc().breakerClosed() && !fixture.apc().tripLatched(),
                    "single lamp stays below 20 kW for more than three seconds");
            helper.succeed();
        });
    }

    private record DebugFixture(ServerLevel level, BlockPos apcPos, PowerDeviceBlockEntity apc,
                                CableFaceNode output, List<BlockPos> lamps) {
        boolean lit(int index) { return level.getBlockState(lamps.get(index)).getValue(PowerDeviceBlock.LIT); }
    }

    /** Compact version of the established source -> HV -> substation -> MV -> APC -> floor-cable path. */
    private static DebugFixture debugFixture(GameTestHelper helper, boolean twoLamps) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos sub = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 6));
        for (int z = 2; z <= 4; z++) {
            BlockPos host = helper.absolutePos(new BlockPos(4, 1, z));
            level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
            require(CableStorage.place(level, host, Direction.WEST, CableTier.HV), "HV cable " + z);
        }
        for (int z = 4; z <= 6; z++) {
            BlockPos host = helper.absolutePos(new BlockPos(2, 1, z));
            level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
            require(CableStorage.place(level, host, Direction.EAST, CableTier.MV), "MV cable " + z);
        }
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, sub, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        BlockPos outputHost = helper.absolutePos(new BlockPos(4, 1, 6));
        level.setBlock(outputHost, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, outputHost, Direction.WEST, CableTier.APC), "APC output lead");
        for (int x = 3; x <= 5; x++) for (int z = 6; z <= 7; z++) {
            BlockPos host = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
            require(CableStorage.place(level, host, Direction.UP, CableTier.APC), "floor LV cable " + host);
        }
        BlockPos first = helper.absolutePos(new BlockPos(4, 1, 7));
        BlockPos second = helper.absolutePos(new BlockPos(5, 1, 7));
        placeDevice(level, first, PowerDeviceKind.DEBUG_LOAD_LAMP, Direction.NORTH);
        if (twoLamps) placeDevice(level, second, PowerDeviceKind.DEBUG_LOAD_LAMP, Direction.NORTH);
        PowerDeviceBlockEntity entity = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
        entity.setEnergyJoules(100_000);
        return new DebugFixture(level, apc, entity,
                new CableFaceNode(outputHost, Direction.WEST, CableTier.APC), List.of(first, second));
    }

    /** Transition guard proof using synthetic meter observations, independent of the wire fixture. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void breakerTransitionsIgnoreRejectedActionsAndRepeatedTripSamples(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.APC.get().defaultBlockState(), 3);
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
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void protectionTripsAfterContinuousMeterAndManualRecloseClearsLatch(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.APC.get().defaultBlockState(), 3);
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
    @GameTest(template = "empty", timeoutTicks = 20)
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
    @GameTest(template = "empty", timeoutTicks = 20)
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
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void connectedCablePathLampLoadTripsApcAndManualRecloseRestoresIt(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos substation = helper.absolutePos(new BlockPos(3, 1, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 6));

        // The established device path: source -> HV -> substation -> MV -> APC.
        for (int z = 2; z <= 4; z++)
            level.setBlock(helper.absolutePos(new BlockPos(4, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(2, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        placeDevice(level, source, PowerDeviceKind.HV_SOURCE, Direction.EAST);
        placeDevice(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        placeDevice(level, apc, PowerDeviceKind.APC, Direction.WEST);
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, 1, z)), Direction.WEST,
                    CableTier.HV), "place source/substation HV cable at z=" + z);
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, 1, z)), Direction.EAST,
                    CableTier.MV), "place substation/APC MV cable at z=" + z);

        // The APC output turns down from its west-facing cable onto a connected 10x10
        // floor spine. Three lamp layers sit one, two, or three blocks above each node.
        BlockPos outputHost = helper.absolutePos(new BlockPos(4, 1, 6));
        level.setBlock(outputHost, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, outputHost, Direction.WEST, CableTier.APC),
                "place APC output lead");
        int lampCount = 0;
        for (int x = 3; x <= 12; x++) {
            for (int z = 6; z <= 15; z++) {
                BlockPos floorHost = helper.absolutePos(new BlockPos(x, 0, z));
                level.setBlock(floorHost, Blocks.STONE.defaultBlockState(), 3);
                require(CableStorage.place(level, floorHost, Direction.UP, CableTier.APC),
                        "place APC floor spine at " + floorHost);
                for (int y = 1; y <= 3; y++) {
                    BlockPos lamp = helper.absolutePos(new BlockPos(x, y, z));
                    if (lamp.equals(apc) || lamp.equals(outputHost)) continue;
                    placeDevice(level, lamp, PowerDeviceKind.LAMP, Direction.NORTH);
                    lampCount++;
                }
            }
        }
        require(lampCount > 200, "fixture has enough real lamps to exceed the APC rating: " + lampCount);
        PowerDeviceBlockEntity apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
        apcEntity.setEnergyJoules(0);

        // Allow the bounded incremental indexer and runtime solve to settle. Then give
        // the real per-server-tick protection meter more than its 60-tick trip duration.
        helper.runAfterDelay(100, () -> {
            CableFaceNode output = new CableFaceNode(outputHost, Direction.WEST, CableTier.APC);
            CableFaceNode spine = new CableFaceNode(helper.absolutePos(new BlockPos(3, 0, 6)),
                    Direction.UP, CableTier.APC);
            var outputState = PowerGraphService.state(level, output);
            require(outputState.knowledge() == LoadedPowerGraph.Knowledge.KNOWN,
                    "APC output path is indexed and known (output=" + outputState + ", spine="
                            + PowerGraphService.state(level, spine) + ", indexedDevices="
                            + PowerRuntime.indexedDeviceCount(level) + ")");
            require(PowerGraphService.state(level, output).componentId()
                            == PowerGraphService.state(level, spine).componentId(),
                    "APC output lead joins the lamp floor spine");
            require(PowerRuntime.lastSolveDiagnostic(level).contains("lampWatts="),
                    "live runtime has solved the connected fixture: " + PowerRuntime.lastSolveDiagnostic(level));
            helper.runAfterDelay(100, () -> {
                require(!apcEntity.breakerClosed() && apcEntity.tripLatched(),
                        "actual >20 kW connected lamp delivery trips and latches APC");
                helper.runAfterDelay(25, () -> {
                    require(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 7)))
                                    .getValue(PowerDeviceBlock.LIT) == false,
                            "scheduled post-trip live re-solve turns off connected lamps");
                    Player player = new Player(level, apc, 0,
                            new GameProfile(UUID.randomUUID(), "wired-overload-test")) {
                        @Override public boolean isCreative() { return true; }
                        @Override public boolean isSpectator() { return false; }
                    };
                    player.getAbilities().instabuild = true;
                    require(apcEntity.toggleBreaker(player, 0), "authorized manual reclose succeeds");
                    require(apcEntity.breakerClosed() && !apcEntity.tripLatched(),
                            "manual reclose clears the persisted trip latch");
                    var saved = apcEntity.saveWithFullMetadata(level.registryAccess());
                    require(saved.getBoolean("BreakerClosed") && !saved.getBoolean("TripLatched"),
                            "APC breaker state serializes after manual reclose");
                    helper.runAfterDelay(25, () -> {
                        require(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 7)))
                                        .getValue(PowerDeviceBlock.LIT),
                                "live scheduled solve restores lamps after manual reclose");
                        BlockPos mvCable = helper.absolutePos(new BlockPos(2, 1, 5));
                        require(CableStorage.remove(level, mvCable, Direction.EAST, CableTier.MV) == CableTier.MV,
                                "remove a real MV cable after overload accumulation has begun");
                        // Wait longer than the >3s protection interval. A cached pre-mutation
                        // >20kW meter must not keep accumulating while this circuit is disconnected.
                        helper.runAfterDelay(85, () -> {
                            require(apcEntity.breakerClosed() && !apcEntity.tripLatched(),
                                    "disconnected MV circuit does not latch from stale overload samples");
                            require(!level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 7)))
                                            .getValue(PowerDeviceBlock.LIT),
                                    "zero-battery lamps go dark after the disconnected circuit re-solves");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    private static void placeDevice(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
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
