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
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.mojang.authlib.GameProfile;
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

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirAlarmPowerGameTests {
    private AirAlarmPowerGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unconnectedAlarmIsNotPowered(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, Direction.NORTH), 3);
        AirAlarmBlockEntity alarm = (AirAlarmBlockEntity) level.getBlockEntity(pos);
        require(alarm != null && !PowerRuntime.airAlarmMeter(level, alarm).known(), "missing APC cannot power alarm");
        helper.runAfterDelay(30, () -> {
            require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "no free status after solve");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 320)
    public static void alarmUsesInheritedHundredWattsAndRadiusThree(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        int y = 12;
        BlockPos source = helper.absolutePos(new BlockPos(3, y, 2));
        BlockPos sub = helper.absolutePos(new BlockPos(3, y, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, y, 6));
        BlockPos alarmPos = helper.absolutePos(new BlockPos(4, y, 10));
        BlockPos cable = helper.absolutePos(new BlockPos(4, y, 7));
        for (int z = 2; z <= 7; z++)
            level.setBlock(helper.absolutePos(new BlockPos(4, y, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(2, y, z)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(alarmPos.south(), Blocks.STONE.defaultBlockState(), 3);
        place(level, source, ModBlocks.HV_SOURCE.get(), Direction.EAST);
        place(level, sub, ModBlocks.HV_MV_SUBSTATION.get(), Direction.EAST);
        place(level, apc, ModBlocks.APC.get(), Direction.WEST);
        for (int z = 2; z <= 4; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.HV), "HV cable");
        for (int z = 4; z <= 6; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(2, y, z)), Direction.EAST, CableTier.MV), "MV cable");
        for (int z = 6; z <= 7; z++)
            require(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.APC), "APC cable");
        for (int cx = (source.getX() - 4) >> 4; cx <= (alarmPos.getX() + 4) >> 4; cx++)
            for (int cz = (source.getZ() - 4) >> 4; cz <= (alarmPos.getZ() + 4) >> 4; cz++)
                level.getChunk(cx, cz);
        level.setBlock(alarmPos, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, Direction.NORTH), 3);
        AirAlarmBlockEntity alarm = (AirAlarmBlockEntity) level.getBlockEntity(alarmPos);
        require(alarm != null && alarm.prototype() == PrototypeRuntime.serverAirAlarms().get(ModAirAlarms.AIR_ALARM),
                "live BE uses resolved inherited definition");
        require(alarm.prototype().apcPowerReceiver().requestedWatts() == 100
                && alarm.prototype().extensionCableReceiver().localReachBlocks() == 3, "prototype values");
        awaitPaidSolve(helper, level, alarm, alarmPos, apc, cable, 0);
    }

    private static void awaitPaidSolve(GameTestHelper helper, ServerLevel level, AirAlarmBlockEntity alarm,
                                       BlockPos alarmPos, BlockPos apc, BlockPos cable, int elapsed) {
        if (!PowerRuntime.airAlarmMeter(level, alarm).known()) {
            require(elapsed < 220, "connected alarm awaits billed 100 W: meter=" + PowerRuntime.airAlarmMeter(level, alarm)
                    + ", node=" + PowerGraphService.nearestApcExtensionNode(level, alarmPos, 3)
                    + ", ready=" + PowerGraphService.topologyReady(level));
            helper.runAfterDelay(5, () -> awaitPaidSolve(helper, level, alarm, alarmPos, apc, cable, elapsed + 5));
            return;
        }
        require(PowerRuntime.airAlarmMeter(level, alarm).deliveredWatts() == 100, "100 W full alarm solve");
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
            helper.runAfterDelay(40, () -> {
                require(PowerRuntime.airAlarmMeter(level, alarm).known(), "fresh solve restores alarm");
                BlockPos secondApc = apc.south();
                place(level, secondApc, ModBlocks.APC.get(), Direction.WEST);
                require(!PowerRuntime.airAlarmMeter(level, alarm).known(), "new ambiguous provider invalidates sample");
                helper.runAfterDelay(40, () -> {
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

    private static void place(ServerLevel level, BlockPos pos, PowerDeviceBlock block, Direction facing) {
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
