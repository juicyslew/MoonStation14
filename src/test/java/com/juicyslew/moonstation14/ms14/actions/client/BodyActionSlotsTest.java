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
    @Test void fixedActionsLeadWhenPresentAndRemainingActionsPackContiguously() {
        var combat = entry("combat_mode_toggle", "combat", false);
        var debug = entry("debug_pulse", "debug", true);
        var snapshot = new BodyActionSnapshot(UUID.randomUUID(), 4, 2, 0, List.of(combat, debug));
        var slots = BodyActionSlots.resolve(snapshot);
        assertEquals(2, slots.size());
        assertEquals(combat, slots.get(0).entry());
        assertEquals(debug, slots.get(1).entry());
        assertEquals(List.of(combat, debug), slots.stream().map(BodyActionSlots.Slot::entry).filter(java.util.Objects::nonNull).toList());
        var removed = new BodyActionSnapshot(snapshot.bodyId(), 4, 3, 0, List.of(debug));
        assertEquals(debug, BodyActionSlots.at(removed, 1));
        assertNull(BodyActionSlots.at(snapshot, 9));
    }
    @Test void multipleProvidersForOneActionRemainSeparatelySelectableInDynamicRows() {
        var tankA = entry("toggle_internals", "equipment:back:token-a", true);
        var tankB = entry("toggle_internals", "equipment:suitstorage:token-b", true);
        var snapshot = new BodyActionSnapshot(UUID.randomUUID(), 4, 2, 0, List.of(tankA, tankB));
        var rows = BodyActionSlots.resolve(snapshot);
        assertEquals(List.of(tankA, tankB), rows.stream().map(BodyActionSlots.Slot::entry)
                .filter(java.util.Objects::nonNull).toList());
        assertNotEquals(rows.get(0).entry().providerId(), rows.get(1).entry().providerId());
    }
    @Test void numberedLookupCoversVanillaHotbarRangeAndNoDebugReservation() {
        var actions = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> entry(i == 0 ? "debug_pulse" : "action_" + i, "provider" + i, true)).toList();
        var snapshot = new BodyActionSnapshot(UUID.randomUUID(), 4, 2, 0, actions);
        for (int i = 1; i <= 9; i++) assertEquals(actions.get(i - 1), BodyActionSlots.at(snapshot, i));
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
    @Test void configuredNineSlotConflictsChooseEarliestMappingAtEveryIndex() {
        boolean[] conflicts = new boolean[9];
        for (int i = 0; i < 9; i++) {
            java.util.Arrays.fill(conflicts, false);
            for (int j = 0; j < i; j++) conflicts[j] = j == 1;
            assertEquals(i > 1 ? 1 : i, BodyActionKeyPolicy.bindingWinner(conflicts, i));
        }
        java.util.Arrays.fill(conflicts, false);
        conflicts[3] = true;
        assertEquals(3, BodyActionKeyPolicy.bindingWinner(conflicts, 8));
    }
    @Test void routingEligibilityRequiresCommittedCharacterAndRejectsUiOrStaleSnapshot() {
        UUID body = UUID.randomUUID();
        assertTrue(BodyActionKeyPolicy.eligible(true, true, true, true, 7));
        assertFalse(BodyActionKeyPolicy.eligible(true, true, false, false, 0)); // ghost/spectator: leave native bindings alone
        assertFalse(BodyActionKeyPolicy.eligible(true, false, true, true, 7)); // UI capture
        assertFalse(BodyActionKeyPolicy.snapshotMatches(body, 7, body, 6)); // stale committed epoch
        assertTrue(BodyActionKeyPolicy.snapshotMatches(body, 7, body, 7));
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
