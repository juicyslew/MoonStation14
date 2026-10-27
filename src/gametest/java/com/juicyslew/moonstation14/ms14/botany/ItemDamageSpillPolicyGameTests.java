package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestTags({"botany", "botany-item-damage", "loose-objects"})
@PrefixGameTestTemplate(false)
public final class ItemDamageSpillPolicyGameTests {
    private ItemDamageSpillPolicyGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    public static void jugContactRetainsOnlyRemainderRejectedByPartiallyFullPuddle(GameTestHelper helper) {
        var setup = setupPuddle(helper, 500);
        var water = ModReagents.createKey("water");
        var jugStack = jugWithWater(1000);
        var jug = addJug(helper, setup.floor, jugStack);
        jug.tick();
        require(!jug.isRemoved(), "partially admitted jug remains as the owner of rejected remainder");
        require(jug.getItem().get(ModDataComponents.REAGENT.get()).centContents().getOrDefault(water, 0L) == 500,
                "real jug contact consumer retains exactly the rejected 500 cents");
        var after = MS14Provider.getDetached(setup.puddle, MS14Bridges.REAGENT).snapshotUnits();
        require(after.getOrDefault(water, 0L) == setup.capacity,
                "existing puddle receives exactly its remaining 500-cent capacity");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    public static void jugContactRetainsEntireSourceWhenPuddleRejectsAllContents(GameTestHelper helper) {
        var setup = setupPuddle(helper, 0);
        var water = ModReagents.createKey("water");
        var before = MS14Provider.getDetached(setup.puddle, MS14Bridges.REAGENT).snapshotUnits();
        var jug = addJug(helper, setup.floor, jugWithWater(1000));
        jug.tick();
        require(!jug.isRemoved(), "fully rejecting puddle does not destroy a filled jug");
        require(jug.getItem().get(ModDataComponents.REAGENT.get()).centContents().getOrDefault(water, 0L) == 1000,
                "zero admission leaves all 1000 source cents unchanged");
        require(MS14Provider.getDetached(setup.puddle, MS14Bridges.REAGENT).snapshotUnits().equals(before),
                "full destination remains unchanged after rejected spill");
        helper.succeed();
    }

    private static PuddleSetup setupPuddle(GameTestHelper helper, long freeCapacityCents) {
        var level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int cx = net.minecraft.world.level.ChunkPos.getX(floor.getX() - 2);
             cx <= net.minecraft.world.level.ChunkPos.getX(floor.getX() + 2); cx++)
            for (int cz = net.minecraft.world.level.ChunkPos.getZ(floor.getZ() - 2);
                 cz <= net.minecraft.world.level.ChunkPos.getZ(floor.getZ() + 2); cz++) level.getChunk(cx, cz);
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        BlockPos puddlePos = floor.above();
        level.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(puddlePos) instanceof PuddleBlockEntity puddle))
            throw new GameTestAssertException("actual puddle block entity is available");
        long capacity = ReagentUnits.fromFloat(puddle.getCapacity());
        require(capacity >= freeCapacityCents, "resolved puddle capacity accommodates fixture fill");
        var initial = ReagentComponent.fromCents(Map.of(ModReagents.createKey("water"), capacity - freeCapacityCents));
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(initial));
        require(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).totalUnits() == capacity - freeCapacityCents,
                "fixture fills actual resolved-capacity puddle in cent units");
        return new PuddleSetup(floor, puddle, capacity);
    }

    private static ItemStack jugWithWater(long cents) {
        var jug = new ItemStack(ModItems.JUG.get());
        jug.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(Map.of(ModReagents.createKey("water"), cents)));
        return jug;
    }

    private static LooseItemEntity addJug(GameTestHelper helper, BlockPos floor, ItemStack stack) {
        LooseItemEntity jug = LooseItemEntity.create(helper.getLevel(),
                new Vec3(floor.getX() + .5, floor.getY() + 2, floor.getZ() + .5), stack);
        jug.launch(new Vec3(0, -5, 0), 0);
        require(helper.getLevel().addFreshEntity(jug), "actual loose jug enters world before collision");
        return jug;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record PuddleSetup(BlockPos floor, PuddleBlockEntity puddle, long capacity) { }
}
