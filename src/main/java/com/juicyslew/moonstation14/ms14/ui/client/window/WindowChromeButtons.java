package com.juicyslew.moonstation14.ms14.ui.client.window;

import java.util.Objects;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

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
        return Button.builder(title, ignored -> action.run())
                .bounds(x, y, SIZE, SIZE)
                .tooltip(Tooltip.create(tooltip))
                .createNarration(ignored -> AbstractWidget.wrapDefaultNarrationMessage(tooltip))
                .build();
    }
}
