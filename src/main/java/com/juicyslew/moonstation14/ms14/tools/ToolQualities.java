package com.juicyslew.moonstation14.ms14.tools;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Runtime lookup of tool capabilities from the resolved server prototype snapshot. */
public final class ToolQualities {
    private ToolQualities() { }

    public static boolean has(ItemStack stack, ToolProfile.Quality quality) {
        if (stack == null || stack.isEmpty() || quality == null) return false;
        return has(BuiltInRegistries.ITEM.getKey(stack.getItem()), quality,
                PrototypeRuntime.serverManager().snapshot(ModToolProfiles.TYPE));
    }

    public static ToolProfile profile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return PrototypeRuntime.serverManager().snapshot(ModToolProfiles.TYPE)
                .get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Sound response follows resolved held-tool metadata and accepted operation only. */
    public static String acceptedWireActionSound(ItemStack stack, boolean accepted, boolean cutOrMend) {
        if (!accepted || !cutOrMend) return null;
        ToolProfile profile = profile(stack);
        return profile == null ? null : profile.useSound();
    }

    static String acceptedWireActionSound(ResourceLocation itemId, PrototypeCatalog<ToolProfile> catalog,
                                          boolean accepted, boolean cutOrMend) {
        if (!accepted || !cutOrMend || itemId == null || catalog == null) return null;
        ToolProfile profile = catalog.get(itemId);
        return profile == null ? null : profile.useSound();
    }

    public static boolean has(ResourceLocation itemId, ToolProfile.Quality quality,
                       PrototypeCatalog<ToolProfile> catalog) {
        if (itemId == null || quality == null || catalog == null) return false;
        ToolProfile profile = catalog.get(itemId);
        return profile != null && profile.tool() != null && profile.tool().qualities().contains(quality);
    }

    /** Returns zero for items without the supported prying component. */
    public static double prySpeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return prySpeed(BuiltInRegistries.ITEM.getKey(stack.getItem()),
                PrototypeRuntime.serverManager().snapshot(ModToolProfiles.TYPE));
    }

    /** Hoe identity is resolved component metadata, never inferred from the held item ID. */
    public static boolean hasHoe(ItemStack stack) {
        ToolProfile profile = profile(stack);
        return profile != null && profile.tags().contains("Hoe");
    }

    /** Shovel eligibility follows the resolved Shovel component, not a mining quality. */
    public static boolean hasShovel(ItemStack stack) {
        ToolProfile profile = profile(stack);
        return profile != null && profile.shovel() != null;
    }

    /** Returns zero when no resolved Shovel component is present. */
    public static double shovelSpeed(ItemStack stack) {
        ToolProfile profile = profile(stack);
        return profile == null || profile.shovel() == null ? 0 : profile.shovel().speedModifier();
    }

    public static boolean hasHoe(ResourceLocation itemId, PrototypeCatalog<ToolProfile> catalog) {
        ToolProfile profile = itemId == null || catalog == null ? null : catalog.get(itemId);
        return profile != null && profile.tags().contains("Hoe");
    }

    public static boolean hasShovel(ResourceLocation itemId, PrototypeCatalog<ToolProfile> catalog) {
        ToolProfile profile = itemId == null || catalog == null ? null : catalog.get(itemId);
        return profile != null && profile.shovel() != null;
    }

    public static double shovelSpeed(ResourceLocation itemId, PrototypeCatalog<ToolProfile> catalog) {
        ToolProfile profile = itemId == null || catalog == null ? null : catalog.get(itemId);
        return profile == null || profile.shovel() == null ? 0 : profile.shovel().speedModifier();
    }

    static double prySpeed(ResourceLocation itemId, PrototypeCatalog<ToolProfile> catalog) {
        if (!has(itemId, ToolProfile.Quality.PRYING, catalog)) return 0;
        ToolProfile profile = catalog.get(itemId);
        return profile.prying() == null ? 0 : profile.prying().speedModifier();
    }
}
