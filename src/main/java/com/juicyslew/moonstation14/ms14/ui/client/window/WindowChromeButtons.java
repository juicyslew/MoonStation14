package com.juicyslew.moonstation14.ms14.ui.client.window;

import java.util.Objects;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import net.minecraft.client.gui.GuiGraphics;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;

/** Factory for focusable vanilla title-bar controls; screens add and reposition the returned widgets. */
public final class WindowChromeButtons {
    public static final int SIZE = 18;
    public static final int EDGE = 5;
    public static final int GAP = 3;
    private static final Component CLOSE_ICON = Component.literal("×");
    private static final Component HELP_ICON = Component.literal("?");
    private static final Component CLOSE_LABEL = Component.translatableWithFallback("gui.close", "Close");
    private static final Component HELP_LABEL = Component.translatableWithFallback("gui.help", "Help");

    private WindowChromeButtons() {}

    /** The close action belongs to the screen (e.g. invoke onClose); no server request is sent here. */
    public static Button close(int windowX, int windowY, int windowWidth, Runnable onClose) {
        Objects.requireNonNull(onClose, "onClose");
        return button(windowX + windowWidth - EDGE - SIZE, windowY + EDGE, CLOSE_ICON, CLOSE_LABEL, onClose);
    }

    /** Pixel bounds of the standard close widget after a uniform GUI pose scale. */
    public static net.minecraft.client.gui.navigation.ScreenRectangle closeBounds(
            int windowX, int windowY, int windowWidth, float scale) {
        return new net.minecraft.client.gui.navigation.ScreenRectangle(
                Math.round(windowX + (windowWidth - EDGE - SIZE) * scale),
                Math.round(windowY + EDGE * scale), Math.round(SIZE * scale), Math.round(SIZE * scale));
    }

    /** Exact bounds shared by non-widget machine chrome painting and hit routing. */
    public static net.minecraft.client.gui.navigation.ScreenRectangle closeBounds(int x, int y, int width, int height) {
        return new net.minecraft.client.gui.navigation.ScreenRectangle(x,y,Math.max(0,width),Math.max(0,height));
    }

    public static boolean contains(net.minecraft.client.gui.navigation.ScreenRectangle bounds,double x,double y) {
        return x>=bounds.left()&&x<(long)bounds.left()+bounds.width()
                &&y>=bounds.top()&&y<(long)bounds.top()+bounds.height();
    }

    public static void paintClose(GuiGraphics graphics,net.minecraft.client.gui.navigation.ScreenRectangle bounds,
                                  int mouseX,int mouseY) {
        paintClose(new GuiGraphicsPaint(graphics,net.minecraft.client.Minecraft.getInstance().font),bounds,mouseX,mouseY);
    }
    public static void paintClose(OrderedPaint graphics,net.minecraft.client.gui.navigation.ScreenRectangle bounds,int mouseX,int mouseY) {
        MachineActionButtonRenderer.render(graphics,"×",bounds.left(),bounds.top(),bounds.width(),bounds.height(),
                contains(bounds,mouseX,mouseY),true,false,.65f);
    }

    /** Returns null when help is not supplied; callers should only register a non-null widget. */
    public static Button help(int windowX, int windowY, int windowWidth, Runnable onHelp) {
        if (onHelp == null) return null;
        return button(windowX + windowWidth - EDGE - SIZE * 2 - GAP, windowY + EDGE, HELP_ICON, HELP_LABEL, onHelp);
    }

    /** The visible title stays compact while narration and the focus tooltip use the full label. */
    public static Button button(int x, int y, Component title, Component tooltip, Runnable action) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(action, "action");
        return new ChromeButton(x,y,title,tooltip,action);
    }

    private static final class ChromeButton extends Button {
        private final String label;
        private ChromeButton(int x,int y,Component title,Component tooltip,Runnable action) {
            super(x,y,SIZE,SIZE,title,ignored->action.run(),ignored->AbstractWidget.wrapDefaultNarrationMessage(tooltip));
            this.label=title.getString();
            setTooltip(Tooltip.create(tooltip));
        }
        @Override public void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            MachineActionButtonRenderer.render(graphics,label,getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused());
        }
    }
}
