package com.juicyslew.moonstation14.ms14.ui.client.pointer;

import net.minecraft.client.gui.GuiGraphics;

/** Small seam for windows hosted by the existing transparent pointer workspace. */
public interface PointerOverlayWindow {
    boolean visible();
    boolean contains(double x, double y);
    void renderIntoGui(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int width, int height);
    boolean mouseClicked(double x, double y, int button);
    boolean mouseDragged(double x, double y);
    boolean mouseReleased(int button);
    boolean mouseScrolled(double x, double y, double delta);
    boolean keyPressed(int key, int scan, int modifiers);
    boolean charTyped(char value, int modifiers);
    boolean isSearchFocused();
    void clearTextFocus();
    void resetOwnedContext();
}
