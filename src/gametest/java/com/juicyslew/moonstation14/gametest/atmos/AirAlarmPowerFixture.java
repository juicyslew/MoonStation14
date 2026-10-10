package com.juicyslew.moonstation14.gametest.atmos;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;

/** Shared real APC-fed alarm fixture used by power and device-network GameTests. */
public final class AirAlarmPowerFixture {
    private AirAlarmPowerFixture() { }

    public record Setup(ServerLevel level, AirAlarmBlockEntity alarm, BlockPos alarmPos,
                        BlockPos apc, BlockPos cable) { }

    public static Setup place(GameTestHelper helper) {
        FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(2, 8, 2)),
                helper.absolutePos(new BlockPos(4, 12, 10)), 0);
        ServerLevel level = (ServerLevel) helper.getLevel();
        int y = 12;
        BlockPos source = helper.absolutePos(new BlockPos(3, y, 2));
        BlockPos sub = helper.absolutePos(new BlockPos(3, y, 4));
        BlockPos apc = helper.absolutePos(new BlockPos(3, y, 6));
        BlockPos alarmPos = helper.absolutePos(new BlockPos(4, y, 10));
        BlockPos cable = helper.absolutePos(new BlockPos(4, y, 7));
        for (int z = 2; z <= 7; z++) level.setBlock(helper.absolutePos(new BlockPos(4, y, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int z = 4; z <= 6; z++) level.setBlock(helper.absolutePos(new BlockPos(2, y, z)), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(alarmPos.south(), Blocks.STONE.defaultBlockState(), 3);
        placePower(level, source, com.juicyslew.moonstation14.block.ModBlocks.HV_SOURCE.get(), Direction.EAST);
        placePower(level, sub, com.juicyslew.moonstation14.block.ModBlocks.HV_MV_SUBSTATION.get(), Direction.EAST);
        placePower(level, apc, com.juicyslew.moonstation14.block.ModBlocks.APC.get(), Direction.WEST);
        for (int z = 2; z <= 4; z++) check(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.HV), "HV cable");
        for (int z = 4; z <= 6; z++) check(CableStorage.place(level, helper.absolutePos(new BlockPos(2, y, z)), Direction.EAST, CableTier.MV), "MV cable");
        for (int z = 6; z <= 7; z++) check(CableStorage.place(level, helper.absolutePos(new BlockPos(4, y, z)), Direction.WEST, CableTier.APC), "APC cable");
        for (int cx = (source.getX() - 4) >> 4; cx <= (alarmPos.getX() + 4) >> 4; cx++)
            for (int cz = (source.getZ() - 4) >> 4; cz <= (alarmPos.getZ() + 4) >> 4; cz++) level.getChunk(cx, cz);
        level.setBlock(alarmPos, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, Direction.NORTH), 3);
        AirAlarmBlockEntity alarm = (AirAlarmBlockEntity) level.getBlockEntity(alarmPos);
        check(alarm != null && alarm.prototype() == PrototypeRuntime.serverAirAlarms().get(ModAirAlarms.AIR_ALARM), "resolved alarm entity");
        check(alarm.prototype().apcPowerReceiver().requestedWatts() == 100 && alarm.prototype().extensionCableReceiver().localReachBlocks() == 3, "resolved power values");
        return new Setup(level, alarm, alarmPos, apc, cable);
    }

    private static void placePower(ServerLevel level, BlockPos pos, com.juicyslew.moonstation14.block.custom.PowerDeviceBlock block, Direction facing) {
        level.setBlock(pos, block.defaultBlockState().setValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING, facing), 3);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
