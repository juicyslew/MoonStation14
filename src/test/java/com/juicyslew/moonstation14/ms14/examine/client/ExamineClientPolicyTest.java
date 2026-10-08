package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExaminationPolicy;
import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;
import com.juicyslew.moonstation14.ms14.examine.ExamineEntry;
import com.juicyslew.moonstation14.ms14.examine.ExamineResponse;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExamineClientPolicyTest {
    private final ExamineTarget target = ExamineTarget.block(new BlockPos(1, 2, 3));

    @Test void transientOutsideClickDismissesAndPinnedOutsideClickDoesNot() {
        var popup = new ExamineClientPolicy.Popup();
        popup.open(target, 4);
        assertTrue(popup.outsideClick());
        assertNull(popup.target());
        popup.open(target, 4);
        popup.pin(true);
        assertFalse(popup.outsideClick());
        assertEquals(target, popup.target());
        assertTrue(popup.escape());
    }

    @Test void escapeOnlyConsumesAnActiveExamineAndPinCannotExtendValidity() {
        var popup = new ExamineClientPolicy.Popup();
        assertFalse(popup.escape()); // other UI owners retain Escape
        popup.open(target, 4); popup.pin(true);
        assertFalse(popup.valid(target, 4, false));
        assertNull(popup.target());
        popup.open(target, 4);
        assertFalse(popup.valid(target, 5, true));
        assertFalse(popup.escape());
    }

    @Test void sharedEligibilityHonorsHealthyCriticalAndDeadBoundaries() {
        var normal = ExaminationPolicy.Examiner.DEFAULT;
        assertTrue(ExamineClientPolicy.eligible(normal, ExaminationPolicy.State.ALIVE, false, 0, 16));
        assertFalse(ExamineClientPolicy.eligible(normal, ExaminationPolicy.State.ALIVE, false, 0, 16.001));
        assertTrue(ExamineClientPolicy.eligible(normal, ExaminationPolicy.State.CRITICAL, false, 0, 1.3));
        assertFalse(ExamineClientPolicy.eligible(normal, ExaminationPolicy.State.CRITICAL, false, 0, 1.301));
        assertTrue(ExamineClientPolicy.eligible(normal, ExaminationPolicy.State.DEAD, false, 0, .75));
        assertFalse(ExamineClientPolicy.eligible(normal, ExaminationPolicy.State.DEAD, false, 0, .751));
    }

    @Test void onlyMatchingActualObserverGhostReceivesObserverPolicy() {
        assertTrue(ExamineClientPolicy.actualObserverGhost(true, true, true));
        assertFalse(ExamineClientPolicy.actualObserverGhost(false, true, true)); // carrier/spectator alone
        assertFalse(ExamineClientPolicy.actualObserverGhost(true, false, true));
        assertFalse(ExamineClientPolicy.actualObserverGhost(true, true, false));
    }

    @Test void localDetailGateMasksImmediatelyAndRefreshesOnlyOnTransitionOrCadence() {
        var examiner = ExaminationPolicy.Examiner.DEFAULT;
        assertTrue(ExamineDetailPolicy.locallyAllowed(examiner, ExaminationPolicy.State.ALIVE, false, 3));
        assertFalse(ExamineDetailPolicy.locallyAllowed(examiner, ExaminationPolicy.State.ALIVE, false, 16.01));
        assertFalse(ExamineClientPolicy.disagreementRefreshDue(false, true, false, 13, 14));
        assertTrue(ExamineClientPolicy.disagreementRefreshDue(false, true, false, 14, 14));
        assertFalse(ExamineClientPolicy.disagreementRefreshDue(false, true, true, 18, 18));
        assertTrue(ExamineClientPolicy.disagreementRefreshDue(false, true, false, 18, 18));
        assertFalse(ExamineClientPolicy.periodicRefreshDue(true, true, false, 29, 10));
        assertTrue(ExamineClientPolicy.periodicRefreshDue(true, true, false, 30, 10));
        assertFalse(ExamineClientPolicy.disagreementRefreshDue(false, false, false, 4, 0)); // scenes
    }

    @Test void clicksKeepPauseAndOtherScreensAheadOfExamineButStorageHostRoutesNormally() {
        assertFalse(ExamineClientPolicy.worldClickAllowed(false, false, false));
        assertFalse(ExamineClientPolicy.popupClickAllowed(false, false));
        assertFalse(ExamineClientPolicy.worldClickAllowed(false, false, false)); // inventory/chat/non-host
        assertFalse(ExamineClientPolicy.worldClickAllowed(false, true, true));
        assertTrue(ExamineClientPolicy.worldClickAllowed(false, true, false));
        assertTrue(ExamineClientPolicy.popupClickAllowed(false, true));
        assertTrue(ExamineClientPolicy.popupClickAllowed(true, false)); // resumed world click
        assertFalse(ExamineClientPolicy.popupClickAllowed(false, false)); // wheel follows same pause/menu admission
    }

    @Test void compactPanelScrollExposesEveryWrappedRowWithinReservedHeight() {
        int top = com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.examineTop(320, 240);
        int available = com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.examineAvailableHeight(320, 240);
        assertEquals(164, top);
        assertEquals(42, available);
        int rows = ExamineClientPolicy.popupVisibleRows(available, 9, 13);
        int panelHeight = ExamineClientPolicy.popupHeight(available, 9, rows);
        assertEquals(2, rows);
        assertTrue(top + panelHeight <= 240 - com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.BOTTOM_RESERVE);
        for (int i = 0; i < 7; i++)
            assertTrue(com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.statusRow(320, 240, i).bottom() < top);
        java.util.Set<Integer> reached = new java.util.HashSet<>();
        int offset = 0;
        while (true) {
            for (int i = offset; i < Math.min(13, offset + rows); i++) reached.add(i);
            int next = ExamineClientPolicy.scrollOffset(offset, 1, 13, rows);
            if (next == offset) break;
            offset = next;
        }
        assertEquals(13, reached.size());
        assertEquals(10, ExamineClientPolicy.clampScrollOffset(100, 13, 3));
        assertEquals(0, ExamineClientPolicy.clampScrollOffset(10, 2, 3)); // contents/revoke shrink

        int viewportThreeRows = ExamineClientPolicy.popupVisibleRows(47, 9, 13);
        assertEquals(3, viewportThreeRows);
        reached.clear(); offset = 0;
        while (true) {
            for (int i = offset; i < Math.min(13, offset + viewportThreeRows); i++) reached.add(i);
            int next = ExamineClientPolicy.scrollOffset(offset, 1, 13, viewportThreeRows);
            if (next == offset) break;
            offset = next;
        }
        assertEquals(13, reached.size());
    }

    @Test void explicitReplacementAndProductionCorrelationKeepRefreshSlotOwnedByLatestLatch() {
        var refresh = new ExamineRefreshPolicy();
        var latch = new ExamineLatchSession();
        var actor = java.util.UUID.randomUUID();
        var a = ExamineTarget.block(new BlockPos(1, 2, 3));
        var b = ExamineTarget.block(new BlockPos(4, 5, 6));
        assertTrue(refresh.request());
        refresh.sent(); latch.begin(a, actor, 7, 1);

        refresh.explicitRequest();
        latch.begin(b, actor, 7, 2);
        assertTrue(refresh.request());
        refresh.sent();
        assertFalse(refresh.request()); // a four-tick disagreement retry coalesces behind B

        var responseA = response(a, actor, 1);
        assertFalse(ExamineResponseStamp.correlates(responseA, 2, b, b, actor, 7));
        assertFalse(latch.accepts(responseA.target(), responseA.actorUuid(), responseA.epoch(), responseA.requestId()));
        assertFalse(refresh.correlatedResponse(false));
        assertTrue(refresh.inFlight(), "A's stale response cannot free B's outstanding slot");

        var responseB = response(b, actor, 2);
        assertTrue(ExamineResponseStamp.correlates(responseB, 2, b, b, actor, 7));
        assertTrue(latch.accepts(responseB.target(), responseB.actorUuid(), responseB.epoch(), responseB.requestId()));
        assertTrue(refresh.correlatedResponse(true), "B's correlated reply releases one coalesced follow-up");
        assertFalse(refresh.inFlight());
        assertTrue(refresh.request());
    }

    @Test void permissionRevocationFiltersStyledDetailsAndClampsScrollWithoutRevivingThem() {
        var entries = new java.util.ArrayList<ExamineEntry>();
        entries.add(ExamineEntry.publicText(Component.literal("Public"), "public", 0));
        for (int i = 0; i < 13; i++) entries.add(ExamineEntry.detail(
                Component.literal("Detail " + i).withStyle(ChatFormatting.GOLD), "detail", i));
        var response = new ExamineResponse(1, target, java.util.UUID.randomUUID(), 1, true,
                Component.literal("Target"), Component.empty(), entries, true, null);
        var permitted = ExamineClientPolicy.visibleEntryTexts(response, true);
        assertEquals(14, permitted.size());
        assertEquals(ChatFormatting.GOLD.getColor(), permitted.get(1).getStyle().getColor().getValue());
        int oldOffset = ExamineClientPolicy.clampScrollOffset(11, permitted.size(), 3);
        assertEquals(11, oldOffset);

        var revoked = ExamineClientPolicy.visibleEntryTexts(response, false);
        assertEquals(List.of(Component.literal("Public")), revoked);
        assertEquals(0, ExamineClientPolicy.clampScrollOffset(oldOffset, revoked.size(), 3));
        assertFalse(revoked.stream().anyMatch(line -> line.getStyle().getColor() != null
                && line.getStyle().getColor().getValue() == ChatFormatting.GOLD.getColor()));
    }

    @Test void presentationWidthsAdaptAndPreviewStaysSingleLine() {
        assertEquals(56, ExamineClientPolicy.popupWidth(10, 320));
        assertEquals(112, ExamineClientPolicy.popupWidth(400, 320));
        assertEquals(68, ExamineClientPolicy.popupWidth(80, 320));
        assertEquals(106, ExamineClientPolicy.popupWidth(130, 320));
        assertEquals(18, ExamineClientPolicy.popupCueMaxWidth(56));
        assertEquals(74, ExamineClientPolicy.popupCueMaxWidth(112));
        int narrowViewportWidth = ExamineClientPolicy.popupWidth(1_000, 80);
        assertTrue(narrowViewportWidth < 112);
        assertEquals(narrowViewportWidth - 38, ExamineClientPolicy.popupCueMaxWidth(narrowViewportWidth));
        assertEquals(83, ExamineClientPolicy.previewWidth((int)Math.ceil(100 * .75f), 320));
        assertTrue(ExamineClientPolicy.previewScale(400, 200) < .75f);
        assertEquals(.75f, ExamineClientPolicy.previewScale(20, 200));
        assertEquals(4, ExamineClientPolicy.previewLeft(10, 83, 320));
        assertEquals(119, ExamineClientPolicy.previewLeft(160, 83, 320));
        assertTrue(ExamineClientPolicy.tooltipTextPresent("Pin"));
        assertFalse(ExamineClientPolicy.tooltipTextPresent(" "));
    }

    @Test void pinHitboxMatchesTheRenderedPinControl() {
        int x = 20, width = 112;
        assertTrue(ExamineClientPolicy.pinHit(x + width - 25, x, width));
        assertFalse(ExamineClientPolicy.pinHit(x + width - 32, x, width));
        assertFalse(ExamineClientPolicy.pinHit(x + width - 14, x, width));
    }

    @Test void sharedStatusColumnPlacementStaysStableAsContentAndCursorChange() {
        int right = com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.chat(320, 240).right();
        int top = com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.examineTop(320, 240);
        assertEquals(164, top);
        for (int naturalTextWidth : new int[]{10, 130, 400, 1_000}) {
            int width = ExamineClientPolicy.popupWidth(naturalTextWidth, 320);
            int left = com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.examineLeft(320, width);
            assertEquals(right, left + width, "content changes width but remains aligned to shared column edge");
            assertEquals(top, com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.examineTop(320, 240));
            int rows = ExamineClientPolicy.popupVisibleRows(42, 9, 13);
            int height = ExamineClientPolicy.popupHeight(42, 9, rows);
            assertTrue(top + height <= 240 - com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.BOTTOM_RESERVE);
        }
        assertEquals(76, ExamineClientPolicy.previewTop(120, 20));
        assertEquals(4, ExamineClientPolicy.previewTop(10, 20));
    }
    @Test void everyEligibleWorldTargetIncludingTransparentGlassPreventsSceneFallback() {
        assertTrue(ExamineClientPolicy.sceneFallbackAllowed(java.util.List.of()));
        var glass = new ExamineSelector.Candidate(target, net.minecraft.world.phys.Vec3.ZERO, 1,
                "Glass", new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), true);
        assertFalse(ExamineClientPolicy.sceneFallbackAllowed(java.util.List.of(glass)));
    }

    private static ExamineResponse response(ExamineTarget target, java.util.UUID actor, long requestId) {
        return new ExamineResponse(requestId, target, actor, 7, true, Component.literal("Target"),
                Component.empty(), List.of(), false, null);
    }
}
