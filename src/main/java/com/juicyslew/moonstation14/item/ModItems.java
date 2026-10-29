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
    /** Internal transport key only; valid instances must also carry PORTABLE_BODY_REFERENCE. */
    public static final DeferredItem<Item> PORTABLE_BODY_KEY = ITEMS.register("portable_body_key",
            () -> new Item(new Item.Properties().stacksTo(1).component(
                    com.juicyslew.moonstation14.component.ModDataComponents.LOOSE_OBJECT_PROFILE.get(),
                    com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.PORTABLE_BODY_KEY_ID)));
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
    public static final DeferredItem<PrototypeEdibleItem> COTTON_MATERIAL = ITEMS.register("cotton_material",
            () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(30)
                    .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "cotton_material"))));
    public static final DeferredItem<PrototypeEdibleItem> PYROTTON_MATERIAL = ITEMS.register("pyrotton_material",
            () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(30)
                    .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "pyrotton_material"))));
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
    // Source-equivalent item identities only; firearm behavior is intentionally not activated here.
    public static final DeferredItem<Item> WEAPON_REVOLVER_PYTHON = ITEMS.register("weapon_revolver_python", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> REVOLVER_CAP_GUN = ITEMS.register("revolver_cap_gun", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> REVOLVER_CAP_GUN_FAKE = ITEMS.register("revolver_cap_gun_fake", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CARTRIDGE_MAGNUM = ITEMS.register("cartridge_magnum", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CARTRIDGE_MAGNUM_AP = ITEMS.register("cartridge_magnum_ap", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CARTRIDGE_CAP = ITEMS.register("cartridge_cap", () -> new Item(new Item.Properties().stacksTo(1)));

    // Food
    public static final DeferredItem<Item> WAFFLE = ITEMS.register("waffle",
            () -> new Item(new Item.Properties().food(ModFoodProperties.WAFFLE))
    );
    public static final DeferredItem<PrototypeEdibleItem> CHEESE_SLICE = ITEMS.register("cheese_slice",
            () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> PUMPKIN_PIE_SLICE = ITEMS.register("pumpkin_pie_slice",
            () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> FOOD_MEAT_WHEAT = ITEMS.register("food_meat_wheat",
            () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "food_meat_wheat"))));
    public static final DeferredItem<ProduceEdibleItem> FOOD_COCOA_BEANS = ITEMS.register("food_cocoa_beans",
            () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "food_cocoa_beans"))));

    public static final DeferredItem<Item> WHEAT_SEEDS = ITEMS.register("wheat_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TOMATO_SEEDS = ITEMS.register("tomato_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> POTATO_SEEDS = ITEMS.register("potato_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> POPPY_SEEDS = ITEMS.register("poppy_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ALOE_SEEDS = ITEMS.register("aloe_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> MEATWHEAT_SEEDS = ITEMS.register("meatwheat_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CARROTS_SEEDS = ITEMS.register("carrots_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CABBAGE_SEEDS = ITEMS.register("cabbage_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LEMON_SEEDS = ITEMS.register("lemon_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LEMOON_SEEDS = ITEMS.register("lemoon_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LIME_SEEDS = ITEMS.register("lime_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ORANGE_SEEDS = ITEMS.register("orange_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> EXTRADIMENSIONAL_ORANGE_SEEDS = ITEMS.register("extradimensional_orange_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> APPLE_SEEDS = ITEMS.register("apple_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GOLDEN_APPLE_SEEDS = ITEMS.register("golden_apple_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> AMBROSIA_DEUS_SEEDS = ITEMS.register("ambrosiadeusseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> AMBROSIA_VULGARIS_SEEDS = ITEMS.register("ambrosiavulgarisseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BANANA_SEEDS = ITEMS.register("banana_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BERRY_SEEDS = ITEMS.register("berry_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BLOOD_TOMATO_SEEDS = ITEMS.register("blood_tomato_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BLOONION_SEEDS = ITEMS.register("bloonion_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BLUE_PUMPKIN_SEEDS = ITEMS.register("blue_pumpkin_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BLUE_TOMATO_SEEDS = ITEMS.register("blue_tomato_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BUNGO_SEEDS = ITEMS.register("bungo_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CANNABIS_SEEDS = ITEMS.register("cannabis_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CHANTERELLE_SEEDS = ITEMS.register("chanterelleseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CHERRY_SEEDS = ITEMS.register("cherry_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CHILI_SEEDS = ITEMS.register("chiliseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CHILLY_SEEDS = ITEMS.register("chillyseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> COCOA_SEEDS = ITEMS.register("cocoa_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CORN_SEEDS = ITEMS.register("corn_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> COTTON_SEEDS = ITEMS.register("cotton_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEATH_NETTLE_SEEDS = ITEMS.register("death_nettle_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> EGGPLANT_SEEDS = ITEMS.register("eggplant_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> EGGY_SEEDS = ITEMS.register("eggy_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> FLY_AMANITA_SEEDS = ITEMS.register("flyamanitaseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GALAXYTHISTLE_SEEDS = ITEMS.register("galaxythistleseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GARLIC_SEEDS = ITEMS.register("garlic_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GLASSTLE_SEEDS = ITEMS.register("glasstleseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GRAPE_SEEDS = ITEMS.register("grape_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HOLYMELON_SEEDS = ITEMS.register("holymelon_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> KOIBEAN_SEEDS = ITEMS.register("koibean_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LAUGHIN_PEA_SEEDS = ITEMS.register("laughin_pea_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LILY_SEEDS = ITEMS.register("lily_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LINGZHI_SEEDS = ITEMS.register("lingzhiseeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> NETTLE_SEEDS = ITEMS.register("nettle_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> OAT_SEEDS = ITEMS.register("oat_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ONION_SEEDS = ITEMS.register("onion_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ONIONRED_SEEDS = ITEMS.register("onionred_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PAPERCANE_SEEDS = ITEMS.register("papercane_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PEA_SEEDS = ITEMS.register("pea_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PINEAPPLE_SEEDS = ITEMS.register("pineapple_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PUMPKIN_SEEDS = ITEMS.register("pumpkin_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PYROTTON_SEEDS = ITEMS.register("pyrotton_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> RAINBOW_CANNABIS_SEEDS = ITEMS.register("rainbow_cannabis_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> RICE_SEEDS = ITEMS.register("rice_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SOYBEAN_SEEDS = ITEMS.register("soybean_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> STEELCAP_SEEDS = ITEMS.register("steelcap_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SUGARCANE_SEEDS = ITEMS.register("sugarcane_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TEA_PLANT_SEEDS = ITEMS.register("tea_plant_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TOBACCO_SEEDS = ITEMS.register("tobacco_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TOWER_CAP_SEEDS = ITEMS.register("towercap_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WATERMELON_SEEDS = ITEMS.register("watermelon_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> MIMANA_SEEDS = ITEMS.register("mimana_seeds", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> WHEAT_BUSHEL = ITEMS.register("wheat_bushel", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> MEATWHEAT_BUSHEL = ITEMS.register("meatwheat_bushel", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "meatwheat_bushel"))));
    public static final DeferredItem<ProduceEdibleItem> TOMATO = ITEMS.register("tomato", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"))));
    public static final DeferredItem<ProduceEdibleItem> POTATO = ITEMS.register("potato", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> POPPY = ITEMS.register("poppy", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> ALOE = ITEMS.register("aloe", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> CARROT = ITEMS.register("carrot", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> CABBAGE = ITEMS.register("cabbage", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LEMON = ITEMS.register("lemon", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LEMOON = ITEMS.register("lemoon", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LIME = ITEMS.register("lime", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> ORANGE = ITEMS.register("orange", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> EXTRADIMENSIONAL_ORANGE = ITEMS.register("extradimensional_orange", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> APPLE = ITEMS.register("apple", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> GOLDEN_APPLE = ITEMS.register("golden_apple", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> AMBROSIA_DEUS = ITEMS.register("ambrosia_deus", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> AMBROSIA_VULGARIS = ITEMS.register("ambrosia_vulgaris", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> BANANA = ITEMS.register("banana", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> BERRIES = ITEMS.register("berries", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> BLOOD_TOMATO = ITEMS.register("blood_tomato", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"))));
    public static final DeferredItem<ProduceEdibleItem> BLOONION = ITEMS.register("bloonion", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "bloonion"))));
    public static final DeferredItem<ProduceEdibleItem> BLUE_PUMPKIN = ITEMS.register("blue_pumpkin", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "blue_pumpkin"))));
    public static final DeferredItem<ProduceEdibleItem> BLUE_TOMATO = ITEMS.register("blue_tomato", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"))));
    public static final DeferredItem<ProduceEdibleItem> BUNGO = ITEMS.register("bungo", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LEAVES_CANNABIS = ITEMS.register("leaves_cannabis", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> MUSHROOM = ITEMS.register("mushroom", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> CHERRY = ITEMS.register("cherry", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> CHILI = ITEMS.register("chili", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> CHILLY = ITEMS.register("chilly", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> COCOA_POD = ITEMS.register("cocoa_pod", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> CORN = ITEMS.register("corn", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> COTTON_BOL = ITEMS.register("cotton_bol", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> DEATH_NETTLE = ITEMS.register("death_nettle", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "death_nettle"))));
    public static final DeferredItem<ProduceEdibleItem> EGGPLANT = ITEMS.register("eggplant", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> EGG = ITEMS.register("egg", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "egg"))));
    public static final DeferredItem<ProduceEdibleItem> FLY_AMANITA = ITEMS.register("fly_amanita", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> GALAXYTHISTLE = ITEMS.register("galaxythistle", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> GARLIC = ITEMS.register("garlic", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> GLASSTLE = ITEMS.register("glasstle", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "glasstle"))));
    public static final DeferredItem<ProduceEdibleItem> GRAPE = ITEMS.register("grape", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> HOLYMELON = ITEMS.register("holymelon", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "holymelon"))));
    public static final DeferredItem<ProduceEdibleItem> FOOD_KOIBEAN = ITEMS.register("food_koibean", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> FOOD_LAUGHIN_PEA_POD = ITEMS.register("food_laughin_pea_pod", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LILY = ITEMS.register("lily", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LINGZHI = ITEMS.register("lingzhi", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> NETTLE = ITEMS.register("nettle", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "nettle"))));
    public static final DeferredItem<ProduceEdibleItem> OAT_BUSHEL = ITEMS.register("oat_bushel", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> ONION = ITEMS.register("onion", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> ONION_RED = ITEMS.register("onion_red", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> PAPERCANE = ITEMS.register("papercane", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> FOOD_PEA_POD = ITEMS.register("food_pea_pod", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> PINEAPPLE = ITEMS.register("pineapple", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> PUMPKIN = ITEMS.register("pumpkin", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "pumpkin"))));
    public static final DeferredItem<ProduceEdibleItem> PYROTTON_BOL = ITEMS.register("pyrotton_bol", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LEAVES_CANNABIS_RAINBOW = ITEMS.register("leaves_cannabis_rainbow", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> RICE_BUSHEL = ITEMS.register("rice_bushel", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> FOOD_SOYBEANS = ITEMS.register("food_soybeans", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> STEEL_LOG = ITEMS.register("steel_log", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> SUGARCANE = ITEMS.register("sugarcane", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LEAVES_TEA = ITEMS.register("leaves_tea", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LEAVES_TOBACCO = ITEMS.register("leaves_tobacco", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> LOG = ITEMS.register("log", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> FOOD_WORLD_PEAS = ITEMS.register("food_world_peas", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> MIMANA = ITEMS.register("mimana", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProduceEdibleItem> WATERMELON = ITEMS.register("watermelon", () -> new ProduceEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "watermelon"))));
    public static final DeferredItem<PrototypeEdibleItem> TRASH_BANANA_PEEL = ITEMS.register("trash_banana_peel", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "trash_banana_peel"))));
    public static final DeferredItem<PrototypeEdibleItem> TRASH_MIMANA_PEEL = ITEMS.register("trash_mimana_peel", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1).component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "trash_mimana_peel"))));
    public static final DeferredItem<PrototypeEdibleItem> TRASH_CHERRY_PIT = ITEMS.register("trash_cherry_pit", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> FOOD_BUNGO_PIT = ITEMS.register("food_bungo_pit", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> FOOD_CORN_TRASH = ITEMS.register("food_corn_trash", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> HOLYMELON_SLICE = ITEMS.register("holymelon_slice", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> ONION_SLICE = ITEMS.register("onion_slice", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> ONION_RED_SLICE = ITEMS.register("onion_red_slice", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> PINEAPPLE_SLICE = ITEMS.register("pineapple_slice", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PrototypeEdibleItem> WATERMELON_SLICE = ITEMS.register("watermelon_slice", () -> new PrototypeEdibleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CLOTHING_HEAD_HAT_HOLY_WATERMELON = ITEMS.register("clothing_head_hat_holy_watermelon", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CLOTHING_HEAD_HAT_WATERMELON = ITEMS.register("clothing_head_hat_watermelon", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CARVED_PUMPKIN = ITEMS.register("carved_pumpkin", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> EGGSHELLS = ITEMS.register("eggshells", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem> SHARD_GLASS = ITEMS.register("shard_glass", () -> new com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem(new Item.Properties().stacksTo(1)
            .component(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), ResourceLocation.fromNamespaceAndPath("moonstation14", "shard_glass"))));

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
    public static final DeferredItem<Item> KITCHEN_KNIFE = ITEMS.register("kitchen_knife",
            () -> new Item(new Item.Properties().stacksTo(1)));
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
