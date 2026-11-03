package com.juicyslew.moonstation14.ms14.actions.client;

import com.mojang.blaze3d.platform.InputConstants;
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

        try (MockedStatic<Screen> screen = mockStatic(Screen.class)) {
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
