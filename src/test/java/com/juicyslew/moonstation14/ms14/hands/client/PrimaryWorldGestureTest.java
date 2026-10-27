package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class PrimaryWorldGestureTest {
    @Test void routesOnlyOwnedWorldPrimaryGestures() {
        assertEquals(PrimaryWorldGesture.Route.MELEE, PrimaryWorldGesture.route(true, true, true, true, true, true));
        assertEquals(PrimaryWorldGesture.Route.VANILLA, PrimaryWorldGesture.route(false, true, true, true, true, true));
        assertEquals(PrimaryWorldGesture.Route.VANILLA, PrimaryWorldGesture.route(false, true, true, true));
        assertEquals(PrimaryWorldGesture.Route.TARGET_USE, PrimaryWorldGesture.route(true, true, true, true));
        assertEquals(PrimaryWorldGesture.Route.MEDICAL_USE, PrimaryWorldGesture.route(true, true, true, true, true));
        assertEquals(BodyHandActionRequest.Action.USE_BODY,
                PrimaryWorldGesture.action(PrimaryWorldGesture.route(true, false, true, false, true)));
        assertEquals(BodyHandActionRequest.Action.USE_TARGET,
                PrimaryWorldGesture.action(PrimaryWorldGesture.route(true, false, true, true)));
        assertEquals(PrimaryWorldGesture.Route.HELD_USE, PrimaryWorldGesture.route(true, false, true, false));
        assertEquals(BodyHandActionRequest.Action.USE_HELD,
                PrimaryWorldGesture.action(PrimaryWorldGesture.route(true, false, true, false)));
        assertEquals(PrimaryWorldGesture.Route.PICKUP, PrimaryWorldGesture.route(true, true, false, true));
        assertEquals(BodyHandActionRequest.Action.PICKUP,
                PrimaryWorldGesture.action(PrimaryWorldGesture.route(true, false, false, true)));
        assertEquals(PrimaryWorldGesture.Route.BLOCK_USE, PrimaryWorldGesture.route(true, true, false, false));
        assertEquals(PrimaryWorldGesture.Route.BLOCK_USE, PrimaryWorldGesture.route(true, true, true, false));
        assertEquals(PrimaryWorldGesture.Route.CONTROLLED_NONE, PrimaryWorldGesture.route(true, false, false, false));
        assertNull(PrimaryWorldGesture.action(PrimaryWorldGesture.Route.VANILLA));
    }

    @Test void emptyHandPortableNominationProducesSelectedPickupEvenWithBackgroundBlock() {
        UUID portable = UUID.randomUUID();
        var click = PrimaryWorldGesture.click(true, false, null, portable, false, null);
        assertEquals(PrimaryWorldGesture.Route.PICKUP, click.route());
        assertEquals(BodyHandActionRequest.Action.PICKUP_SELECTED, click.action());
        assertEquals(portable, click.target());
    }

    @Test void pileWinsAndPortableLivingDoesNotBecomeHeldHandTarget() {
        UUID pile = UUID.randomUUID(), portable = UUID.randomUUID();
        var emptyHand = PrimaryWorldGesture.click(false, false, pile, portable, false, null);
        assertEquals(BodyHandActionRequest.Action.PICKUP_SELECTED, emptyHand.action());
        assertEquals(pile, emptyHand.target());

        var heldHand = PrimaryWorldGesture.click(false, true, null, portable, false, null);
        assertEquals(PrimaryWorldGesture.Route.HELD_USE, heldHand.route());
        assertEquals(BodyHandActionRequest.Action.USE_HELD, heldHand.action());
        assertNull(heldHand.target());

        var heldHandWithPile = PrimaryWorldGesture.click(false, true, pile, portable, false, null);
        assertEquals(BodyHandActionRequest.Action.USE_TARGET, heldHandWithPile.action());
        assertEquals(pile, heldHandWithPile.target());
    }

    @Test void noCandidateAndMedicalHeldUseRetainExistingRoutes() {
        var blocked = PrimaryWorldGesture.click(true, false, null, null, false, null);
        assertEquals(PrimaryWorldGesture.Route.BLOCK_USE, blocked.route());
        assertNull(blocked.action());

        UUID portable = UUID.randomUUID(), patient = UUID.randomUUID();
        var medical = PrimaryWorldGesture.click(false, true, null, portable, true, patient);
        assertEquals(PrimaryWorldGesture.Route.MEDICAL_USE, medical.route());
        assertEquals(BodyHandActionRequest.Action.USE_BODY, medical.action());
        assertEquals(patient, medical.target());
    }

    @Test void outlineSharesPilePriorityAndOnlyAddsPortableCandidateWhenPickupIsAvailable() {
        UUID pile = UUID.randomUUID(), portable = UUID.randomUUID();
        assertEquals(pile, PrimaryWorldGesture.outlineTarget(false, true, pile, portable));
        assertEquals(portable, PrimaryWorldGesture.outlineTarget(true, false, null, portable));
        assertNull(PrimaryWorldGesture.outlineTarget(false, false, null, portable));
        assertNull(PrimaryWorldGesture.outlineTarget(true, true, null, portable));
    }

    @Test void resolvedClickRecipientIsAlsoThePresentationCandidateAcrossPriorities() {
        UUID medical = UUID.randomUUID(), pile = UUID.randomUUID(), portable = UUID.randomUUID();
        var treatment = PrimaryWorldGesture.resolve(true, true, pile, portable, true, medical);
        assertEquals(PrimaryWorldGesture.Route.MEDICAL_USE, treatment.route());
        assertEquals(medical, treatment.target());

        var ordinaryHeld = PrimaryWorldGesture.resolve(true, true, pile, portable, false, null);
        assertEquals(PrimaryWorldGesture.Route.TARGET_USE, ordinaryHeld.route());
        assertEquals(pile, ordinaryHeld.target());
        var noMobTarget = PrimaryWorldGesture.resolve(true, true, null, portable, false, null);
        assertEquals(PrimaryWorldGesture.Route.BLOCK_USE, noMobTarget.route());
        assertNull(noMobTarget.target());
        var emptyHand = PrimaryWorldGesture.resolve(true, false, null, portable, false, null);
        assertEquals(BodyHandActionRequest.Action.PICKUP_SELECTED, emptyHand.action());
        assertEquals(portable, emptyHand.target());
        var medicalMiss = PrimaryWorldGesture.resolve(true, true, pile, portable, false, null);
        assertEquals(PrimaryWorldGesture.Route.TARGET_USE, medicalMiss.route());
        assertEquals(pile, medicalMiss.target());
    }
}
