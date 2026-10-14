package com.juicyslew.moonstation14.gametest.power;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.mojang.authlib.GameProfile;
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
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestTags({"power", "air-alarm", "ventilation"})
@PrefixGameTestTemplate(false)
public final class AirAlarmPowerGameTests {
    private AirAlarmPowerGameTests() { }

    @GameTestTags({"airlock", "power", "power_loaded_index_reconcile"})
    // Full-chunk footprint isolates this deliberate service-level unload from peer fixtures.
    @GameTest(template = "power_receiver_tall_clear", batch = "power_loaded_index_reconcile",
            timeoutTicks = 320, templateNamespace = "moonstation14")
    public static void receiverReconcilesAlreadyLoadedMissingFootprint(GameTestHelper helper) {
        FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(2, 8, 2)),
                helper.absolutePos(new BlockPos(4, 12, 10)), 0);
        ServerLevel level = (ServerLevel) helper.getLevel();
        int y = 12;
        BlockPos source = helper.absolutePos(new BlockPos(3, y, 2));
        BlockPos sub = helper.absolutePos(new BlockPos(3, y, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, y, 6));
        BlockPos alarmPos = helper.absolutePos(new BlockPos(4, y, 10));
        for (int z = 2; z <= 7; z++) level.setBlock(helper.absolutePos(new BlockPos(4, y, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++) level.setBlock(helper.absolutePos(new BlockPos(2, y, z)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(alarmPos.south(), Blocks.STONE.defaultBlockState(), 3);
        place(level, source, ModBlocks.HV_SOURCE.get(), Direction.EAST);
        place(level, sub, ModBlocks.HV_MV_SUBSTATION.get(), Direction.EAST);
        place(level, apc, ModBlocks.APC.get(), Direction.WEST);
        for (int z = 2; z <= 4; z++) require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.HV), "HV cable");
        for (int z = 4; z <= 6; z++) require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, y, z)), Direction.EAST, CableTier.MV), "MV cable");
        for (int z = 6; z <= 7; z++) require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.APC), "APC cable");
        level.setBlock(alarmPos, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, Direction.NORTH), 3);
        AirAlarmBlockEntity alarm = (AirAlarmBlockEntity) level.getBlockEntity(alarmPos);
        require(alarm != null, "live alarm fixture");
        var physical = level.getChunkSource().getChunkNow(alarmPos.getX() >> 4, alarmPos.getZ() >> 4);
        require(physical != null, "receiver chunk physically loaded");
        PowerGraphService.onChunkUnload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(physical));
        require(PowerGraphService.nodesNear(level, alarmPos, 3, CableTier.APC) == null, "missed index is unknown and catch-up queued");
        require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "catch-up cannot fabricate a meter sample");
        require(PowerGraphService.nodesNear(level, alarmPos, 3, CableTier.APC) == null, "repeated query stays unknown while queued");
        awaitFreshAlarmMeter(helper, level, alarm, alarmPos, apc, helper.absolutePos(new BlockPos(4, y, 7)),
                "loaded missing-footprint reconciliation proves delivered 100 W", helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 40, templateNamespace = "moonstation14")
    public static void unconnectedAlarmIsNotPowered(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, Direction.NORTH), 3);
        AirAlarmBlockEntity alarm = (AirAlarmBlockEntity) level.getBlockEntity(pos);
        require(alarm != null && !PowerRuntime.airAlarmMeter(level, alarm).known(), "missing APC cannot power alarm");
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 30, () -> {
            require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "no free status after solve");
            helper.succeed();
        });
    }

    @GameTest(template = "power_receiver_tall_clear", timeoutTicks = 1200, templateNamespace = "moonstation14")
    public static void alarmUsesInheritedHundredWattsAndRadiusThree(GameTestHelper helper) {
        var fixture = com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture.place(helper);
        awaitKnownAlarmTopology(helper, fixture.level(), fixture.alarmPos(), fixture.cable(),
                "alarm source-path topology and all expected ports become authoritative", () ->
                        awaitPaidSolve(helper, fixture.level(), fixture.alarm(), fixture.alarmPos(),
                                fixture.apc(), fixture.cable(), 0));
    }

    private static void awaitPaidSolve(GameTestHelper helper, ServerLevel level, AirAlarmBlockEntity alarm,
                                       BlockPos alarmPos, BlockPos apc, BlockPos cable, int elapsed) {
        if (!PowerRuntime.airAlarmMeter(level, alarm).known()) {
            require(elapsed < 220, "connected alarm awaits billed 100 W: meter=" + PowerRuntime.airAlarmMeter(level, alarm)
                    + ", node=" + PowerGraphService.nearestApcExtensionNode(level, alarmPos, 3)
                    + ", ready=" + PowerGraphService.topologyReady(level));
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5, () -> awaitPaidSolve(helper, level, alarm, alarmPos, apc, cable, elapsed + 5));
            return;
        }
        require(PowerFixtureReadiness.topologyReadyWithKnownPorts(level, alarmPorts(alarmPos, cable))
                        && PowerRuntime.airAlarmMeter(level, alarm).known()
                        && PowerRuntime.airAlarmMeter(level, alarm).deliveredWatts() == 100,
                "fresh 100 W alarm meter with authoritative source path and expected ports");
        PowerDeviceBlockEntity apcEntity = (PowerDeviceBlockEntity) level.getBlockEntity(apc);
        Player player = new Player(level, apc, 0, new GameProfile(UUID.randomUUID(), "alarm-power-test")) {
            @Override public boolean isCreative() { return true; }
            @Override public boolean isSpectator() { return false; }
        };
        player.getAbilities().instabuild = true;
        require(apcEntity.toggleBreaker(player, 0), "open breaker");
        require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "breaker invalidates sample immediately");
        require(apcEntity.toggleBreaker(player, 0), "close breaker");
        require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "closing breaker needs new solve");
        awaitFreshAlarmMeter(helper, level, alarm, alarmPos, apc, cable,
                "fresh solve after closing breaker restores alarm", () -> {
                require(PowerRuntime.airAlarmMeter(level, alarm).known(), "fresh solve restores alarm");
                BlockPos secondApc = apc.south();
                place(level, secondApc, ModBlocks.APC.get(), Direction.WEST);
                require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "new ambiguous provider invalidates sample");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 40, () -> {
                    require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "two APCs cannot bill alarm ambiguously");
                    level.setBlock(secondApc, Blocks.AIR.defaultBlockState(), 3);
                    require(CableStorage.remove(level, cable, Direction.WEST, CableTier.APC) == CableTier.APC, "cut cable");
                    require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "cable cut invalidates immediately");
                    level.setBlock(alarmPos, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(alarmPos, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, Direction.NORTH), 3);
                    AirAlarmBlockEntity replacement = (AirAlarmBlockEntity) level.getBlockEntity(alarmPos);
                    require(replacement != null && replacement != alarm
                            && !PowerRuntime.airAlarmMeter(level, alarm).known()
                            && !PowerRuntime.airAlarmMeter(level, replacement).known(), "replacement cannot inherit sample");
                    helper.succeed();
                });
        });
    }

    private static void awaitFreshAlarmMeter(GameTestHelper helper, ServerLevel level, AirAlarmBlockEntity alarm,
            BlockPos alarmPos, BlockPos apc, BlockPos cable, String phase, Runnable ready) {
        PowerFixtureReadiness.await(helper, level, 500,
                () -> alarmPortsKnown(level, alarmPos, cable)
                        && PowerRuntime.airAlarmMeter(level, alarm).known()
                        && PowerRuntime.airAlarmMeter(level, alarm).deliveredWatts() == 100,
                () -> phase + ", meter=" + PowerRuntime.airAlarmMeter(level, alarm) + ", ports="
                        + alarmPortDiagnostic(level, alarmPos, apc, cable) + ", indexedDevices="
                        + PowerRuntime.indexedDeviceCount(level), ready);
    }

    private static void awaitKnownAlarmTopology(GameTestHelper helper, ServerLevel level, BlockPos alarmPos,
                                                BlockPos cable, String phase, Runnable ready) {
        PowerFixtureReadiness.await(helper, level, 500,
                () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level, alarmPorts(alarmPos, cable)),
                () -> phase + ", ports=" + alarmPortDiagnostic(level, alarmPos,
                        alarmPos.offset(-1, 0, -4), cable) + ", indexedDevices="
                        + PowerRuntime.indexedDeviceCount(level), ready);
    }

    private static boolean alarmPortsKnown(ServerLevel level, BlockPos alarmPos, BlockPos cable) {
        return PowerFixtureReadiness.topologyReadyWithKnownPorts(level, alarmPorts(alarmPos, cable));
    }

    private static CableFaceNode[] alarmPorts(BlockPos alarmPos, BlockPos cable) {
        return new CableFaceNode[]{
                new CableFaceNode(alarmPos.offset(0, 0, -8), Direction.WEST, CableTier.HV),
                new CableFaceNode(alarmPos.offset(0, 0, -6), Direction.WEST, CableTier.HV),
                new CableFaceNode(alarmPos.offset(-2, 0, -6), Direction.EAST, CableTier.MV),
                new CableFaceNode(alarmPos.offset(-2, 0, -4), Direction.EAST, CableTier.MV),
                new CableFaceNode(alarmPos.offset(0, 0, -4), Direction.WEST, CableTier.APC),
                new CableFaceNode(cable, Direction.WEST, CableTier.APC)};
    }

    private static String alarmPortDiagnostic(ServerLevel level, BlockPos alarmPos, BlockPos apc, BlockPos cable) {
        var ports = alarmPorts(alarmPos, cable);
        return "sourceHV=" + PowerGraphService.state(level, ports[0]) + ",subHV=" + PowerGraphService.state(level, ports[1])
                + ",subMV=" + PowerGraphService.state(level, ports[2]) + ",apcMV=" + PowerGraphService.state(level, ports[3])
                + ",apcOutput=" + PowerGraphService.state(level, ports[4]) + ",extension=" + PowerGraphService.state(level, ports[5])
                + ",nearest=" + PowerGraphService.nearestApcExtensionNode(level, alarmPos, 3) + ",apc=" + apc
                + ",topologyReady=" + PowerGraphService.topologyReady(level);
    }

    private static void place(ServerLevel level, BlockPos pos, PowerDeviceBlock block, Direction facing) {
        if (block.kind() == PowerDeviceKind.APC) {
            require(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level, pos, facing), "place supported APC");
            return;
        }
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
