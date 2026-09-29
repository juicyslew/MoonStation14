package com.juicyslew.moonstation14.ms14.ui;

import com.juicyslew.moonstation14.ms14.ui.client.MachineSwitch;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineSwitchTest {
    @Test
    void soundHookDoesNotRequireSoundManagerOrSendIntent() {
        List<Boolean> requests = new ArrayList<>();
        MachineSwitch widget = createSwitch(requests);

        widget.playDownSound(null);
        assertTrue(requests.isEmpty());
        assertFalse(widget.presentedOn());

        widget.onPress();
        assertEquals(List.of(true), requests);
        assertFalse(widget.presentedOn()); // Requests do not replace authoritative state.

        widget.setPresentedState(true, true);
        widget.playDownSound(null);
        widget.onPress();
        assertEquals(List.of(true, false), requests);
    }

    @Test
    void disabledSwitchDoesNotSendIntentOrChangePresentation() {
        List<Boolean> requests = new ArrayList<>();
        MachineSwitch widget = createSwitch(requests);
        widget.setPresentedState(false, false);

        widget.playDownSound(null);
        widget.onPress();

        assertTrue(requests.isEmpty());
        assertFalse(widget.presentedOn());
        assertFalse(widget.active);
    }

    private static MachineSwitch createSwitch(List<Boolean> requests) {
        return new MachineSwitch(0, 0, 120, 20, Component.literal("Breaker"),
                Component.literal("On"), Component.literal("Off"), false, requests::add);
    }
}
