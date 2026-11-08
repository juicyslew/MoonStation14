package com.juicyslew.moonstation14.ms14.ui.client;

import com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent;

/** Non-modal gameplay screen contract used to retain character movement while a window is open. */
public interface GameplayWindow {
    PopupMovementIntent.State movementIntent();

    /** True only while a text editor owns keyboard input. */
    boolean capturesKeyboard();

    /** Shared gate used by the live body-input tick: non-modal windows keep actions available. */
    static boolean allowsBodyInput(net.minecraft.client.gui.screens.Screen screen,boolean mouseGrabbed) {
        if (screen == null) return mouseGrabbed;
        return screen instanceof GameplayWindow window && !window.capturesKeyboard();
    }
}

