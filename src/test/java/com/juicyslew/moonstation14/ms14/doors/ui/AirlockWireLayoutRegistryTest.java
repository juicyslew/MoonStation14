package com.juicyslew.moonstation14.ms14.doors.ui;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AirlockWireLayoutRegistryTest {
    private static final List<String> ACTIONS = List.of("power_one", "power_two", "log", "bolt", "bolt_light", "timing", "safety", "ai_interact");

    @Test void layoutIsSharedAndClientRowsRevealOnlyAppearance() {
        var registry = new AirlockWireLayoutRegistry(new Random(41));
        var one = AirlockState.initial(java.util.UUID.randomUUID(), true, true);
        var anotherDoor = AirlockState.initial(java.util.UUID.randomUUID(), true, true);
        var first = registry.describe("standard", ACTIONS, one);
        var second = registry.describe("standard", ACTIONS, anotherDoor);
        assertEquals(first, second);
        assertEquals(8, first.size());
        assertEquals(8, first.stream().map(AirlockWireLayoutRegistry.WireAppearance::rowId).distinct().count());
        assertEquals(8, first.stream().map(AirlockWireLayoutRegistry.WireAppearance::argb).distinct().count());
        assertEquals(8, first.stream().map(AirlockWireLayoutRegistry.WireAppearance::letter).distinct().count());
        for (var row : first) assertNotNull(registry.resolve("standard", row.rowId()));
        assertNull(registry.resolve("standard", 8));
        assertNull(registry.resolve("unknown-layout", 0));
    }

    @Test void sameLayoutIdentityHasSameFunctionColorAcrossDoorsAndCutChangesOnlyCosmeticFlag() {
        var registry = new AirlockWireLayoutRegistry(new Random(7));
        var original = AirlockState.initial(java.util.UUID.randomUUID(), true, true);
        var cut = original.applyWire(AirlockState.Wire.SAFETY, AirlockState.WireAction.CUT, 1);
        var first = registry.describe("standard", ACTIONS, original);
        var secondDoor = AirlockState.initial(java.util.UUID.randomUUID(), true, true);
        assertEquals(first, registry.describe("standard", ACTIONS, secondDoor));
        var changed = registry.describe("standard", ACTIONS, cut);
        assertEquals(first.stream().map(r -> List.of(r.rowId(), r.argb(), r.letter())).toList(),
                changed.stream().map(r -> List.of(r.rowId(), r.argb(), r.letter())).toList());
        assertEquals(1, changed.stream().filter(AirlockWireLayoutRegistry.WireAppearance::cut).count());
    }

    @Test void layoutsRejectUnsupportedCompositionAndInvalidOpaqueSelectors() {
        var registry = new AirlockWireLayoutRegistry(new Random(8));
        var state = AirlockState.initial(java.util.UUID.randomUUID(), true, true);
        assertThrows(IllegalArgumentException.class, () -> registry.describe("bad", List.of("power_one", "log"), state));
        assertThrows(IllegalArgumentException.class, () -> registry.describe("dup", List.of("power_one", "power_one", "bolt", "bolt_light", "timing", "safety", "log", "ai_interact"), state));
        assertThrows(IllegalArgumentException.class, () -> registry.describe("unknown", null, state));
    }

    @Test void inactiveSlotsTrackOnlyPhysicalCutsAndNeverAffectImplementedControls() {
        var initial = AirlockState.initial(java.util.UUID.randomUUID(), true, true).withPower(true);
        var cut = initial.applyWire(AirlockState.Wire.AI_INTERACT, AirlockState.WireAction.CUT, 1);
        assertTrue(cut.aiInteractWireCut());
        assertFalse(cut.logWireCut());
        assertEquals(initial.powered(), cut.powered());
        assertEquals(initial.safety(), cut.safety());
        assertEquals(initial.boltsDown(), cut.boltsDown());
        var uncutPulse = initial.applyWire(AirlockState.Wire.AI_INTERACT, AirlockState.WireAction.PULSE, 1);
        assertFalse(uncutPulse.aiInteractWireCut(), "placeholder pulse does not cut or mend an uncut wire");
        var afterOrdinaryStateMutation = cut.withPower(false).bumpRevision();
        assertTrue(afterOrdinaryStateMutation.aiInteractWireCut(), "ordinary root revisions preserve inactive physical cuts");
        var pulsed = cut.applyWire(AirlockState.Wire.AI_INTERACT, AirlockState.WireAction.PULSE, 2);
        assertTrue(pulsed.aiInteractWireCut());
        assertEquals(cut.powerAvailable(), pulsed.powerAvailable());
        var mended = pulsed.applyWire(AirlockState.Wire.AI_INTERACT, AirlockState.WireAction.MEND, 3);
        assertFalse(mended.aiInteractWireCut());
        var logCut = mended.applyWire(AirlockState.Wire.LOG, AirlockState.WireAction.CUT, 4);
        assertTrue(logCut.logWireCut());
        assertFalse(logCut.aiInteractWireCut());
        var logPulsed = logCut.applyWire(AirlockState.Wire.LOG, AirlockState.WireAction.PULSE, 5);
        assertTrue(logPulsed.logWireCut(), "placeholder pulse cannot mend a cut Log wire");
        assertEquals(logCut.powerAvailable(), logPulsed.powerAvailable());
        assertEquals(logCut.boltsDown(), logPulsed.boltsDown());
        assertTrue(logPulsed.withPower(true).logWireCut(), "power changes preserve the persisted log-wire cut bit");
        var logMended = logPulsed.applyWire(AirlockState.Wire.LOG, AirlockState.WireAction.MEND, 6);
        assertFalse(logMended.logWireCut(), "explicit mend clears the physical placeholder cut bit");
        var appearance = new AirlockWireLayoutRegistry(new Random(4)).describe("standard", ACTIONS, logPulsed);
        assertEquals(8, appearance.size());
        assertEquals(1, appearance.stream().filter(AirlockWireLayoutRegistry.WireAppearance::cut).count());
    }
}
