package com.juicyslew.moonstation14.ms14.examine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExamineRefreshPolicyTest {
    @Test void explicitInspectionReplacesPendingTargetAndOnlyItsReplyReleasesSlot() {
        var policy = new ExamineRefreshPolicy();
        assertTrue(policy.request()); // automatic request A
        policy.sent();
        policy.explicitRequest(); // explicit click B invalidates A's pending slot
        assertTrue(policy.request());
        policy.sent();
        assertFalse(policy.correlatedResponse(false)); // delayed A is stale for B
        assertTrue(policy.inFlight());
        assertFalse(policy.request()); // periodic/mismatch refresh coalesces behind B
        assertTrue(policy.correlatedResponse(true)); // B reply releases one queued update
    }

    @Test void delayedReplyIsAcceptedAndReleasesOneCoalescedFollowUp() {
        var policy = new ExamineRefreshPolicy();
        assertTrue(policy.request());
        policy.sent();
        for (int tick = 1; tick <= 8; tick++) assertFalse(policy.request()); // > 200ms at 20 TPS
        assertTrue(policy.inFlight());
        assertTrue(policy.correlatedResponse(true));
        assertFalse(policy.inFlight());
        assertTrue(policy.request()); // caller sends the single queued follow-up
        policy.sent();
        assertFalse(policy.correlatedResponse(false)); // stale response cannot release it
        assertTrue(policy.inFlight());
    }

    @Test void resetDropsPendingTransitionAndDoesNotTouchPopupLifetime() {
        var policy = new ExamineRefreshPolicy();
        policy.request(); policy.sent(); policy.request();
        policy.reset();
        assertFalse(policy.inFlight());
        assertTrue(policy.request());
        var popup = new ExamineClientPolicy.Popup(() -> 0);
        popup.open(com.juicyslew.moonstation14.ms14.examine.ExamineTarget.block(new net.minecraft.core.BlockPos(1, 2, 3)), 1, 0);
        assertTrue(popup.valid(com.juicyslew.moonstation14.ms14.examine.ExamineTarget.block(new net.minecraft.core.BlockPos(1, 2, 3)), 1, true, 9_999));
        assertFalse(popup.valid(com.juicyslew.moonstation14.ms14.examine.ExamineTarget.block(new net.minecraft.core.BlockPos(1, 2, 3)), 1, true, 10_000));
    }
}
