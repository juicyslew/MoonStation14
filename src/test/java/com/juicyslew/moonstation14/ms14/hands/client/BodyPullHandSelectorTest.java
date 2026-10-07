package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BodyPullHandSelectorTest {
    private static BodyHandStateSnapshot.Hand empty(String id) {
        return new BodyHandStateSnapshot.Hand(id, null, null, 0);
    }

    private static BodyHandStateSnapshot.Hand occupied(String id) {
        return new BodyHandStateSnapshot.Hand(id, "token-" + id, "minecraft:stone", 1);
    }

    @Test
    void prefersActiveWhenItIsEmptyAndUnreserved() {
        var active = empty("right");
        assertSame(active, BodyPullHandSelector.select(List.of(empty("left"), active), "right"));
    }

    @Test
    void fallsBackToOtherEmptyHandWhenActiveHandIsOccupied() {
        var other = empty("left");
        assertSame(other, BodyPullHandSelector.select(List.of(other, occupied("right")), "right"));
    }

    @Test
    void deniesWhenBothHandsAreOccupied() {
        assertNull(BodyPullHandSelector.select(List.of(occupied("left"), occupied("right")), "right"));
    }

    @Test
    void skipsReservedEmptyHandsWhenSelectingFallback() {
        var available = empty("right");
        var reserved = new BodyHandStateSnapshot.Hand("left", null, null, 0,
                false, null, null, 0, "00000000-0000-0000-0000-000000000001");
        assertSame(available, BodyPullHandSelector.select(List.of(reserved, available), "missing"));
        assertNull(BodyPullHandSelector.select(List.of(reserved), "left"));
    }
}
