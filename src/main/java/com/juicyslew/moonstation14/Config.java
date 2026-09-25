package com.juicyslew.moonstation14;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    public static final ModConfigSpec.BooleanValue EXPERIMENTAL_VERTICAL_SLICE_MOVEMENT = BUILDER
            .comment("Allow the experimental vertical-slice movement on this server (sampled only at server startup)")
            .define("experimentalVerticalSliceMovement", false);

    public static final ModConfigSpec.BooleanValue EXPERIMENTAL_MIND_GHOST_CONTROL = BUILDER
            .comment("Opt in to operator-only experimental Mind ghost control tests (sampled only at server startup)")
            .define("experimentalMindGhostControl", false);

    public static final ModConfigSpec.BooleanValue ENABLE_ATMOSPHERICS = BUILDER
            .comment("Enable server-side atmospherics simulation (sampled only at server startup)")
            .define("enableAtmospherics", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ATMOSPHERE_VACUUM_DIMENSIONS = BUILDER
            .comment("Dimension IDs whose ambient atmosphere is vacuum. Changes apply at server startup; restart the server to reclassify ambient atmosphere.")
            .defineListAllowEmpty("atmosphereVacuumDimensions", List.of(), () -> "", Config::isValidAtmosphereDimension);

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    public static boolean isValidAtmosphereDimension(final Object obj) {
        if (!(obj instanceof String dimensionName)) return false;
        ResourceLocation location = ResourceLocation.tryParse(dimensionName);
        return location != null && !location.getPath().isEmpty();
    }

    public static Set<ResourceKey<Level>> parseAtmosphereVacuumDimensions(List<? extends String> dimensions) {
        return dimensions.stream()
                .map(ResourceLocation::parse)
                .map(location -> ResourceKey.create(Registries.DIMENSION, location))
                .collect(Collectors.toUnmodifiableSet());
    }
}
