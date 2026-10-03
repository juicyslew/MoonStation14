package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageDragPreviewPolicyTest {
    private static BodyHandStateSnapshot.Hand hand(String id, String token, int count) {
        return new BodyHandStateSnapshot.Hand(id, token, "minecraft:stone", count);
    }

    private static BodyHandClientPolicy.InventoryView view(BodyHandStateSnapshot.Hand first,
                                                            BodyHandStateSnapshot.Hand second, String active) {
        return new BodyHandClientPolicy.InventoryView(first, second, active, null, null, null, null);
    }

    private static BodyHandStateSnapshot.GridEntry entry(String token, String item, int count, int x) {
        return new BodyHandStateSnapshot.GridEntry(token, item, count, x, 0, 0);
    }

    @Test void retainedHandPreviewDoesNotImplyActionProof() {
        var presented = hand("left", "hand-token", 3);
        var source = StorageDragPreviewPolicy.resolve("hand-token", null, null,
                view(presented, null, "left"), null);
        assertNotNull(source);
        assertEquals(presented.itemId(), source.itemId());
        assertEquals(presented.count(), source.count());
        assertNotNull(source.footprint());
        assertFalse(source.actionable());

        var fresh = StorageDragPreviewPolicy.resolve("hand-token", null, null,
                view(presented, null, "left"), view(presented, null, "left"));
        assertTrue(fresh.actionable());
    }

    @Test void tokenCanNominateInactiveHandAndRequiresExactProof() {
        var inactive = hand("right", "right-token", 3);
        var shown = view(hand("left", "left-token", 1), inactive, "left");
        assertTrue(StorageDragPreviewPolicy.resolve("right-token", null, null, shown, shown).actionable());
        assertFalse(StorageDragPreviewPolicy.resolve("right-token", null, null, shown,
                view(hand("left", "left-token", 1), hand("right", "right-token", 2), "left")).actionable());
        assertFalse(StorageDragPreviewPolicy.resolve("right-token", null, null, shown,
                view(hand("left", "left-token", 1), hand("right", "other-token", 3), "left")).actionable());
        assertNull(StorageDragPreviewPolicy.resolve("gone", null, null, shown, shown));
    }

    @Test void retainedGridChildRequiresSameFreshEntry() {
        var shown = entry("child-token", "minecraft:stone", 3, 0);
        var source = StorageDragPreviewPolicy.resolve("child-token", List.of(shown), null, null, null);
        assertNotNull(source);
        assertFalse(source.actionable());
        assertTrue(StorageDragPreviewPolicy.resolve("child-token", List.of(shown), List.of(shown), null, null).actionable());
        assertFalse(StorageDragPreviewPolicy.resolve("child-token", List.of(shown),
                List.of(entry("child-token", "minecraft:stone", 2, 0)), null, null).actionable());
        assertFalse(StorageDragPreviewPolicy.resolve("child-token", List.of(shown),
                List.of(entry("child-token", "minecraft:stone", 3, 1)), null, null).actionable());
        assertFalse(StorageDragPreviewPolicy.resolve("child-token", List.of(shown),
                List.of(entry("other-token", "minecraft:stone", 3, 0)), null, null).actionable());
    }
}
