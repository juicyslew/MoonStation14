package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PuddleBlockEntityGameTests {
    private PuddleBlockEntityGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void overflowWaitsUntilAboveFiftyAndConservesCents(GameTestHelper helper) {
        BlockPos lowSourcePos = new BlockPos(2, 1, 2);
        BlockPos lowTargetPos = new BlockPos(3, 1, 2);
        BlockPos flowingSourcePos = new BlockPos(6, 1, 2);
        BlockPos flowingTargetPos = new BlockPos(7, 1, 2);
        for (BlockPos floor : new BlockPos[]{lowSourcePos, lowTargetPos, flowingSourcePos, flowingTargetPos}) {
            helper.setBlock(floor.below(), Blocks.STONE);
        }
        for (BlockPos blocked : new BlockPos[]{
                lowSourcePos.north(), lowSourcePos.south(), lowSourcePos.west(),
                lowTargetPos.north(), lowTargetPos.south(), lowTargetPos.east(),
                flowingSourcePos.north(), flowingSourcePos.south(), flowingSourcePos.west(),
                flowingTargetPos.north(), flowingTargetPos.south(), flowingTargetPos.east()}) {
            helper.setBlock(blocked, Blocks.STONE);
        }
        helper.setBlock(lowSourcePos, ModBlocks.PUDDLE.get().defaultBlockState());
        helper.setBlock(flowingSourcePos, ModBlocks.PUDDLE.get().defaultBlockState());
        helper.setBlock(flowingTargetPos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity lowSource = (PuddleBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(lowSourcePos));
        PuddleBlockEntity flowingSource = (PuddleBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(flowingSourcePos));
        PuddleBlockEntity flowingTarget = (PuddleBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(flowingTargetPos));
        require(lowSource.getCapacity() == 1000f && flowingSource.getCapacity() == 1000f,
                "puddle maximum capacity must be 1000 units");
        fill(lowSource, 20f);
        fill(flowingSource, 51.01f);
        fill(flowingTarget, 1f);
        long expectedTotal = MS14Provider.get(flowingSource, MS14Bridges.REAGENT).totalUnits()
                + MS14Provider.get(flowingTarget, MS14Bridges.REAGENT).totalUnits();

        helper.runAfterDelay(2, () -> {
            flowOnce(helper, lowSourcePos, lowSource);
            flowOnce(helper, flowingSourcePos, flowingSource);
            BlockPos lowTarget = helper.absolutePos(lowTargetPos);
            require(!helper.getLevel().getBlockState(lowTarget).is(ModBlocks.PUDDLE.get()),
                    "20 units must not create a spreading neighbor");
            long sourceCents = MS14Provider.get(flowingSource, MS14Bridges.REAGENT).totalUnits();
            long destinationCents = MS14Provider.get(flowingTarget, MS14Bridges.REAGENT).totalUnits();
            require(destinationCents > 100L && sourceCents + destinationCents == expectedTotal,
                    "volume above 50 must flow to the neighbor while retaining and conserving exact cents");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyOverCapacityPuddleSurvivesTickUnchanged(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        ReagentAttachment legacy = new ReagentAttachment();
        legacy.specificAdd(ModReagents.createKey("water"), 1001f, 2000f);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, legacy);
        long before = MS14Provider.get(puddle, MS14Bridges.REAGENT).totalUnits();

        helper.runAfterDelay(2, () -> {
            PuddleBlockEntity.tick(helper.getLevel(), helper.absolutePos(pos),
                    helper.getLevel().getBlockState(helper.absolutePos(pos)), puddle);
            require(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) == puddle,
                    "legacy over-capacity puddle must not be deleted");
            require(MS14Provider.get(puddle, MS14Bridges.REAGENT).totalUnits() == before,
                    "legacy over-capacity contents must remain unchanged for repair");
            helper.succeed();
        });
    }

    private static void fill(PuddleBlockEntity puddle, float volume) {
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("water"), volume, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
    }

    private static void flowOnce(GameTestHelper helper, BlockPos relativePos, PuddleBlockEntity puddle) {
        BlockPos pos = helper.absolutePos(relativePos);
        try {
            var method = PuddleBlockEntity.class.getDeclaredMethod("slowTick",
                    net.minecraft.world.level.Level.class, BlockPos.class,
                    net.minecraft.world.level.block.state.BlockState.class, PuddleBlockEntity.class);
            method.setAccessible(true);
            method.invoke(puddle, helper.getLevel(), pos, helper.getLevel().getBlockState(pos), puddle);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not invoke puddle flow calculation", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
