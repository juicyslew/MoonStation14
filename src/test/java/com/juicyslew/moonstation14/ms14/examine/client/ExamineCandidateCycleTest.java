package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamineCandidateCycleTest {
    private static ExamineSelector.Candidate candidate(ExamineTarget target, double distance, boolean transparent) {
        return new ExamineSelector.Candidate(target, Vec3.ZERO, distance, "", new AABB(0, 0, 0, 1, 1, 1), transparent);
    }

    @Test void stableTransparentAnchorKeepsPreferredTargetAcrossRayHitAndOrderingChanges() {
        var cycle = new ExamineCandidateCycle();
        var glass = ExamineTarget.block(new BlockPos(1, 0, 0));
        var behind = ExamineTarget.block(new BlockPos(2, 0, 0));
        var other = ExamineTarget.block(new BlockPos(3, 0, 0));
        assertEquals(behind, cycle.update(List.of(candidate(glass, 1, true), candidate(behind, 2, false),
                candidate(other, 3, false)), glass, null, 0));
        assertEquals(behind, cycle.update(List.of(candidate(other, 2, false), candidate(glass, 1.2, true),
                candidate(behind, 2.1, false)), glass, behind, 0));
        assertEquals(glass, cycle.update(List.of(candidate(other, 2, false), candidate(glass, 1.2, true),
                candidate(behind, 2.1, false)), glass, behind, 1));
    }

    @Test void popupExpiresInRealTimeAndUnpinStartsFreshDeadline() {
        var clock = new java.util.concurrent.atomic.AtomicLong(100);
        var popup = new ExamineClientPolicy.Popup(clock::get);
        var target = ExamineTarget.block(new BlockPos(1, 0, 0));
        popup.open(target, 7);
        clock.set(500); popup.pin(true);
        clock.set(10_000); assertTrue(popup.valid(target, 7, true));
        popup.pin(false);
        clock.set(19_999); assertTrue(popup.valid(target, 7, true));
        clock.set(20_000); assertFalse(popup.valid(target, 7, true));
    }

    @Test void repeatedRefreshChecksDoNotExtendUnpinnedLifetime() {
        var clock = new java.util.concurrent.atomic.AtomicLong(0);
        var popup = new ExamineClientPolicy.Popup(clock::get);
        var target = ExamineTarget.block(new BlockPos(4, 0, 0));
        popup.open(target, 2);
        clock.set(9_000); assertTrue(popup.valid(target, 2, true));
        clock.set(9_999); assertTrue(popup.valid(target, 2, true));
        clock.set(10_000); assertFalse(popup.valid(target, 2, true));
    }

    @Test void compactPopupLayoutKeepsSeparateControlsAtSmallGuiWidths() {
        int width = ExamineClientPolicy.popupWidth(220, 320);
        assertEquals(112, width);
        assertEquals(1, ExamineClientPolicy.popupWidth(220, 9));
        assertEquals(83, ExamineClientPolicy.previewWidth((int)Math.ceil(100 * .75f), 320));
        assertTrue(ExamineClientPolicy.pinHit(width - 20, 0, width));
        assertTrue(ExamineClientPolicy.closeHit(width - 7, 0, width));
        assertFalse(ExamineClientPolicy.closeHit(width - 20, 0, width));
    }

    @Test void liveEntityOutlineAdapterUsesCurrentMovedBoundsAndRejectsReplacement() {
        var id = java.util.UUID.randomUUID();
        var moved = new AABB(8, 2, 3, 9, 4, 4);
        assertEquals(moved, com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.liveEntityBounds(moved, id, id, false));
        assertEquals(null, com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.liveEntityBounds(
                moved, java.util.UUID.randomUUID(), id, false));
    }

    @Test void previewUsesOnlyCurrentCandidatePublicName() {
        var candidate = new ExamineSelector.Candidate(ExamineTarget.block(new BlockPos(5, 0, 0)), Vec3.ZERO,
                1, "Public label", new AABB(0, 0, 0, 1, 1, 1));
        assertEquals("Public label", ExamineClientPolicy.publicPreviewName(candidate));
        assertEquals("", ExamineClientPolicy.publicPreviewName(null));
    }

    @Test void activeAndDifferentShiftPreviewRemainIndependentOutlines() {
        var active = ExamineTarget.block(new BlockPos(6, 0, 0));
        var preview = ExamineTarget.block(new BlockPos(7, 0, 0));
        assertTrue(ExamineClientPolicy.drawSeparatePreview(active, preview));
        assertFalse(ExamineClientPolicy.drawSeparatePreview(active, active));
    }

    @Test void automaticOpaqueChoiceUpdatesButManualChoiceSurvivesRayOrderChanges() {
        var cycle = new ExamineCandidateCycle();
        var glass = ExamineTarget.block(new BlockPos(1, 0, 0));
        var behind = ExamineTarget.block(new BlockPos(2, 0, 0));
        var farther = ExamineTarget.block(new BlockPos(3, 0, 0));
        assertEquals(glass, cycle.update(List.of(candidate(glass, 1, true)), glass, null, 0));
        assertEquals(behind, cycle.update(List.of(candidate(glass, 1, true), candidate(behind, 2, false)), glass, glass, 0));
        assertEquals(farther, cycle.update(List.of(candidate(glass, 1, true), candidate(behind, 2, false), candidate(farther, 3, false)), glass, behind, -1));
        assertEquals(farther, cycle.update(List.of(candidate(farther, 1, false), candidate(glass, 1, true), candidate(behind, 2, false)), glass, behind, 0));
    }
}
