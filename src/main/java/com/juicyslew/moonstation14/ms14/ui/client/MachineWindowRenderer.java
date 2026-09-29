package com.juicyslew.moonstation14.ms14.ui.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Pixel-coordinate FancyWindow-inspired primitives. Coordinates are already GUI-scaled by Minecraft. */
public final class MachineWindowRenderer {
    public static final int TEXT = 0xffe5ecee;
    public static final int MUTED_TEXT = 0xffb0bec2;
    private static final int FRAME = 0xff11191d;
    private static final int BORDER = 0xff60747c;
    private static final int FACE = 0xff35474e;
    private static final int HEADER = 0xff26363d;
    private static final int INSET = 0xff141f24;
    private static final int TRACK = 0xff090f12;
    private static final int HIGHLIGHT = 0xff82959b;
    private static final String[] PERCENT_LABELS = new String[101];

    static {
        for (int i = 0; i <= 100; i++) PERCENT_LABELS[i] = i + "%";
    }

    private MachineWindowRenderer() {}

    /** Paint the complete frame and title bar; leave content placement to the screen. */
    public static void window(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                              int x, int y, int width, int height, Component title) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, BORDER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, FACE);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 26, HEADER);
        divider(graphics, x + 3, y + 26, width - 6);
        graphics.drawString(font, title, x + 10, y + 10, TEXT, true);
    }

    /** Recessed panel, including a one-pixel rim. */
    public static void insetPanel(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int width, int height) {
        if (width < 3 || height < 3) return;
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, INSET);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, TRACK);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, BORDER);
    }

    public static void divider(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int width) {
        if (width <= 0) return;
        graphics.fill(x, y, x + width, y + 1, FRAME);
        graphics.fill(x, y + 1, x + width, y + 2, HIGHLIGHT);
    }

    /** Inset footer strip with text at the left edge. */
    public static void footer(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                              int x, int y, int width, int height, Component text) {
        insetPanel(graphics, x, y, width, height);
        if (height >= font.lineHeight + 4)
            graphics.drawString(font, text, x + 5, y + (height - font.lineHeight) / 2, MUTED_TEXT, true);
    }

    /** Label on the left, current value right-aligned inside the supplied width. */
    public static void statusRow(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                 int x, int y, int width,
                                 Component label, Component value, int valueColor) {
        graphics.drawString(font, label, x, y, MUTED_TEXT, true);
        graphics.drawString(font, value, x + width - font.width(value), y, valueColor, true);
    }

    /** Authoritative percent is shown inside the bar; fill may be eased separately by the caller. */
    public static void chargeMeter(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                   int x, int y, int width, int height,
                                   int authoritativePermille, float displayedPermille) {
        if (width < 4 || height < font.lineHeight + 4) return;
        insetPanel(graphics, x, y, width, height);
        int inside = width - 4;
        int filled = MachineMeterMath.fillWidth(inside, displayedPermille);
        if (filled > 0) graphics.fill(x + 2, y + 2, x + 2 + filled, y + height - 2,
                MachineMeterMath.chargeColor(authoritativePermille));
        String label = PERCENT_LABELS[MachineMeterMath.percent(authoritativePermille)];
        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - font.lineHeight) / 2;
        graphics.drawString(font, label, textX + 1, textY + 1, FRAME, false);
        graphics.drawString(font, label, textX, textY, TEXT, false);
    }

    /** Dark preview well with an optional, centered square sprite (texture dimensions are supplied by caller). */
    public static void imagePane(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int width, int height,
                                 ResourceLocation texture, int textureWidth, int textureHeight, int spriteSize) {
        insetPanel(graphics, x, y, width, height);
        if (texture == null || textureWidth <= 0 || textureHeight <= 0 || spriteSize <= 0) return;
        int size = Math.min(spriteSize, Math.min(width - 4, height - 4));
        int sourceSize = Math.min(textureWidth, textureHeight);
        if (size > 0) graphics.blit(texture, x + (width - size) / 2, y + (height - size) / 2,
                size, size, 0.0f, 0.0f, sourceSize, sourceSize, textureWidth, textureHeight);
    }
}
