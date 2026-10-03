package com.juicyslew.moonstation14.gametest.atmos;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.FunctionalAirAlarmPlacement;
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
public final class FunctionalAirAlarmPlacementGameTests {
    private FunctionalAirAlarmPlacementGameTests() { }

    @GameTest(template = "empty")
    public static void horizontalFacesMountOutwardAndDetachWithTheirSupport(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos clicked = helper.absolutePos(new BlockPos(2, 2, 2));
        if (FunctionalAirAlarmPlacement.mountedState(level, clicked, Direction.NORTH) != null)
            throw new AssertionError("air is not a wall host");
        level.setBlock(clicked, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        for (Direction face : Direction.values()) {
            BlockPos host = clicked.relative(face);
            level.setBlock(host, Blocks.IRON_BLOCK.defaultBlockState(), 3);
            var placement = FunctionalAirAlarmPlacement.mountedState(level, host, face);
            BlockPos target = host.relative(face);
            if (placement == null || placement.getValue(AirAlarmBlock.FACING) != face
                    || !placement.canSurvive(level, target)
                    || !placement.getCollisionShape(level, target, CollisionContext.empty()).isEmpty())
                throw new AssertionError("wall placement must face outward, survive, and remain noncolliding: " + face);
            if (!level.getBlockState(host).is(Blocks.IRON_BLOCK))
                throw new AssertionError("clicked support block must remain unchanged: " + face);
            level.setBlock(target, placement, 3);
            if (!(level.getBlockEntity(target) instanceof AirAlarmBlockEntity alarm)
                    || alarm.prototype() != PrototypeRuntime.serverAirAlarms().get(ModAirAlarms.AIR_ALARM))
                throw new AssertionError("placed shell must have a BE bound to resolved inherited prototype: " + face);
            level.setBlock(host, Blocks.AIR.defaultBlockState(), 3);
            if (!level.getBlockState(target).isAir()
                    || FunctionalAirAlarmPlacement.mountedState(level, host, face) != null)
                throw new AssertionError("lost alarm support must detach it and refuse a new placement: " + face);
        }
        helper.succeed();
        MoonStation14.LOGGER.info("VENTILATION_REGRESSION_PASS horizontalFacesMountOutwardAndDetachWithTheirSupport");
    }
}
