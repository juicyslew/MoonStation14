package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AimReleaseWorldDumpTest {
    private final AimReleaseWorldDump gesture = new AimReleaseWorldDump();
    private final UUID source = UUID.randomUUID(), machine = UUID.randomUUID(), body = UUID.randomUUID();

    @Test void sameAimIsOriginalAlternateUseAndMouseUpIsConsumed() {
        assertEquals(AimReleaseWorldDump.Press.CAPTURE, gesture.press(true, false, true, source, body, 4));
        assertEquals(AimReleaseWorldDump.Release.ORIGINAL_ALTERNATE_USE,
                gesture.release(source, false, source, source, body, 4));
        assertTrue(gesture.consumeMouseUp());
        assertFalse(gesture.consumeMouseUp());
    }

    @Test void onlyChangedAimToValidMachineDumpsAndActiveCameraAimDoesNotCancel() {
        gesture.press(true, false, true, source, body, 4);
        gesture.aim(machine);
        assertEquals(AimReleaseWorldDump.Release.DUMP, gesture.release(machine, true, null, source, body, 4));
    }

    @Test void originalRoutingAndPointerPressAreNotCaptured() {
        var ineligible = gesture.press(true, false, false, source, body, 4);
        assertEquals(AimReleaseWorldDump.Press.ROUTE_ORIGINAL, ineligible);
        assertFalse(AimReleaseWorldDump.consumesWorldPress(ineligible));
        var pointerOwned = gesture.press(true, true, true, source, body, 4);
        assertEquals(AimReleaseWorldDump.Press.IGNORE, pointerOwned);
        assertFalse(AimReleaseWorldDump.consumesWorldPress(pointerOwned));
        assertFalse(gesture.active());
    }

    @Test void capturedWorldPressIsConsumedBeforeExamineFallback() {
        var captured = gesture.press(true, false, true, source, body, 4);
        assertEquals(AimReleaseWorldDump.Press.CAPTURE, captured);
        assertTrue(AimReleaseWorldDump.consumesWorldPress(captured));
    }

    @Test void configuredBindingSupportsDefaultAndReboundMouseButNeverStealsAltE() {
        // Physical mapping match is supplied by the configured KeyMapping, not hardcoded Alt+left polling.
        assertTrue(AimReleaseWorldDump.configuredBindingMatches(true, true, false)); // default Shift+left
        assertTrue(AimReleaseWorldDump.configuredBindingMatches(true, true, false)); // rebound modified mouse
        assertFalse(AimReleaseWorldDump.configuredBindingMatches(true, false, false));
        assertFalse(AimReleaseWorldDump.configuredBindingMatches(true, true, true)); // legacy keyboard Alt+E stays routed
    }

    @Test void fallbackRequiresTheCapturedDefaultShiftPhysicalBinding() {
        var input = new AimReleasePhysicalInput();
        input.beginMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        assertEquals(AimReleaseWorldDump.Press.CAPTURE, gesture.press(true, false, true, source, body, 4));
        gesture.aim(source);
        assertEquals(AimReleaseWorldDump.Release.ORIGINAL_ALTERNATE_USE,
                gesture.release(source, false, source, source, body, 4));
        assertFalse(input.legacyFallback());
        input.beginMouse(2, false);
        assertFalse(input.legacyFallback());
    }

    @Test void capturedUuidBodyEpochAndModifierAreValidated() {
        gesture.press(true, false, true, source, body, 4);
        gesture.aim(machine);
        assertEquals(AimReleaseWorldDump.Release.CANCEL, gesture.release(machine, true, null, UUID.randomUUID(), body, 4));
        gesture.press(true, false, true, source, body, 4);
        gesture.aim(machine);
        gesture.modifierPressed();
        assertEquals(AimReleaseWorldDump.Release.CANCEL, gesture.release(machine, true, null, source, body, 4));
        gesture.press(true, false, true, source, body, 4);
        assertEquals(AimReleaseWorldDump.Release.CANCEL, gesture.release(machine, true, null, source, UUID.randomUUID(), 4));
    }

    @Test void escapeCancelsWithoutTouchingWindowState() {
        gesture.press(true, false, true, source, body, 4);
        assertEquals(AimReleaseWorldDump.Release.CANCEL, gesture.escape());
        assertFalse(gesture.active());
        assertTrue(gesture.consumeMouseUp());
        assertFalse(gesture.consumeMouseUp());
    }
}
