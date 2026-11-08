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
    @Test void allocatorKeepsSurvivorsAndReusesHolesInGrantOrder() {
        var a = entry("a", "p", true); var b = entry("b", "p", false); var c = entry("c", "p", true);
        var slots = new BodyActionSlots(); var body = UUID.randomUUID();
        slots.update(new BodyActionSnapshot(body, 5, 1, 0, List.of(a, b)));
        var view = slots.resolve(); assertSame(view, slots.resolve());
        assertEquals(a, slots.at(1)); assertEquals(b, slots.at(2));
        slots.update(new BodyActionSnapshot(body, 5, 2, 0, List.of(b)));
        assertEquals(b, slots.at(2)); assertNull(slots.at(1));
        slots.update(new BodyActionSnapshot(body, 5, 3, 0, List.of(b, c)));
        assertEquals(c, slots.at(1)); assertEquals(b, slots.at(2));
        assertNotSame(view, slots.resolve());
    }
    @Test void allocatesTwentyUniformSlotsAndLeavesTwentyFirstPendingUntilHole() {
        var allocator = new BodyActionSlots(); var body = UUID.randomUUID();
        var entries = new java.util.ArrayList<BodyActionSnapshot.Entry>();
        for (int i = 0; i < 21; i++) entries.add(entry("arbitrary_" + i, "provider", true));
        var first = new BodyActionSnapshot(body, 1, 1, 0, entries); allocator.update(first);
        for (int i = 0; i < 20; i++) assertEquals(entries.get(i), allocator.at(i + 1));
        assertEquals(20, allocator.resolve().size()); assertEquals(21, first.actions().size());
        allocator.update(new BodyActionSnapshot(body, 1, 2, 0, entries.subList(1, 21)));
        assertEquals(entries.get(1), allocator.at(2)); assertEquals(entries.get(20), allocator.at(1));
        assertEquals(20, allocator.resolve().size());
    }
    @Test void naturalGrantOrderHasNoCombatScreamOrDebugReservations() {
        var allocator = new BodyActionSlots(); var body = UUID.randomUUID();
        var scream = entry("scream", "voice", true); var harm = entry("harm", "hand", true);
        var combat = entry("combat_mode_toggle", "mind", true); var debug = entry("debug_pulse", "debug", true);
        allocator.update(new BodyActionSnapshot(body, 1, 1, 0, List.of(scream)));
        assertEquals(scream, allocator.at(1));
        allocator.update(new BodyActionSnapshot(body, 1, 2, 0, List.of(harm, scream)));
        assertEquals(scream, allocator.at(1)); assertEquals(harm, allocator.at(2));
        allocator.clear(); allocator.update(new BodyActionSnapshot(body, 1, 3, 0, List.of(harm)));
        assertEquals(harm, allocator.at(1));
        allocator.clear(); allocator.update(new BodyActionSnapshot(body, 1, 4, 0, List.of(debug, combat)));
        assertEquals(debug, allocator.at(1)); assertEquals(combat, allocator.at(2));
    }
    @Test void identitiesIncludeProviderAndSnapshotsRefreshEntryWithoutMovingSlots() {
        var allocator = new BodyActionSlots(); var body = UUID.randomUUID();
        var a = entry("same", "provider-a", true); var b = entry("same", "provider-b", true);
        allocator.update(new BodyActionSnapshot(body, 1, 1, 0, List.of(a, b)));
        assertEquals(a, allocator.at(1)); assertEquals(b, allocator.at(2));
        var updated = new BodyActionSnapshot.Entry(a.actionId(), a.providerId(), "Updated", a.icon(), a.iconOn(), a.kind(), false, false, 5, 0, "COOLDOWN", 0);
        allocator.update(new BodyActionSnapshot(body, 1, 2, 0, List.of(b, updated)));
        assertEquals(updated, allocator.at(1)); assertEquals(b, allocator.at(2));
        allocator.update(new BodyActionSnapshot(body, 1, 3, 0, List.of(updated, b)));
        assertEquals(updated, allocator.at(1)); assertEquals(b, allocator.at(2));
    }
    @Test void incomingInternalsAndMaskActionsUseUniformArrivalOrderAndProviderIdentity() {
        var combat = entry("combat_mode_toggle", "mind", true);
        var scream = entry("scream", "voice", true);
        var internals = entry("toggle_internals", "equipment:back:tank", true);
        var mask = entry("toggle_mask", "equipment:mask:mask", true);
        var replacement = entry("toggle_mask", "equipment:mask:replacement", true);
        var body = UUID.randomUUID(); var slots = new BodyActionSlots();
        slots.update(new BodyActionSnapshot(body, 4, 1, 0, List.of(combat, scream, internals, mask)));
        assertEquals(combat, slots.at(1)); assertEquals(scream, slots.at(2));
        assertEquals(internals, slots.at(3)); assertEquals(mask, slots.at(4));
        slots.update(new BodyActionSnapshot(body, 4, 2, 0, List.of(combat, scream, internals)));
        assertEquals(internals, slots.at(3));
        slots.update(new BodyActionSnapshot(body, 4, 3, 0, List.of(combat, scream, internals, replacement)));
        assertEquals(replacement, slots.at(4)); assertEquals(internals, slots.at(3));
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
    @Test void configuredTwentySlotConflictsChooseEarliestMappingAtEveryIndex() {
        boolean[] conflicts = new boolean[20];
        for (int i = 0; i < 20; i++) {
            java.util.Arrays.fill(conflicts, false);
            for (int j = 0; j < i; j++) conflicts[j] = j == 1;
            assertEquals(i > 1 ? 1 : i, BodyActionKeyPolicy.bindingWinner(conflicts, i));
        }
        java.util.Arrays.fill(conflicts, false);
        conflicts[3] = true;
        assertEquals(3, BodyActionKeyPolicy.bindingWinner(conflicts, 19));
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
