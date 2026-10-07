package com.juicyslew.moonstation14.ms14.ui.client;

import com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent;

/** Non-modal gameplay screen contract used to retain character movement while a window is open. */
public interface GameplayWindow {
    PopupMovementIntent.State movementIntent();

    /** True only while a text editor owns keyboard input. */
    boolean capturesKeyboard();
}
