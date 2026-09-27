package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MoonStation14.MOD_ID);

    public static final Supplier<CreativeModeTab> MOONSTATION_ITEMS_TAB = CREATIVE_MODE_TAB.register("moonstation_items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.STEEL.get()))
                    .title(Component.translatable("creativetab.moonstation14.moonstation_items"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.STEEL);
                        output.accept(ModItems.WOOD);
                        output.accept(ModItems.CLOTH);
                        output.accept(ModItems.GLASS);
                        output.accept(ModItems.PAPER);
                        output.accept(ModItems.PLASTIC);
                        output.accept(ModItems.CROWBAR);
                        output.accept(ModItems.WAFFLE);
                        output.accept(ModItems.BOTTLE);
                        output.accept(ModItems.ATMOSPHERE_ANALYZER);
                    }).build());
    public static final Supplier<CreativeModeTab> MOONSTATION_BLOCKS_TAB = CREATIVE_MODE_TAB.register("moonstation_blocks_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.STEEL_WALL_GIRDER_BLOCK.get()))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "moonstation_items_tab"))
                    .title(Component.translatable("creativetab.moonstation14.moonstation_blocks"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModBlocks.STEEL_WALL_BLOCK);
                        output.accept(ModBlocks.STEEL_WALL_GIRDER_BLOCK);
                        output.accept(ModBlocks.MAGIC_BLOCK);
                        output.accept(ModBlocks.JUG);
                        output.accept(ModBlocks.ATMOS_AIR_PRODUCER);
                        output.accept(ModBlocks.ATMOS_OXYGEN_PRODUCER);
                        output.accept(ModBlocks.ATMOS_NITROGEN_PRODUCER);
                        output.accept(ModBlocks.ATMOS_CARBON_DIOXIDE_PRODUCER);
                        output.accept(ModBlocks.ATMOS_PLASMA_PRODUCER);
                        output.accept(ModBlocks.ATMOS_TRITIUM_PRODUCER);
                        output.accept(ModBlocks.ATMOS_WATER_VAPOR_PRODUCER);
                        output.accept(ModBlocks.ATMOS_AMMONIA_PRODUCER);
                        output.accept(ModBlocks.ATMOS_NITROUS_OXIDE_PRODUCER);
                        output.accept(ModBlocks.ATMOS_FREZON_PRODUCER);
                        output.accept(ModBlocks.ATMOS_GAS_SINK);
                        output.accept(ModBlocks.ATMOS_HEATER);
                        output.accept(ModBlocks.ATMOS_COOLER);
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
