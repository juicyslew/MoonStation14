package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GameplayWindowMovementDispatchTest {
    private static final class TextOwningWindow extends Screen implements GameplayWindow, BodyMovementIntentScreen {
        private final AtomicInteger samples = new AtomicInteger();
        private boolean captures = true;
        private TextOwningWindow() { super(Component.literal("test")); }
        @Override public boolean capturesKeyboard() { return captures; }
        private PopupMovementIntent.State movementIntentSample() {
            samples.incrementAndGet();
            return new PopupMovementIntent.State(1, 1, true, false, true);
        }
        @Override public PopupMovementIntent.State movementIntent() { return movementIntentSample(); }
    }

    @Test void keyboardCaptureSendsNeutralThenResumesWindowMovementSampling() {
        var screen = new TextOwningWindow();
        var ordinary = new PopupMovementIntent.State(0, 1, true, false, true);
        var sent = new AtomicInteger();
        var captured = GameplayWindowMovementDispatch.dispatch(4, 5, MobHarnessKind.CHARACTER, screen, ordinary,
                90, 10, intent -> true, ignored -> sent.incrementAndGet());
        assertEquals(0, captured.wishX());
        assertEquals(0, captured.wishZ());
        assertEquals(0, captured.buttons());
        assertEquals(0, screen.samples.get(), "keyboard focus must not poll gameplay window movement");
        assertEquals(1, sent.get(), "send neutral intent, do not leave server's previous movement active");

        screen.captures = false;
        var resumed = GameplayWindowMovementDispatch.dispatch(4, 6, MobHarnessKind.CHARACTER, screen, ordinary,
                90, 10, intent -> true, ignored -> sent.incrementAndGet());
        assertTrue(resumed.wishX() > 0);
        assertTrue(resumed.wishZ() > 0);
        assertEquals(1, screen.samples.get());
        assertEquals(2, sent.get());
    }
}
