package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.custom.BottleItem;
import com.juicyslew.moonstation14.item.custom.AtmosphereAnalyzerItem;
import com.juicyslew.moonstation14.item.custom.CrowbarItem;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.item.custom.SteelItem;
import com.juicyslew.moonstation14.item.custom.PrototypeEdibleItem;
import com.juicyslew.moonstation14.item.custom.StationFloorTileItem;
import com.juicyslew.moonstation14.item.custom.StationFloorTilePryItem;
import com.juicyslew.moonstation14.item.custom.CableSpoolItem;
import com.juicyslew.moonstation14.item.custom.WirecutterItem;
import com.juicyslew.moonstation14.item.custom.CreativeGasPipeVisualItem;
import com.juicyslew.moonstation14.item.custom.GrenadeItem;
import com.juicyslew.moonstation14.item.custom.SyringeItem;
import com.juicyslew.moonstation14.item.custom.BeakerItem;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.item.custom.PillItem;
import com.juicyslew.moonstation14.item.custom.ProduceEdibleItem;
import com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlock;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoonStation14.MOD_ID);
    // Force the equipment item declarations to register on the same native item registry.
    private static final Object EQUIPMENT_ITEMS = ModEquipmentItems.GREY_CAP;

    // SIMPLE ITEMS
    public static final DeferredItem<Item> PLASTIC = ITEMS.register("plastic",
            () -> new Item(new Item.Properties().stacksTo(30))
    );
    public static final DeferredItem<Item> GLASS = ITEMS.register("glass",
            () -> new Item(new Item.Properties().stacksTo(30).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "glass")))
    );
    public static final DeferredItem<Item> PAPER = ITEMS.register("paper",
            () -> new Item(new Item.Properties().stacksTo(30))
    );
    public static final DeferredItem<Item> CLOTH = ITEMS.register("cloth",
            () -> new Item(new Item.Properties().stacksTo(30))
    );
    public static final DeferredItem<Item> BAG = ITEMS.register("bag",
            () -> new Item(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "bag"))));
    public static final DeferredItem<Item> BELT = ITEMS.register("belt",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WOOD = ITEMS.register("wood",
            () -> new Item(new Item.Properties().stacksTo(30))
    );
    public static final DeferredItem<Item> PICKAXE = ITEMS.register("pickaxe",
            () -> new Item(new Item.Properties().durability(250))
    );
    public static final DeferredItem<Item> WRENCH = ITEMS.register("wrench", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SCREWDRIVER = ITEMS.register("screwdriver", () -> new Item(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "screwdriver"))));
    public static final DeferredItem<Item> WELDER = ITEMS.register("welder", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BOTANY_HOE = ITEMS.register("botany_hoe", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BOTANY_SPADE = ITEMS.register("botany_spade", () -> new Item(new Item.Properties().stacksTo(1)));

    // Food
    public static final DeferredItem<Item> WAFFLE = ITEMS.register("waffle",
            () -> new Item(new Item.Properties().food(ModFoodProperties.WAFFLE))
    );
    public static final DeferredItem<PrototypeEdibleItem> CHEESE_SLICE = ITEMS.register("cheese_slice",
            () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> PUMPKIN_PIE_SLICE = ITEMS.register("pumpkin_pie_slice",
            () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> WHEAT_SEEDS = ITEMS.register("wheat_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TOMATO_SEEDS = ITEMS.register("tomato_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> POTATO_SEEDS = ITEMS.register("potato_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> POPPY_SEEDS = ITEMS.register("poppy_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ALOE_SEEDS = ITEMS.register("aloe_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> WHEAT_BUSHEL = ITEMS.register("wheat_bushel", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> TOMATO = ITEMS.register("tomato", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> POTATO = ITEMS.register("potato", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> POPPY = ITEMS.register("poppy", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> ALOE = ITEMS.register("aloe", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));

    // CUSTOM ITEMS
    // TODO: If I start having dependency issues with what needs to be defined before what, consider a lazy-initialized static map pattern for these item's construction result maps.
    public static final DeferredItem<Item> STEEL = ITEMS.register("steel",
            () -> new SteelItem(new Item.Properties().stacksTo(30).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "steel")))
    );
    public static final DeferredItem<Item> METAL_ROD = ITEMS.register("metal_rod",
            () -> new Item(new Item.Properties().stacksTo(30)
                    .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "metal_rod")))
    );
    public static final DeferredItem<Item> CROWBAR = ITEMS.register("crowbar",
            () -> new CrowbarItem(new Item.Properties().durability(32))
    );
    public static final DeferredItem<StationFloorTileItem> STATION_FLOOR_TILE = ITEMS.register("station_floor_tile",
            () -> new StationFloorTileItem(new Item.Properties(), StationFloorBlock.TileFinish.STEEL));
    public static final DeferredItem<StationFloorTileItem> STATION_FLOOR_TILE_WHITE = ITEMS.register("station_floor_tile_white",
            () -> new StationFloorTileItem(new Item.Properties(), StationFloorBlock.TileFinish.WHITE));
    public static final DeferredItem<StationFloorTilePryItem> STATION_FLOOR_TILE_PRY = ITEMS.register("station_floor_tile_pry",
            () -> new StationFloorTilePryItem(new Item.Properties().durability(32)));
    public static final DeferredItem<CableSpoolItem> HV_CABLE_SPOOL = ITEMS.register("hv_cable_spool",
            () -> new CableSpoolItem(new Item.Properties().stacksTo(16), CableTier.HV));
    public static final DeferredItem<CableSpoolItem> MV_CABLE_SPOOL = ITEMS.register("mv_cable_spool",
            () -> new CableSpoolItem(new Item.Properties().stacksTo(16), CableTier.MV));
    public static final DeferredItem<CableSpoolItem> APC_CABLE_SPOOL = ITEMS.register("apc_cable_spool",
            () -> new CableSpoolItem(new Item.Properties().stacksTo(16), CableTier.APC));
    public static final DeferredItem<WirecutterItem> WIRECUTTER = ITEMS.register("wirecutter",
            () -> new WirecutterItem(new Item.Properties().durability(128)));
    public static final DeferredItem<Item> AIRLOCK_MULTITOOL = ITEMS.register("multitool",
            () -> new Item(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "airlock_multitool"))));
    public static final DeferredItem<BottleItem> BOTTLE = ITEMS.register("bottle",
            () -> new BottleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent()))
    );
    public static final DeferredItem<PillItem> PILL = ITEMS.register("pill", () -> new PillItem(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.PILL_TYPE.get(), new com.juicyslew.moonstation14.ms14.chemistry.output.PillData(0))
            .component(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent())));
    public static final DeferredItem<Item> PILL_CANISTER = ITEMS.register("pill_canister", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SyringeItem> SYRINGE = ITEMS.register("syringe",
            () -> new SyringeItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent())));
    public static final DeferredItem<JugItem> JUG = ITEMS.register("jug",
            () -> new JugItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), LooseObjectProfile.JUG_ID)));
    public static final DeferredItem<CreativeGasPipeVisualItem> GAS_PIPE_VISUAL = ITEMS.register("gas_pipe_visual",
            () -> new CreativeGasPipeVisualItem(ModBlocks.GAS_PIPE_VISUAL.get(), new Item.Properties(), 1));
    public static final DeferredItem<CreativeGasPipeVisualItem> GAS_PIPE_VISUAL_SECONDARY = ITEMS.register("gas_pipe_visual_secondary",
            () -> new CreativeGasPipeVisualItem(ModBlocks.GAS_PIPE_VISUAL.get(), new Item.Properties(), 2));
    public static final DeferredItem<CreativeGasPipeVisualItem> GAS_PIPE_VISUAL_TERTIARY = ITEMS.register("gas_pipe_visual_tertiary",
            () -> new CreativeGasPipeVisualItem(ModBlocks.GAS_PIPE_VISUAL.get(), new Item.Properties(), 4));
    public static final DeferredItem<Item> GAS_VENT = ITEMS.register("gas_vent",
            () -> new Item(new Item.Properties().stacksTo(1)));
    /** Entity presentation surrogate only; never contains canister gas or a tank slot. */
    public static final DeferredItem<Item> GAS_CANISTER_PROXY = ITEMS.register("gas_canister_proxy",
            () -> new Item(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "gas_canister"))));
    public static final DeferredItem<Item> GAS_SCRUBBER = ITEMS.register("gas_scrubber",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> AIR_ALARM = ITEMS.register("air_alarm",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GAS_PIPE_SEGMENT = ITEMS.register("gas_pipe_segment",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> GAS_PIPE_SEGMENT_SECONDARY = ITEMS.register("gas_pipe_segment_secondary",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> GAS_PIPE_SEGMENT_TERTIARY = ITEMS.register("gas_pipe_segment_tertiary",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> T_RAY_SCANNER = ITEMS.register("t_ray_scanner",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<GrenadeItem> GRENADE = ITEMS.register("grenade",
            () -> new GrenadeItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), LooseObjectProfile.GRENADE_ID)
                    .component(ModDataComponents.GRENADE_FUSE.get(), com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData.SAFE)));
    public static final DeferredItem<AtmosphereAnalyzerItem> ATMOSPHERE_ANALYZER = ITEMS.register("atmosphere_analyzer",
            () -> new AtmosphereAnalyzerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> OINTMENT = ITEMS.register("ointment", () -> new Item(new Item.Properties().stacksTo(10)));
    public static final DeferredItem<Item> BRUTEPACK = ITEMS.register("brutepack", () -> new Item(new Item.Properties().stacksTo(10)));
    public static final DeferredItem<Item> GAUZE = ITEMS.register("gauze", () -> new Item(new Item.Properties().stacksTo(10)));
    public static final DeferredItem<Item> HANDHELD_HEALTH_ANALYZER = ITEMS.register("handheld_health_analyzer", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BeakerItem> BEAKER = ITEMS.register("beaker", () -> new BeakerItem(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent()), ModBeakers.SMALL_ID));
    public static final DeferredItem<BeakerItem> BEAKER_LARGE = ITEMS.register("beaker_large", () -> new BeakerItem(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent()), ModBeakers.LARGE_ID));
    public static final DeferredItem<GasTankItem> PLASMA_TANK = ITEMS.register("plasma_tank", () -> new GasTankItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> CHEM_MASTER = ITEMS.register("chem_master",
            () -> new BlockItem(com.juicyslew.moonstation14.block.ModBlocks.CHEM_MASTER.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> NETWORK_CONFIGURATOR = ITEMS.register("network_configurator", () -> new Item(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "network_configurator"))
            .component(ModDataComponents.NETWORK_DEVICE_ITEM_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "configurator"))));
    public static final DeferredItem<Item> REMOTE_SIGNALLER = ITEMS.register("remote_signaller", () -> new Item(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "remote_signaller"))
            .component(ModDataComponents.NETWORK_DEVICE_ITEM_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "standardwireless15"))));
    public static final DeferredItem<Item> REMOTE_SIGNALLER_ADVANCED = ITEMS.register("remote_signaller_advanced", () -> new Item(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "remote_signaller_advanced"))
            .component(ModDataComponents.NETWORK_DEVICE_ITEM_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "advancedwireless50"))));
    public static final DeferredItem<BlockItem> DISPOSAL_UNIT = ITEMS.register("disposal_unit",
            () -> new BlockItem(ModBlocks.DISPOSAL_UNIT.get(), new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
