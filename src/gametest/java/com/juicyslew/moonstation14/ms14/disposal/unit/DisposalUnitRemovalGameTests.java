package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisposalUnitRemovalGameTests {
    private DisposalUnitRemovalGameTests() { }

    @GameTest(batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void freedUnitCellIsSafeRemovalExitWhenAdjacentCellsBlocked(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 2; dy++) {
            var p = pos.offset(dx, dy, dz);
            if (!p.equals(pos)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(pos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = level.getBlockEntity(pos);
        if (!(unit instanceof DisposalUnitBlockEntity disposal)) throw new GameTestAssertException("disposal unit BE placed");
        disposal.setPowerSwitch(false);
        var body = helper.spawn(EntityType.PIG, new net.minecraft.core.BlockPos(3, 2, 3));
        boolean invisible = body.isInvisible(), noPhysics = body.noPhysics, noGravity = body.isNoGravity();
        if (!disposal.insertBody(body)) throw new GameTestAssertException("body admitted to unit");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (body.isRemoved() || body.isInvisible() != invisible || body.noPhysics != noPhysics || body.isNoGravity() != noGravity)
            throw new GameTestAssertException("removal restores original body flags and retains body");
        if (Math.abs(body.getX() - (pos.getX() + .5)) > .1 || Math.abs(body.getY() - pos.getY()) > .1)
            throw new GameTestAssertException("freed unit cell is used as a collision-safe exit");
        helper.succeed();
    }
}
