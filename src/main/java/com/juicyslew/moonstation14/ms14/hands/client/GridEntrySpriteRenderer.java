package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Draws item artwork in the bounding box owned by a grid placement. */
final class GridEntrySpriteRenderer {
    private static final ResourceLocation PICKAXE = ResourceLocation.fromNamespaceAndPath(
            "moonstation14", "textures/item/pickaxe.png");
    private static final String PICKAXE_ID = "moonstation14:pickaxe";
    private static final int CELL = StoragePopupLayout.CELL_SIZE, PADDING = 1;

    private GridEntrySpriteRenderer() { }

    static void draw(net.minecraft.client.gui.GuiGraphics graphics, String itemId, int count, GridGeometry.Footprint footprint,
                     GridGeometry.Rotation rotation, int left, int top) {
        var bounds = footprint.cells(rotation);
        int footprintWidth = (bounds.stream().mapToInt(GridGeometry.Cell::x).max().orElse(0) + 1) * CELL;
        int footprintHeight = (bounds.stream().mapToInt(GridGeometry.Cell::y).max().orElse(0) + 1) * CELL;
        var cells = bounds;
        int columns = cells.stream().mapToInt(GridGeometry.Cell::x).max().orElse(0) + 1;
        int rows = cells.stream().mapToInt(GridGeometry.Cell::y).max().orElse(0) + 1;
        int width = columns * CELL - PADDING * 2, height = rows * CELL - PADDING * 2;
        int x = left + PADDING, y = top + PADDING;
        if (PICKAXE_ID.equals(itemId)) {
            var fit = StorageSpriteGeometry.fit(rotation.degrees());
            graphics.pose().pushPose();
            graphics.pose().translate(x + width / 2f + fit.offsetX(), y + height / 2f + fit.offsetY(), 0);
            graphics.pose().scale((float) fit.scale(), (float) fit.scale(), 1);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation.degrees() - 45));
            graphics.pose().translate(-16, -16, 0);
            graphics.blit(PICKAXE, 0, 0, 32, 32, 0, 0, 32, 32, 32, 32);
            graphics.pose().popPose();
        } else {
            graphics.enableScissor(left, top, left + footprintWidth, top + footprintHeight);
            var id = ResourceLocation.tryParse(itemId);
            if (id != null) {
                var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    float scale = Math.min(width, height) / 16f;
                    graphics.pose().pushPose();
                    graphics.pose().translate(x + width / 2f, y + height / 2f, 0);
                    graphics.pose().scale(scale, scale, 1);
                    graphics.renderItem(new ItemStack(item), -8, -8);
                    graphics.pose().popPose();
                }
            }
        }
        if (!PICKAXE_ID.equals(itemId)) graphics.disableScissor();
        if (count > 1) {
            // Vanilla's count label is 9px high, larger than this compact cell. Keep it
            // inside the placement rather than letting it paint into neighboring items.
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String label = Integer.toString(count);
            if (!PICKAXE_ID.equals(itemId)) graphics.enableScissor(left, top, left + footprintWidth, top + footprintHeight);
            graphics.pose().pushPose();
            graphics.pose().translate(x + width - 1, y + height - 1, 0);
            graphics.pose().scale(.4f, .4f, 1);
            graphics.drawString(font, label, -font.width(label), -font.lineHeight, 0xffffffff, true);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }
}
