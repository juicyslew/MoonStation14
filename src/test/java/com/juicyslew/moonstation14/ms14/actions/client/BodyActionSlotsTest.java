package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyActionSlotsTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static BodyActionSnapshot.Entry entry(String action, String provider, boolean enabled) {
        return new BodyActionSnapshot.Entry(id(action), provider, action, id("icon"), Optional.empty(),
                ActionPrototype.Kind.INSTANT, enabled, false, 0, 0, "", 0);
    }
    @Test void stableSlotsKeepMissingAndDisabledActionsAndIgnoreProviderRemoval() {
        var combat = entry("combat_mode_toggle", "combat", false);
        var debug = entry("debug_pulse", "debug", true);
        var snapshot = new BodyActionSnapshot(UUID.randomUUID(), 4, 2, 0, List.of(combat, debug));
        var slots = BodyActionSlots.resolve(snapshot);
        assertEquals(3, slots.size());
        assertEquals(combat, slots.get(0).entry());
        assertNull(slots.get(1).entry());
        assertEquals(debug, slots.get(2).entry());
        assertNull(BodyActionSlots.at(snapshot, 2));
        assertEquals(List.of(combat, debug), slots.stream().map(BodyActionSlots.Slot::entry).filter(java.util.Objects::nonNull).toList());
        var removed = new BodyActionSnapshot(snapshot.bodyId(), 4, 3, 0, List.of(debug));
        assertNull(BodyActionSlots.at(removed, 1));
        assertEquals(debug, BodyActionSlots.at(removed, 3));
    }
    @Test void policyRejectsMissingWorldBodyScreenAndStaleBodyEpoch() {
        UUID body = UUID.randomUUID();
        assertTrue(BodyActionKeyPolicy.eligible(true, true, true, true, 1));
        assertFalse(BodyActionKeyPolicy.eligible(false, true, true, true, 1));
        assertFalse(BodyActionKeyPolicy.eligible(true, false, true, true, 1));
        assertFalse(BodyActionKeyPolicy.eligible(true, true, false, false, 1));
        assertFalse(BodyActionKeyPolicy.eligible(true, true, true, true, 0));
        assertFalse(BodyActionKeyPolicy.snapshotMatches(body, 2, body, 1));
        assertTrue(BodyActionKeyPolicy.snapshotMatches(body, 2, body, 2));
    }
    @Test void duplicateBindingsChooseLowestIndex() {
        assertEquals(0, BodyActionKeyPolicy.bindingWinner(new String[]{"same", "same", "other"}, 1));
        assertEquals(2, BodyActionKeyPolicy.bindingWinner(new String[]{"same", "same", "other"}, 2));
    }
    @Test void debugChordBlocksOnlyBodyActionAdmission() {
        var modClicks = new java.util.ArrayDeque<>(java.util.List.of(1, 2, 3));
        var nativeHotbarClicks = new java.util.ArrayDeque<>(java.util.List.of(1));
        int[] activations = {0};
        assertTrue(BodyActionKeyPolicy.consumeDebugChord(true, modClicks::clear));
        assertTrue(modClicks.isEmpty());
        assertEquals(1, nativeHotbarClicks.size());
        // The F3+number event has discarded its clicks; releasing F3 before the tick cannot replay them.
        assertFalse(BodyActionKeyPolicy.consumeDebugChord(false, modClicks::clear));
        assertTrue(modClicks.isEmpty());
        assertEquals(0, activations[0]);
        assertFalse(BodyActionKeyPolicy.consumeDebugChord(false, modClicks::clear));
        assertEquals(1, BodyActionKeyPolicy.bindingWinner(new String[]{"1", "2", "3"}, 1));
    }
}
