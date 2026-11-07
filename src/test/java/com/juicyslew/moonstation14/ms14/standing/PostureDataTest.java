package com.juicyslew.moonstation14.ms14.standing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostureDataTest {
    @Test void physicalVariantTracksOnlyDimensionRelevantPostureTransitions() {
        var prone = PostureData.STANDING.forceDown(10);
        var aimed = prone.withSupportedGunAim(true);
        assertNotEquals(PhysicalPosture.variant(prone), PhysicalPosture.variant(aimed));
        assertNotEquals(PhysicalPosture.variant(aimed), PhysicalPosture.variant(prone));
        assertNotEquals(PhysicalPosture.variant(prone), PhysicalPosture.variant(PostureData.STANDING));
        assertEquals(PhysicalPosture.variant(prone), PhysicalPosture.variant(prone.movement(2, 1, true)));
        assertEquals(PhysicalPosture.variant(PostureData.STANDING), PhysicalPosture.variant(null));
    }
    @Test void occupiedAndReservedHandsScaleCrawlAndGetUp() {
        assertEquals(.4, PostureData.crawlSpeed(true, .4, 2, 2));
        assertEquals(.2, PostureData.crawlSpeed(true, .4, 1, 2));
        assertEquals(0, PostureData.crawlSpeed(true, .4, 0, 2));
        assertEquals(1, PostureData.crawlSpeed(false, .4, 0, 2));
        assertEquals(2, PostureData.standSeconds(2, 0, 2));
        assertEquals(4d/3, PostureData.standSeconds(2, 1, 2), 1e-9);
        assertEquals(1, PostureData.standSeconds(2, 2, 2));
    }
    @Test void zeroResolvedHandsDisableCrawlingWhileStandingRemainsFullSpeed() {
        assertEquals(0, PostureData.crawlSpeed(true, .4, 0, 0));
        assertEquals(1, PostureData.crawlSpeed(false, .4, 0, 0));
    }
    @Test void voluntaryAndForcedDownHaveDistinctAutomaticStandPolicy() {
        PostureData voluntary = PostureData.STANDING.voluntaryToggle(10, 10);
        assertTrue(voluntary.down()); assertFalse(voluntary.voluntaryAutoStand());
        assertFalse(voluntary.mayStand(19, true, true));
        assertTrue(voluntary.mayStand(20, true, true));
        PostureData forced = PostureData.STANDING.forceDown(20);
        assertTrue(forced.voluntaryAutoStand());
        assertFalse(forced.mayStand(20, false, true));
        assertFalse(forced.mayStand(20, true, false));
        assertTrue(forced.completeGetUp(false).down());
    }
    @Test void damageRefreshPreservesPosturePolicyAndCoarseMovementState() {
        PostureData voluntary = PostureData.STANDING.voluntaryToggle(10, 10)
                .movement(2, 1, true).beginGetUp(7);
        PostureData refreshed = voluntary.refreshKnockdown(30);
        assertTrue(refreshed.down());
        assertFalse(refreshed.voluntaryAutoStand());
        assertEquals(30, refreshed.knockdownUntil());
        assertEquals(7, refreshed.getUpKey());
        assertEquals(2, refreshed.totalHands());
        assertEquals(1, refreshed.freeHands());
        assertTrue(refreshed.pulling());
        assertEquals(voluntary, voluntary.refreshKnockdown(5));
    }
    @Test void transitionsPreserveDownedOrientationWhileNewKnockdownDefaultsFaceDown() {
        PostureData voluntary = PostureData.STANDING.voluntaryToggle(10, 0);
        assertFalse(voluntary.faceUp());
        assertTrue(voluntary.voluntaryRoll().faceUp());
        assertFalse(voluntary.voluntaryRoll().voluntaryRoll().faceUp());
        assertFalse(PostureData.STANDING.forceDown(20).faceUp());
        PostureData rolled = voluntary.voluntaryRoll();
        assertTrue(rolled.forceDown(20, PostureData.DownTransition.MEDICAL).faceUp());
        assertFalse(voluntary.forceDown(20, PostureData.DownTransition.MEDICAL).faceUp());
        assertTrue(rolled.forceDown(20, PostureData.DownTransition.KNOCKDOWN).faceUp());
        assertFalse(voluntary.completeGetUp(true).faceUp());
    }
    @Test void otherBodyTurnOnlyChangesFacingAndNeverChangesMedicalOrDownState() {
        PostureData criticalOrDeadPosture = PostureData.STANDING.forceDown(40).movement(2, 2, false);
        PostureData turned = criticalOrDeadPosture.voluntaryRoll();
        assertTrue(turned.down());
        assertFalse(criticalOrDeadPosture.faceUp());
        assertTrue(turned.faceUp());
        assertTrue(turned.voluntaryAutoStand());
        assertEquals(criticalOrDeadPosture.knockdownUntil(), turned.knockdownUntil());
        assertEquals(criticalOrDeadPosture.totalHands(), turned.totalHands());
        assertEquals(criticalOrDeadPosture.freeHands(), turned.freeHands());
        assertEquals(criticalOrDeadPosture.pulling(), turned.pulling());
        assertFalse(PostureSystem.turnOtherBody(null, null));
    }
    @Test void movementFacingSurvivesPostureTransitionsAndOnlyChangesForTravel() {
        PostureData down = PostureData.STANDING.voluntaryToggle(1, 0).facing(37f);
        assertEquals(37f, down.movement(2, 1, false).movementFacingYaw());
        assertEquals(37f, down.voluntaryRoll().movementFacingYaw());
        assertEquals(37f, down.refreshKnockdown(10).movementFacingYaw());
        assertEquals(37f, down.completeGetUp(true).movementFacingYaw());
        assertThrows(IllegalArgumentException.class, () -> down.facing(Float.NaN));
        assertEquals(0f, PostureData.yawForWorldMovement(0, 1), 1e-5);
        assertEquals(-90f, PostureData.yawForWorldMovement(1, 0), 1e-5);
        assertEquals(90f, PostureData.yawForWorldMovement(-1, 0), 1e-5);
        assertEquals(180f, Math.abs(PostureData.yawForWorldMovement(0, -1)), 1e-5);
        assertThrows(IllegalArgumentException.class, () -> PostureData.yawForWorldMovement(0, 0));
    }
    @Test void facingNormalizesFiniteYawAndPreservesOtherPostureFields() {
        PostureData original = PostureData.STANDING.voluntaryToggle(10, 5)
                .movement(3, 2, true).crawling(true).forceFaceUp(20);
        PostureData positive = original.facing(450f);
        PostureData negative = original.facing(-450f);
        assertEquals(90f, positive.movementFacingYaw());
        assertEquals(-90f, negative.movementFacingYaw());
        assertEquals(-180f, original.facing(180f).movementFacingYaw());
        assertEquals(-180f, original.facing(-180f).movementFacingYaw());
        assertEquals(90f, original.facing(3600f + 90f).movementFacingYaw());
        assertEquals(-90f, original.facing(-3600f - 90f).movementFacingYaw());
        assertEquals(original.down(), positive.down());
        assertEquals(original.voluntaryAutoStand(), positive.voluntaryAutoStand());
        assertEquals(original.knockdownUntil(), positive.knockdownUntil());
        assertEquals(original.getUpKey(), positive.getUpKey());
        assertEquals(original.totalHands(), positive.totalHands());
        assertEquals(original.freeHands(), positive.freeHands());
        assertEquals(original.pulling(), positive.pulling());
        assertEquals(original.faceUp(), positive.faceUp());
        assertEquals(original.ownCrawling(), positive.ownCrawling());
        assertThrows(IllegalArgumentException.class, () -> original.facing(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> original.facing(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> original.facing(Float.NEGATIVE_INFINITY));
    }
    @Test void supportedGunAimSurvivesDownedCopiesAndClearsOnStanding() {
        PostureData aimed = PostureData.STANDING.forceDown(20).withSupportedGunAim(true);
        assertTrue(aimed.movement(2, 1, true).supportedGunAim());
        assertTrue(aimed.crawling(true).supportedGunAim());
        assertTrue(aimed.forceDown(30).supportedGunAim());
        assertTrue(aimed.beginGetUp(2).supportedGunAim());
        assertFalse(aimed.completeGetUp(true).supportedGunAim());
        assertFalse(PostureData.STANDING.withSupportedGunAim(true).supportedGunAim());
        PostureData decoded = PostureData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                PostureData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, aimed).getOrThrow()).getOrThrow();
        assertTrue(decoded.supportedGunAim());
    }
    @Test void crawlPreparationPreservesAimAndAllOtherFieldsWhileTurningFaceDown() {
        PostureData source = PostureData.STANDING.forceDown(24).movement(3, 2, true)
                .facing(63).forceFaceUp(30).withSupportedGunAim(true);
        PostureData prepared = PostureSystem.prepareCrawlPosture(source, true);
        assertTrue(prepared.down());
        assertFalse(prepared.faceUp());
        assertTrue(prepared.ownCrawling());
        assertTrue(prepared.supportedGunAim());
        assertEquals(source.down(), prepared.down());
        assertEquals(source.voluntaryAutoStand(), prepared.voluntaryAutoStand());
        assertEquals(source.knockdownUntil(), prepared.knockdownUntil());
        assertEquals(source.getUpKey(), prepared.getUpKey());
        assertEquals(source.totalHands(), prepared.totalHands());
        assertEquals(source.freeHands(), prepared.freeHands());
        assertEquals(source.pulling(), prepared.pulling());
        assertEquals(source.movementFacingYaw(), prepared.movementFacingYaw());
        assertFalse(PostureSystem.prepareCrawlPosture(source.withSupportedGunAim(false), true).supportedGunAim());
    }
}
