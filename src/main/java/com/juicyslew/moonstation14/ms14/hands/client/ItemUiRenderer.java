package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** The shared entry point for rendering item icons in project-owned interfaces. */
@OnlyIn(Dist.CLIENT)
public final class ItemUiRenderer {
    private ItemUiRenderer() { }

    public static void render(GuiGraphics graphics, ItemStack presentationStack, int x, int y) {
        if (presentationStack == null || presentationStack.isEmpty()) return;
        var layers = ItemUiAppearance.resolve(presentationStack);
        if (layers == null) graphics.renderItem(presentationStack, x, y);
        else {
            graphics.blit(layers.base(), x, y, 0, 0, 16, 16, 16, 16);
            int tint = layers.tint();
            graphics.setColor(((tint >>> 16) & 255) / 255f, ((tint >>> 8) & 255) / 255f,
                    (tint & 255) / 255f, ((tint >>> 24) & 255) / 255f);
            graphics.blit(layers.overlay(), x, y, 0, 0, 16, 16, 16, 16);
            graphics.setColor(1f, 1f, 1f, 1f);
        }
    }

    public static ItemStack stack(String itemId, int count, ItemPresentation presentation) {
        if (presentation != null) return presentation.stack();
        var id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        var item = id == null ? net.minecraft.world.item.Items.BARRIER
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(net.minecraft.world.item.Items.BARRIER);
        ItemStack stack = new ItemStack(item);
        if (count > 1) stack.setCount(Math.min(count, stack.getMaxStackSize()));
        return stack;
    }

    public static void render(GuiGraphics graphics, String itemId, int count, ItemPresentation presentation, int x, int y) {
        render(graphics, stack(itemId, count, presentation), x, y);
    }

}
