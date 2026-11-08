package com.juicyslew.moonstation14.ms14.actions.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.settings.KeyMappingLookup;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BodyActionModifierLookupTest {
    @Test
    void modifierBindingTakesPriorityOverPlainBinding() {
        assertLookupPriority(false, KeyModifier.NONE);
        assertLookupPriority(true, KeyModifier.SHIFT);
    }

    @Test
    void changedBindingUpdatesTheRegisteredModifierLookup() {
        KeyMapping open = mapping(KeyModifier.CONTROL);
        InputConstants.Key plainK = InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_K);
        when(open.getKey()).thenReturn(plainK);
        when(open.isActiveAndMatches(plainK)).thenReturn(true);
        KeyMappingLookup lookup = new KeyMappingLookup();
        lookup.put(plainK, open);
        Minecraft mc = mock(Minecraft.class);
        try (MockedStatic<Minecraft> minecraft = mockStatic(Minecraft.class);
             MockedStatic<Screen> screen = mockStatic(Screen.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mc);
            screen.when(Screen::hasControlDown).thenReturn(true);
            screen.when(Screen::hasShiftDown).thenReturn(false);
            screen.when(Screen::hasAltDown).thenReturn(false);
            assertEquals(List.of(open), lookup.getAll(plainK));
            screen.when(Screen::hasControlDown).thenReturn(false);
            assertEquals(List.of(), lookup.getAll(plainK));
        }
        lookup.remove(open);
    }

    private static void assertLookupPriority(boolean shiftDown, KeyModifier expected) {
        KeyMapping plain = mapping(KeyModifier.NONE);
        KeyMapping shifted = mapping(KeyModifier.SHIFT);
        KeyMappingLookup lookup = new KeyMappingLookup();
        InputConstants.Key probeKey = InputConstants.Type.KEYSYM.getOrCreate(1000);
        when(plain.getKey()).thenReturn(probeKey);
        when(shifted.getKey()).thenReturn(probeKey);
        when(plain.isActiveAndMatches(probeKey)).thenReturn(true);
        when(shifted.isActiveAndMatches(probeKey)).thenReturn(true);
        lookup.put(probeKey, plain);
        lookup.put(probeKey, shifted);

        Minecraft mc = mock(Minecraft.class);
        try (MockedStatic<Minecraft> minecraft = mockStatic(Minecraft.class);
             MockedStatic<Screen> screen = mockStatic(Screen.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(mc);
            screen.when(Screen::hasShiftDown).thenReturn(shiftDown);
            screen.when(Screen::hasControlDown).thenReturn(false);
            screen.when(Screen::hasAltDown).thenReturn(false);
            assertEquals(List.of(shiftDown ? shifted : plain), lookup.getAll(probeKey));
        } finally {
            lookup.remove(plain);
            lookup.remove(shifted);
        }
    }

    private static KeyMapping mapping(KeyModifier modifier) {
        KeyMapping mapping = mock(KeyMapping.class);
        when(mapping.getKeyModifier()).thenReturn(modifier);
        return mapping;
    }
}
