package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AirlockBumpSystemTest {
    @Test
    void contactMemoRearmsWhenFreshPaidPowerReadinessChanges() {
        UUID root = UUID.randomUUID();
        var unknown = new AirlockBumpSystem.ContactMemo(root, 7, false);
        var paid = new AirlockBumpSystem.ContactMemo(root, 7, true);
        assertNotEquals(unknown, paid);
        assertEquals(unknown, new AirlockBumpSystem.ContactMemo(root, 7, false));
        assertEquals(paid, new AirlockBumpSystem.ContactMemo(root, 7, true));
    }

    @Test
    void contactMemoTracksIndependentRootsAndBothDoorHalvesShareRootIdentity() {
        UUID leftRoot = UUID.randomUUID();
        UUID rightRoot = UUID.randomUUID();
        var left = new AirlockBumpSystem.ContactMemo(leftRoot, 4, true);
        var right = new AirlockBumpSystem.ContactMemo(rightRoot, 9, false);
        assertEquals(leftRoot, left.root());
        assertEquals(rightRoot, right.root());
        assertNotEquals(left, right);
        assertNotEquals(right, new AirlockBumpSystem.ContactMemo(rightRoot, 9, true));
        assertEquals(left, new AirlockBumpSystem.ContactMemo(leftRoot, 4, true));
    }
}
