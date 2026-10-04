package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyHandInputPolicyTest {
    @Test void onlyGrabbedWorldInputForCommittedBodyIsOwned() {
        assertTrue(BodyHandInputPolicy.ownsPrimary(true, true, true, true, 4));
        assertFalse(BodyHandInputPolicy.ownsPrimary(true, false, true, true, 4)); // popup/GUI
        assertFalse(BodyHandInputPolicy.ownsPrimary(true, true, false, true, 4)); // released cursor
        assertFalse(BodyHandInputPolicy.ownsPrimary(true, true, true, false, 4)); // absent body / spectator
        assertFalse(BodyHandInputPolicy.ownsPrimary(true, true, true, true, 0)); // uncommitted camera
        assertFalse(BodyHandInputPolicy.ownsPrimary(false, true, true, true, 4));
    }

    @Test void ownedPrimaryGestureCancelsVanillaEvenWhenActionGateCannotSend() {
        var targetRoute = PrimaryWorldGesture.route(true, false, true, true);
        assertEquals(BodyHandActionRequest.Action.USE_TARGET, PrimaryWorldGesture.action(targetRoute));
        var heldRoute = PrimaryWorldGesture.route(true, false, true, false);
        assertEquals(BodyHandActionRequest.Action.USE_HELD, PrimaryWorldGesture.action(heldRoute));
        // Cancellation depends on input ownership/route, not whether the live request gate has proof.
        assertTrue(BodyHandInputPolicy.cancelsVanillaAttack(targetRoute));
        assertFalse(BodyHandInputPolicy.cancelsVanillaAttack(
                PrimaryWorldGesture.route(false, false, true, true)));
    }

    @Test void dropModifierChoosesExclusiveDropOrThrow() {
        assertEquals(BodyHandActionRequest.Action.DROP, BodyHandInputPolicy.dropAction(true, false));
        assertEquals(BodyHandActionRequest.Action.THROW_ITEM, BodyHandInputPolicy.dropAction(true, true));
        assertNull(BodyHandInputPolicy.dropAction(false, true));
    }
}
