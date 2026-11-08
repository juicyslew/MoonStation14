package com.juicyslew.moonstation14.ms14.standing.client;

import net.neoforged.neoforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RollInputPolicyTest {
    @Test void liveQueueKeepsPressAcrossReleaseAndMenuChordDoesNotEnqueue() {
        var input = RollInputPolicy.pending();
        int shift = org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT, ctrl = org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
        input.keyEvent(org.lwjgl.glfw.GLFW.GLFW_PRESS, shift, KeyModifier.NONE, false);
        input.keyEvent(org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0, KeyModifier.NONE, false);
        assertEquals(RollInputPolicy.Intent.OTHER, input.poll());
        input.keyEvent(org.lwjgl.glfw.GLFW.GLFW_PRESS, 0, KeyModifier.NONE, false);
        input.keyEvent(org.lwjgl.glfw.GLFW.GLFW_REPEAT, 0, KeyModifier.NONE, false);
        input.keyEvent(org.lwjgl.glfw.GLFW.GLFW_PRESS, ctrl, KeyModifier.NONE, true);
        assertEquals(RollInputPolicy.Intent.SELF, input.poll());
        assertEquals(RollInputPolicy.Intent.NONE, input.poll());
    }
    @Test void pendingRollIsClearedWhenGameplayAdmissionIsLost() {
        var input = RollInputPolicy.pending();
        input.keyEvent(org.lwjgl.glfw.GLFW.GLFW_PRESS, 0, KeyModifier.NONE, false);
        input.clear();
        assertEquals(RollInputPolicy.Intent.NONE, input.poll());
    }
    @Test void pendingIntentCannotCrossLevelIdentityButSurvivesSameLevelTickSync() {
        var input = RollInputPolicy.pending();
        Object levelA = new Object(), levelB = new Object();
        input.syncContext(levelA);
        input.keyEvent(org.lwjgl.glfw.GLFW.GLFW_PRESS, 0, KeyModifier.NONE, false);
        input.syncContext(levelB);
        assertEquals(RollInputPolicy.Intent.NONE, input.poll());
        input.keyEvent(org.lwjgl.glfw.GLFW.GLFW_PRESS, 0, KeyModifier.NONE, false);
        input.syncContext(levelB);
        assertEquals(RollInputPolicy.Intent.SELF, input.poll());
    }
    @Test void defaultKReservesControlForMenuAndShiftForOtherBody() {
        assertEquals(RollInputPolicy.Intent.SELF, RollInputPolicy.classify(0, KeyModifier.NONE));
        assertEquals(RollInputPolicy.Intent.OTHER, RollInputPolicy.classify(org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT, KeyModifier.NONE));
        assertEquals(RollInputPolicy.Intent.NONE, RollInputPolicy.classify(org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL, KeyModifier.NONE));
    }
    @Test void configuredModifierVariantsRemainUsableAndMismatchesAreIgnored() {
        int ctrl = org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL, shift = org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
        assertEquals(RollInputPolicy.Intent.SELF, RollInputPolicy.classify(ctrl, KeyModifier.CONTROL));
        assertEquals(RollInputPolicy.Intent.OTHER, RollInputPolicy.classify(ctrl | shift, KeyModifier.CONTROL));
        assertEquals(RollInputPolicy.Intent.NONE, RollInputPolicy.classify(0, KeyModifier.CONTROL));
        assertEquals(RollInputPolicy.Intent.NONE, RollInputPolicy.classify(ctrl, KeyModifier.NONE));
    }
}
