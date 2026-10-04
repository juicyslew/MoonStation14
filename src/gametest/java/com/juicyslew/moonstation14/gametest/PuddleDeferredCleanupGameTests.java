package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PuddleDeferredCleanupGameTests {
    private PuddleDeferredCleanupGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 5)
    public static void failedDeferredCommitKeepsEmptyPuddleEntity(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(pos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of()));
        puddle.beginDeferredEmptyCleanup();
        puddle.endDeferredEmptyCleanup(false);
        if (level.getBlockEntity(pos) != puddle) throw new GameTestAssertException("failed commit removed its empty puddle");
        helper.succeed();
    }
}
