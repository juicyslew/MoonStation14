package com.juicyslew.moonstation14.ms14.actions.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BodyActionLegacyBindingMigrationTest {
    private static final int K = org.lwjgl.glfw.GLFW.GLFW_KEY_K;

    @Test
    void migratesLoadedLegacyKOnlyOnceAndKeepsCustomBindings() throws Exception {
        KeyMapping open = open();
        boolean priorChecked = checked();
        InputConstants.Key priorKey = open.getKey();
        KeyModifier priorModifier = open.getKeyModifier();
        try {
            setChecked(false);
            open.setKeyModifierAndCode(KeyModifier.NONE, key(K));
            field(KeyMapping.class, "clickCount").setInt(open, 1);
            Minecraft mc = mock(Minecraft.class);
            Options options = mock(Options.class);
            setOptions(mc, options);
            setKeyMappings(options, new KeyMapping[]{open});
            try (MockedStatic<Minecraft> minecraft = mockStatic(Minecraft.class);
                 MockedStatic<Screen> screen = mockStatic(Screen.class)) {
                minecraft.when(Minecraft::getInstance).thenReturn(mc);
                screen.when(Screen::hasControlDown).thenReturn(true);
                screen.when(Screen::hasShiftDown).thenReturn(false);
                screen.when(Screen::hasAltDown).thenReturn(false);
                BodyActionKeys.normalizeLegacyOpenBinding();
                assertEquals(KeyModifier.CONTROL, open.getKeyModifier());
                assertEquals(K, open.getKey().getValue());
                assertFalse(BodyActionKeys.matches(K, 0, 0));
                assertTrue(BodyActionKeys.matches(K, 0, 2));
                assertFalse(open.consumeClick(), "legacy queued activation was drained");
                verify(options, times(1)).save();

                open.setKeyModifierAndCode(KeyModifier.NONE, key(K));
                BodyActionKeys.normalizeLegacyOpenBinding();
                assertEquals(KeyModifier.NONE, open.getKeyModifier(), "migration must not override a later explicit rebind");
                assertEquals(K, open.getKey().getValue());
                verify(options, times(1)).save();

                open.setKeyModifierAndCode(KeyModifier.NONE, key(org.lwjgl.glfw.GLFW.GLFW_KEY_J));
                BodyActionKeys.normalizeLegacyOpenBinding();
                assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_J, open.getKey().getValue());
                verify(options, times(1)).save();
            }
        } finally {
            open.setKeyModifierAndCode(priorModifier, priorKey);
            open.resetMapping();
            setChecked(priorChecked);
        }
    }

    @Test
    void preservesCustomAndAlreadyMigratedBindingAndWaitsForRegistration() throws Exception {
        KeyMapping open = open();
        boolean priorChecked = checked();
        InputConstants.Key priorKey = open.getKey();
        KeyModifier priorModifier = open.getKeyModifier();
        try {
            Minecraft mc = mock(Minecraft.class);
            Options options = mock(Options.class);
            setOptions(mc, options);
            try (MockedStatic<Minecraft> minecraft = mockStatic(Minecraft.class)) {
                minecraft.when(Minecraft::getInstance).thenReturn(mc);
                setKeyMappings(options, new KeyMapping[0]);
                setChecked(false);
                open.setKeyModifierAndCode(KeyModifier.NONE, key(K));
                BodyActionKeys.normalizeLegacyOpenBinding();
                assertFalse(checked());
                setKeyMappings(options, new KeyMapping[]{open});
                BodyActionKeys.normalizeLegacyOpenBinding();
                assertEquals(KeyModifier.CONTROL, open.getKeyModifier());
                verify(options).save();

                setChecked(false);
                open.setKeyModifierAndCode(KeyModifier.NONE, key(org.lwjgl.glfw.GLFW.GLFW_KEY_J));
                BodyActionKeys.normalizeLegacyOpenBinding();
                assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_J, open.getKey().getValue());
                setChecked(false);
                open.setKeyModifierAndCode(KeyModifier.CONTROL, key(K));
                reset(options);
                BodyActionKeys.normalizeLegacyOpenBinding();
                verify(options, never()).save();

                setChecked(false);
                open.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
                BodyActionKeys.normalizeLegacyOpenBinding();
                assertTrue(open.isUnbound());
                verify(options, never()).save();
            }
        } finally {
            open.setKeyModifierAndCode(priorModifier, priorKey);
            setChecked(priorChecked);
        }
    }

    private static InputConstants.Key key(int code) { return InputConstants.Type.KEYSYM.getOrCreate(code); }
    private static KeyMapping open() throws Exception { return (KeyMapping) field(BodyActionKeys.class, "OPEN").get(null); }
    private static boolean checked() throws Exception { return field(BodyActionKeys.class, "legacyBindingChecked").getBoolean(null); }
    private static void setChecked(boolean value) throws Exception { field(BodyActionKeys.class, "legacyBindingChecked").setBoolean(null, value); }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static void setOptions(Minecraft minecraft, Options options) throws Exception { field(Minecraft.class, "options").set(minecraft, options); }
    private static void setKeyMappings(Options options, KeyMapping[] mappings) throws Exception { field(Options.class, "keyMappings").set(options, mappings); }
}
