package com.juicyslew.moonstation14.gametest.atmos;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirAlarmShellGameTests {
    private AirAlarmShellGameTests() { }

    @GameTest(template = "empty")
    public static void wallShellResolvesInheritedPrototypeWithoutTakingHost(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos alarmPos = host.north();
        level.setBlock(host, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var state = ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(AirAlarmBlock.FACING, Direction.NORTH);
        if (!state.canSurvive(level, alarmPos)) throw new AssertionError("alarm requires a wall host");
        level.setBlock(alarmPos, state, 3);
        var alarm = (AirAlarmBlockEntity) level.getBlockEntity(alarmPos);
        var definition = PrototypeRuntime.serverAirAlarms().get(ModAirAlarms.AIR_ALARM);
        if (definition == null || alarm == null || alarm.prototype() != definition
                || !definition.wallMount().anchored() || !definition.wallMount().nonColliding()
                || definition.apcPowerReceiver().requestedWatts() != 100)
            throw new AssertionError("live alarm must resolve inherited wallmount and 100 W definition");
        if (!level.getBlockState(host).is(Blocks.IRON_BLOCK)
                || !state.getCollisionShape(level, alarmPos, CollisionContext.empty()).isEmpty())
            throw new AssertionError("shell must leave wall intact and be non-colliding");
        level.setBlock(host, Blocks.AIR.defaultBlockState(), 3);
        if (!level.getBlockState(alarmPos).isAir()) throw new AssertionError("alarm must detach when host is removed");
        helper.succeed();
    }
}
