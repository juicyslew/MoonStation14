package com.juicyslew.moonstation14.ms14.ui.client;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameplayHudPolicyTest {
    @Test void bodyHudAndPersistentWindowsHaveOneOwnerPerGameplayPhase() {
        assertTrue(GameplayHudPolicy.visible(null));
        assertTrue(GameplayHudPolicy.visible(new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen(
                new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupWorkspace(320, 240))));
        assertTrue(GameplayHudPolicy.visible(new ChatScreen("")));
        assertFalse(GameplayHudPolicy.visible(new Screen(net.minecraft.network.chat.Component.literal("settings")) { }));

        assertTrue(GameplayHudPolicy.hudPaintsWorkspace(null));
        assertFalse(GameplayHudPolicy.hudPaintsWorkspace(new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen(
                new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupWorkspace(320, 240))));
        assertTrue(GameplayHudPolicy.hudPaintsWorkspace(new ChatScreen("")));
        assertTrue(GameplayHudPolicy.hudPaintsWire(null));
        assertTrue(GameplayHudPolicy.hudPaintsWire(new ChatScreen("")));
    }
}
