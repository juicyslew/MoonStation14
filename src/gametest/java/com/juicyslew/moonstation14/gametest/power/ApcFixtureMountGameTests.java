package com.juicyslew.moonstation14.gametest.power;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"power", "apc-wall-mount"})
@PrefixGameTestTemplate(false)
public final class ApcFixtureMountGameTests {
    private ApcFixtureMountGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void fixturesMountAllFourFacesWithoutChangingPorts(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < directions.length; i++) {
            Direction facing = directions[i];
            BlockPos pos = helper.absolutePos(new BlockPos(2 + i * 3, 1, 2));
            helper.assertTrue(PowerDeviceFixturePlacement.placeApc(level, pos, facing), "fixture APC mounts on " + facing);
            helper.assertTrue(level.getBlockState(pos).getValue(PowerDeviceBlock.FACING) == facing, "facing remains " + facing);
            var supportPos = pos.relative(facing.getOpposite());
            helper.assertTrue(level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing), "sturdy backing at " + supportPos);
            helper.assertTrue(((PowerDeviceBlock) ModBlocks.APC.get()).kind().ports(pos, facing)
                    .equals(((PowerDeviceBlock) ModBlocks.APC.get()).kind().ports(pos, level.getBlockState(pos).getValue(PowerDeviceBlock.FACING))),
                    "the mount utility preserves existing device port orientation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void occupiedNonsupportIsNotOverwritten(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos apc = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos backing = apc.relative(Direction.EAST);
        level.setBlock(backing, Blocks.GLASS_PANE.defaultBlockState(), 3);
        helper.assertTrue(!PowerDeviceFixturePlacement.placeApc(level, apc, Direction.WEST),
                "non-sturdy occupied support position refuses placement");
        helper.assertTrue(level.getBlockState(backing).is(Blocks.GLASS_PANE), "fixture does not overwrite existing structure");
        helper.assertTrue(level.getBlockState(apc).isAir(), "APC is not placed without sturdy support");
        helper.succeed();
    }
}
