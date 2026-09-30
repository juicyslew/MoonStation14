package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import java.util.List;

public class ModCreativeModeTabs {
    public static final List<Supplier<? extends ItemLike>> MOONSTATION_ITEM_ENTRIES = List.of(
            ModItems.STEEL, ModItems.WOOD, ModItems.CLOTH, ModItems.GLASS, ModItems.PAPER, ModItems.PLASTIC,
            ModItems.CROWBAR, ModItems.STATION_FLOOR_TILE, ModItems.STATION_FLOOR_TILE_WHITE,
            ModItems.STATION_FLOOR_TILE_PRY, ModItems.HV_CABLE_SPOOL, ModItems.MV_CABLE_SPOOL,
            ModItems.APC_CABLE_SPOOL, ModItems.CABLE_CUTTER, ModItems.WAFFLE, ModItems.BOTTLE,
            ModItems.ATMOSPHERE_ANALYZER, ModItems.POUCH, ModItems.BAG, ModItems.BELT);
    public static final List<Supplier<? extends ItemLike>> MOONSTATION_BLOCK_ENTRIES = List.of(
            ModBlocks.STEEL_WALL_BLOCK, ModBlocks.STEEL_WALL_GIRDER_BLOCK, ModBlocks.STATION_FLOOR,
            ModBlocks.HV_SOURCE, ModBlocks.HV_MV_SUBSTATION, ModBlocks.APC, ModBlocks.POWER_LAMP,
            ModBlocks.HIGH_LOAD_TEST_LAMP, ModBlocks.MAGIC_BLOCK, ModBlocks.JUG,
            ModBlocks.ATMOS_AIR_PRODUCER, ModBlocks.ATMOS_OXYGEN_PRODUCER,
            ModBlocks.ATMOS_NITROGEN_PRODUCER, ModBlocks.ATMOS_CARBON_DIOXIDE_PRODUCER,
            ModBlocks.ATMOS_PLASMA_PRODUCER, ModBlocks.ATMOS_TRITIUM_PRODUCER,
            ModBlocks.ATMOS_WATER_VAPOR_PRODUCER, ModBlocks.ATMOS_AMMONIA_PRODUCER,
            ModBlocks.ATMOS_NITROUS_OXIDE_PRODUCER, ModBlocks.ATMOS_FREZON_PRODUCER,
            ModBlocks.ATMOS_GAS_SINK, ModBlocks.ATMOS_HEATER, ModBlocks.ATMOS_COOLER);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MoonStation14.MOD_ID);

    public static final Supplier<CreativeModeTab> MOONSTATION_ITEMS_TAB = CREATIVE_MODE_TAB.register("moonstation_items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.STEEL.get()))
                    .title(Component.translatable("creativetab.moonstation14.moonstation_items"))
                    .displayItems((itemDisplayParameters, output) ->
                            MOONSTATION_ITEM_ENTRIES.forEach(entry -> output.accept(entry.get()))).build());
    public static final Supplier<CreativeModeTab> MOONSTATION_BLOCKS_TAB = CREATIVE_MODE_TAB.register("moonstation_blocks_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.STEEL_WALL_GIRDER_BLOCK.get()))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "moonstation_items_tab"))
                    .title(Component.translatable("creativetab.moonstation14.moonstation_blocks"))
                    .displayItems((itemDisplayParameters, output) ->
                            MOONSTATION_BLOCK_ENTRIES.forEach(entry -> output.accept(entry.get()))).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
