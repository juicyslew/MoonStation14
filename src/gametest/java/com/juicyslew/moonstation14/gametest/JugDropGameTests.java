package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JugDropGameTests {
    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(
            ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));

    private JugDropGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void breakingJugDropsExactlyOneJugAndPreservesContents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos filledPos = new BlockPos(2, 1, 2);
        BlockPos emptyPos = new BlockPos(6, 1, 2);
        ReagentComponent contents = new ReagentComponent(Map.of(WATER, 12f));
        ItemStack filledPlacement = new ItemStack(ModItems.JUG.get());
        filledPlacement.set(ModDataComponents.REAGENT.get(), contents);

        level.setBlock(filledPos, ModBlocks.JUG.get().defaultBlockState(), 3);
        ModBlocks.JUG.get().setPlacedBy(level, filledPos, level.getBlockState(filledPos), null, filledPlacement);
        require(level.destroyBlock(filledPos, true), "filled jug should be destroyed");
        assertSingleJugDrop(helper, filledPos, contents, "filled jug");

        level.setBlock(emptyPos, ModBlocks.JUG.get().defaultBlockState(), 3);
        require(level.destroyBlock(emptyPos, true), "empty jug should be destroyed");
        assertSingleJugDrop(helper, emptyPos, null, "empty jug");
        helper.succeed();
    }

    private static void assertSingleJugDrop(GameTestHelper helper, BlockPos pos,
                                            ReagentComponent expectedContents, String description) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(pos).inflate(2));
        require(drops.size() == 1, description + " must produce exactly one dropped item, got " + drops.size());
        ItemStack stack = drops.get(0).getItem();
        require(stack.is(ModItems.JUG.get()), description + " drop must be the jug item");
        ReagentComponent actualContents = stack.get(ModDataComponents.REAGENT.get());
        if (expectedContents == null) {
            require(actualContents == null || actualContents.contents().isEmpty(),
                    "empty jug drop must not have reagent contents");
        } else {
            require(expectedContents.equals(actualContents), "filled jug drop must preserve its reagent component");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
