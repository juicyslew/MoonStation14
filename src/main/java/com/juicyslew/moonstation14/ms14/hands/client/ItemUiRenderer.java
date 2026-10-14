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

    /** Draw one sanitized stack fully contained by a shared inventory tile. */
    public static void renderIntoSlot(GuiGraphics graphics, ItemStack stack, int count,
                                      int x, int y, int width, int height, net.minecraft.client.gui.Font font) {
        if (stack == null || stack.isEmpty() || width < 3 || height < 3) return;
        float scale=Math.min(1f,Math.min((width-2)/16f,(height-2)/16f));
        if(scale>0){var pose=graphics.pose();pose.pushPose();pose.translate(x+width/2f,y+height/2f,0);pose.scale(scale,scale,1);render(graphics,stack,-8,-8);pose.popPose();}
        if(count>1&&font!=null){String label=Integer.toString(count);var pose=graphics.pose();pose.pushPose();pose.translate(x+width-2,y+height-font.lineHeight*.62f,0);pose.scale(.62f,.62f,1);graphics.drawString(font,label,-font.width(label),0,0xffffffff,true);pose.popPose();}
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

