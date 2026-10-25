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
            ModItems.STEEL, ModItems.METAL_ROD, ModItems.WOOD, ModItems.CLOTH, ModItems.GLASS, ModItems.PAPER, ModItems.PLASTIC,
            ModItems.CHEESE_SLICE, ModItems.PUMPKIN_PIE_SLICE, ModItems.FOOD_MEAT_WHEAT, ModItems.FOOD_COCOA_BEANS,
            ModItems.COTTON_MATERIAL, ModItems.PYROTTON_MATERIAL,
            ModItems.WHEAT_SEEDS, ModItems.MEATWHEAT_SEEDS, ModItems.TOMATO_SEEDS, ModItems.POTATO_SEEDS, ModItems.POPPY_SEEDS, ModItems.ALOE_SEEDS,
            ModItems.WHEAT_BUSHEL, ModItems.MEATWHEAT_BUSHEL, ModItems.TOMATO, ModItems.POTATO, ModItems.POPPY, ModItems.ALOE,
            ModItems.CROWBAR, ModItems.AIRLOCK_MULTITOOL, ModItems.PILL, ModItems.PILL_CANISTER, ModItems.STATION_FLOOR_TILE, ModItems.STATION_FLOOR_TILE_WHITE,
            ModItems.STATION_FLOOR_TILE_PRY, ModItems.HV_CABLE_SPOOL, ModItems.MV_CABLE_SPOOL,
            ModItems.APC_CABLE_SPOOL, ModItems.WIRECUTTER, ModItems.WAFFLE, ModItems.BOTTLE, ModItems.SYRINGE,
            ModItems.ATMOSPHERE_ANALYZER, ModItems.GRENADE, ModItems.BAG, ModItems.BELT, ModItems.PICKAXE,
            ModItems.JUG, ModItems.BEAKER, ModItems.BEAKER_LARGE, ModItems.CHEM_MASTER, ModItems.GAS_PIPE_SEGMENT, ModItems.GAS_PIPE_SEGMENT_SECONDARY, ModItems.GAS_PIPE_SEGMENT_TERTIARY,
            ModItems.GAS_VENT, ModItems.GAS_SCRUBBER, ModItems.AIR_ALARM,
            ModItems.DISPOSAL_UNIT,
            ModItems.WRENCH, ModItems.SCREWDRIVER, ModItems.WELDER, ModItems.KITCHEN_KNIFE, ModItems.BOTANY_HOE, ModItems.BOTANY_SPADE,
            ModItems.OINTMENT, ModItems.BRUTEPACK, ModItems.GAUZE, ModItems.HANDHELD_HEALTH_ANALYZER,
            ModItems.NETWORK_CONFIGURATOR, ModItems.REMOTE_SIGNALLER, ModItems.REMOTE_SIGNALLER_ADVANCED,
            ModItems.PLASMA_TANK,
            ModEquipmentItems.GREY_CAP, ModEquipmentItems.GREY_JUMPSUIT, ModEquipmentItems.SIMPLE_COAT,
            ModEquipmentItems.BLACK_GLOVES, ModEquipmentItems.BLACK_SHOES, ModEquipmentItems.GLASSES,
            ModEquipmentItems.RED_TIE, ModEquipmentItems.BLACK_BANDANA, ModEquipmentItems.EARPIECES,
            ModEquipmentItems.BLANK_ID_CARD, ModEquipmentItems.UTILITY_CASE, ModEquipmentItems.SUIT_RAD,
            ModEquipmentItems.HOOD_RAD);
    public static final List<Supplier<? extends ItemLike>> MOONSTATION_BLOCK_ENTRIES = List.of(
            ModBlocks.STEEL_WALL_BLOCK, ModBlocks.PLASTIC_WALL, ModBlocks.STEEL_WALL_GIRDER_BLOCK, ModBlocks.STATION_FLOOR, ModBlocks.AIRLOCK,
            ModBlocks.HV_SOURCE, ModBlocks.HV_MV_SUBSTATION, ModBlocks.APC, ModBlocks.POWER_LAMP,
            ModBlocks.HIGH_LOAD_TEST_LAMP, ModBlocks.MAGIC_BLOCK,
            ModBlocks.PA_CONTROL_BOX, ModBlocks.PA_END_CAP, ModBlocks.PA_FUEL_CHAMBER, ModBlocks.PA_POWER_BOX,
            ModBlocks.PA_EMITTER_PORT, ModBlocks.PA_EMITTER_FORE, ModBlocks.PA_EMITTER_STARBOARD,
            ModBlocks.EMITTER, ModBlocks.FIELD_GENERATOR, ModBlocks.SINGULARITY_GENERATOR, ModBlocks.TESLA_GENERATOR,
            ModBlocks.TESLA_COIL, ModBlocks.TESLA_GROUNDING_ROD, ModBlocks.SINGULARITY_BEACON, ModBlocks.RADIATION_COLLECTOR,
            ModBlocks.ATMOS_AIR_PRODUCER, ModBlocks.ATMOS_OXYGEN_PRODUCER,
            ModBlocks.ATMOS_NITROGEN_PRODUCER, ModBlocks.ATMOS_CARBON_DIOXIDE_PRODUCER,
            ModBlocks.ATMOS_PLASMA_PRODUCER, ModBlocks.ATMOS_TRITIUM_PRODUCER,
            ModBlocks.ATMOS_WATER_VAPOR_PRODUCER, ModBlocks.ATMOS_AMMONIA_PRODUCER,
            ModBlocks.ATMOS_NITROUS_OXIDE_PRODUCER, ModBlocks.ATMOS_FREZON_PRODUCER,
            ModBlocks.ATMOS_GAS_SINK, ModBlocks.ATMOS_HEATER, ModBlocks.ATMOS_COOLER,
            ModItems.GAS_PIPE_VISUAL, ModItems.GAS_PIPE_VISUAL_SECONDARY, ModItems.GAS_PIPE_VISUAL_TERTIARY,
            ModBlocks.DISPOSAL_UNIT, ModBlocks.DISPOSAL_TUBE, ModBlocks.CHEM_MASTER,
            ModBlocks.SIGNAL_BUTTON, ModBlocks.SIGNAL_SWITCH,
            ModBlocks.HYDROPONICS_TRAY, ModBlocks.EMPTY_HYDROPONICS_TRAY, ModBlocks.SEED_EXTRACTOR, ModBlocks.REAGENT_GRINDER);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MoonStation14.MOD_ID);

    public static final Supplier<CreativeModeTab> MOONSTATION_ITEMS_TAB = CREATIVE_MODE_TAB.register("moonstation_items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.STEEL.get()))
                    .title(Component.translatable("creativetab.moonstation14.moonstation_items"))
                    .displayItems((itemDisplayParameters, output) -> {
                            MOONSTATION_ITEM_ENTRIES.stream().filter(entry -> entry.get().asItem() != ModItems.PLASMA_TANK.get())
                                    .forEach(entry -> output.accept(
                                            com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.createCreativeStack(entry.get())));
                            var tanks = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientGasTanks();
                            output.accept(com.juicyslew.moonstation14.item.custom.GasTankItem.createStack(tanks,
                                    com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.PLASMA_ID));
                            output.accept(com.juicyslew.moonstation14.item.custom.GasTankItem.createStack(tanks,
                                    com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.PLASMA_FILLED_ID));
                    }).build());
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
