package com.juicyslew.moonstation14.ms14.ui.window;

import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowChromeButtonsTest {
    @Test
    void closeUsesCompactIconAndOnlyInvokesSuppliedActionOnPress() {
        AtomicInteger closes = new AtomicInteger();
        Button close = WindowChromeButtons.close(40, 30, 180, closes::incrementAndGet);

        assertEquals("×", close.getMessage().getString());
        assertNarration(close, "Close");
        assertEquals(40 + 180 - WindowChromeButtons.EDGE - WindowChromeButtons.SIZE, close.getX());
        assertEquals(30 + WindowChromeButtons.EDGE, close.getY());
        assertEquals(WindowChromeButtons.SIZE, close.getWidth());
        assertEquals(WindowChromeButtons.SIZE, close.getHeight());
        assertEquals(0, closes.get());

        close.onPress();
        assertEquals(1, closes.get());
    }

    @Test
    void helpIsOptionalAndUsesCompactIconWithDescriptiveTooltip() {
        assertNull(WindowChromeButtons.help(40, 30, 180, null));
        AtomicInteger helps = new AtomicInteger();
        Button help = WindowChromeButtons.help(40, 30, 180, helps::incrementAndGet);

        assertEquals("?", help.getMessage().getString());
        assertNarration(help, "Help");
        assertEquals(40 + 180 - WindowChromeButtons.EDGE - WindowChromeButtons.SIZE * 2 - WindowChromeButtons.GAP,
                help.getX());
        assertEquals(30 + WindowChromeButtons.EDGE, help.getY());
        assertEquals(0, helps.get());
        help.onPress();
        assertEquals(1, helps.get());
    }

    @Test
    void closeRequiresCallback() {
        assertThrows(NullPointerException.class, () -> WindowChromeButtons.close(0, 0, 100, null));
    }

    private static void assertNarration(Button button, String label) {
        StringBuilder title = new StringBuilder();
        StringBuilder hint = new StringBuilder();
        button.updateNarration(new NarrationElementOutput() {
            @Override
            public void add(NarratedElementType type, NarrationThunk<?> contents) {
                if (type == NarratedElementType.TITLE) contents.getText(title::append);
                if (type == NarratedElementType.HINT) contents.getText(hint::append);
            }

            @Override
            public NarrationElementOutput nest() {
                return this;
            }
        });
        assertTrue(title.toString().contains(label), title.toString());
        assertEquals(label, hint.toString());
    }
}
