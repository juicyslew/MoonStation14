package com.juicyslew.moonstation14.block;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static net.minecraft.world.level.block.Blocks.never;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MoonStation14.MOD_ID);

    public static final DeferredBlock<Block> STEEL_WALL_BLOCK = registerBlock("steel_wall_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.COPPER_GRATE)));

    public static final DeferredBlock<Block> STEEL_WALL_GIRDER_BLOCK = registerBlock("steel_wall_girder_block",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion()
                    .isViewBlocking(ModBlocks::never)
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.COPPER_GRATE)));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus){
        BLOCKS.register(eventBus);
    }

    private static boolean always(BlockState state, BlockGetter blockGetter, BlockPos pos) {
        return true;
    }

    private static boolean never(BlockState state, BlockGetter blockGetter, BlockPos pos) {
        return false;
    }
}
