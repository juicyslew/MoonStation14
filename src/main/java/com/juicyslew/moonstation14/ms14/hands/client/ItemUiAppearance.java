package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Capability resolver for project-specific 2D item layers; null means use Minecraft's native model. */
@OnlyIn(Dist.CLIENT)
public final class ItemUiAppearance {
    public record SpriteLayers(ResourceLocation base, ResourceLocation overlay, int tint) { }
    @FunctionalInterface private interface Provider { SpriteLayers resolve(ItemStack stack); }
    private static final java.util.Map<ResourceLocation, Provider> PROVIDERS = java.util.Map.of(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "jug"), ItemUiAppearance::jug);
    private ItemUiAppearance() { }

    public static SpriteLayers resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Provider provider = PROVIDERS.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return provider == null ? null : provider.resolve(stack);
    }

    private static SpriteLayers jug(ItemStack stack) {
        var reagent = stack.get(ModDataComponents.REAGENT.get());
        int tier = com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance.fillTier(reagent, com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance.capacityCents());
        return new SpriteLayers(ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/item/jug.png"),
                ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/item/jug_" + tier + ".png"),
                com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance.color(reagent, net.minecraft.client.Minecraft.getInstance().level));
    }
}
