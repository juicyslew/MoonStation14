package com.juicyslew.moonstation14.ms14.ui.client;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/** Screen phases where the body HUD and its persistent gameplay windows remain visible. */
public final class GameplayHudPolicy {
    private GameplayHudPolicy() { }

    public static boolean visible(Screen screen) {
        return screen == null || screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen
                || screen instanceof ChatScreen;
    }

    /** The storage host paints its workspace itself; the HUD paints it in gameplay/chat phases. */
    public static boolean hudPaintsWorkspace(Screen screen) {
        return screen == null || screen instanceof ChatScreen;
    }

    /** The storage host paints the wire view itself, avoiding a duplicate HUD pass. */
    public static boolean hudPaintsWire(Screen screen) {
        return screen == null || screen instanceof ChatScreen;
    }
}

