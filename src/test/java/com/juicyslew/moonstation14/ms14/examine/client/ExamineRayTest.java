package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExamineRayTest {
    @Test void intersectsNearestVisibleBoxAndRejectsMissOrOutOfRange() {
        Vec3 origin = Vec3.ZERO, direction = new Vec3(0, 0, 4);
        var hit = ExamineRay.intersect(origin, direction, 8, new AABB(-1, -1, 3, 1, 1, 4));
        assertNotNull(hit);
        assertEquals(3, hit.distance(), 1e-9);
        assertNull(ExamineRay.intersect(origin, direction, 2, new AABB(-1, -1, 3, 1, 1, 4)));
        assertNull(ExamineRay.intersect(origin, direction, 8, new AABB(2, 2, 3, 3, 3, 4)));
    }

    @Test void candidateCycleKeepsIdentityWhenRaySetStableAndResetsWhenItChanges() {
        var a = candidate(ExamineTarget.block(new BlockPos(0, 0, 2)), 2);
        var b = candidate(ExamineTarget.entity(4, UUID.fromString("00000000-0000-0000-0000-000000000004")), 3);
        var cycle = new ExamineCandidateCycle();
        assertEquals(a.target(), cycle.update(List.of(a, b), 0));
        assertEquals(b.target(), cycle.update(List.of(a, b), -1));
        assertEquals(b.target(), cycle.update(List.of(a, b), 0));
        var changed = candidate(ExamineTarget.block(new BlockPos(1, 0, 2)), 2);
        assertEquals(b.target(), cycle.update(List.of(changed, b), 0));
        assertNull(cycle.update(List.of(), 1));
    }

    @Test void productionOrderingIsNearestThenStableIdentityAndBounded() {
        var farther = candidate(ExamineTarget.block(new BlockPos(0, 0, 5)), 5);
        var nearest = candidate(ExamineTarget.block(new BlockPos(0, 0, 1)), 1);
        var tied = candidate(ExamineTarget.block(new BlockPos(0, 0, 2)), 1);
        assertEquals(List.of(nearest.target(), tied.target()), ExamineCandidateOrdering.nearest(
                List.of(farther, tied, nearest), 2).stream().map(ExamineSelector.Candidate::target).toList());
    }

    @Test void cyclePrefersOpaqueDefaultAndRetainsChoiceAcrossGeometryAndListChanges() {
        var glass = new ExamineSelector.Candidate(ExamineTarget.block(new BlockPos(0, 0, 1)),
                new Vec3(0, 0, 1), 1, "glass", new AABB(0, 0, 1, 1, 1, 1.1), true);
        var behind = candidate(ExamineTarget.block(new BlockPos(0, 0, 3)), 3);
        var anchor = glass.target();
        var cycle = new ExamineCandidateCycle();
        assertEquals(behind.target(), cycle.update(List.of(glass, behind), anchor, null, 0));
        assertEquals(glass.target(), cycle.update(List.of(behind, glass), anchor, null, -1));
        var movedGlass = new ExamineSelector.Candidate(glass.target(), new Vec3(0, 0, 1.1), 1.1,
                "glass", new AABB(0, 0, 1, 1, 1, 1.1), true);
        assertEquals(glass.target(), cycle.update(List.of(behind, movedGlass), anchor, null, 0));
        assertEquals(behind.target(), cycle.update(List.of(behind), anchor, null, 0));
    }

    @Test void staleRequestTargetActorAndEpochRepliesAreRejectedByProductionGuard() {
        UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000010");
        ExamineTarget target = ExamineTarget.block(new BlockPos(2, 3, 4));
        var response = new com.juicyslew.moonstation14.ms14.examine.ExamineResponse(7, target, actor, 9,
                true, net.minecraft.network.chat.Component.literal("Door"), net.minecraft.network.chat.Component.empty(),
                List.of(), false);
        assertTrue(ExamineResponseStamp.matches(response, 7, target, target, actor, 9, null, false));
        assertFalse(ExamineResponseStamp.matches(response, 6, target, target, actor, 9, null, false));
        assertFalse(ExamineResponseStamp.matches(response, 7, ExamineTarget.block(new BlockPos(2, 3, 5)), target,
                actor, 9, null, false));
        assertFalse(ExamineResponseStamp.matches(response, 7, target, ExamineTarget.block(new BlockPos(2, 3, 5)),
                actor, 9, null, false));
        assertFalse(ExamineResponseStamp.matches(response, 7, target, target,
                UUID.fromString("00000000-0000-0000-0000-000000000011"), 9, null, false));
        assertFalse(ExamineResponseStamp.matches(response, 7, target, target, actor, 10, null, false));
        UUID controller = UUID.fromString("00000000-0000-0000-0000-000000000030");
        var stamped = new com.juicyslew.moonstation14.ms14.examine.ExamineResponse(7, target, actor, 9,
                true, net.minecraft.network.chat.Component.literal("APC"), net.minecraft.network.chat.Component.empty(),
                List.of(), true, controller);
        assertTrue(ExamineResponseStamp.matches(stamped, 7, target, target, actor, 9, controller, false));
        assertFalse(ExamineResponseStamp.matches(stamped, 7, target, target, actor, 9,
                UUID.fromString("00000000-0000-0000-0000-000000000031"), false));
        assertTrue(ExamineResponseStamp.matches(stamped, 7, target, target, actor, 9, null, true));
    }

    @Test void correlatedBlockEntityDenialNeedsNoStampButAcceptedResultDoes() {
        UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000010");
        ExamineTarget target = ExamineTarget.block(new BlockPos(2, 3, 4));
        var denied = new com.juicyslew.moonstation14.ms14.examine.ExamineResponse(7, target, actor, 9,
                false, net.minecraft.network.chat.Component.literal("Source"), net.minecraft.network.chat.Component.empty(),
                List.of(), false, null);
        assertTrue(ExamineResponseStamp.acceptsBoundary(denied, 7, target, target, actor, 9, null, false));
        var acceptedMissingStamp = new com.juicyslew.moonstation14.ms14.examine.ExamineResponse(7, target, actor, 9,
                true, net.minecraft.network.chat.Component.literal("Source"), net.minecraft.network.chat.Component.empty(),
                List.of(), false, null);
        assertTrue(ExamineResponseStamp.acceptsBoundary(acceptedMissingStamp, 7, target, target, actor, 9, null, false));
        assertFalse(ExamineResponseStamp.acceptsBoundary(denied, 7, target, target,
                UUID.fromString("00000000-0000-0000-0000-000000000011"), 9, null, false));
        assertFalse(ExamineResponseStamp.acceptsBoundary(denied, 7, target, target, actor, 10, null, false));
        assertFalse(ExamineResponseStamp.acceptsBoundary(denied, 7, target,
                ExamineTarget.block(new BlockPos(2, 3, 5)), actor, 9, null, false));
    }

    @Test void clientControllerIdentityRejectsSameTypeReplacementButAllowsSameControllerStateUpdates() {
        var target = ExamineTarget.block(new BlockPos(3, 4, 5));
        var blockId = net.minecraft.resources.ResourceLocation.parse("moonstation14:airlock");
        Object oldController = new Object(), replacement = new Object();
        var oldBinding = new ExamineControllerIdentity.Binding(target, oldController, blockId, null);
        var openState = new ExamineControllerIdentity.Binding(target, oldController, blockId, null);
        var replacementBinding = new ExamineControllerIdentity.Binding(target, replacement, blockId, null);
        assertTrue(ExamineControllerIdentity.same(oldBinding, openState));
        assertFalse(ExamineControllerIdentity.same(oldBinding, replacementBinding));

        var ordinary = new ExamineControllerIdentity.Binding(target, null, blockId, "airlock_state[open=false]");
        var changedOrdinaryState = new ExamineControllerIdentity.Binding(target, null, blockId, "airlock_state[open=true]");
        assertFalse(ExamineControllerIdentity.same(ordinary, changedOrdinaryState));
    }

    @Test void oldDetailsNeverUseTheNewlySelectedTargetsNearDistance() {
        var apc = ExamineTarget.block(new BlockPos(0, 0, 2));
        var apcCandidate = candidate(apc, 2);
        var other = candidate(ExamineTarget.block(new BlockPos(0, 0, 100)), 100);
        var actor = UUID.fromString("00000000-0000-0000-0000-000000000020");
        var batteryDetail = new com.juicyslew.moonstation14.ms14.examine.ExamineEntry(
                com.juicyslew.moonstation14.ms14.examine.ExamineEntry.Visibility.DETAIL,
                net.minecraft.network.chat.Component.literal("Battery charge"), "Power", 1);
        var response = new com.juicyslew.moonstation14.ms14.examine.ExamineResponse(1, apc, actor, 1,
                true, net.minecraft.network.chat.Component.literal("APC"), net.minecraft.network.chat.Component.empty(),
                List.of(batteryDetail), true);
        assertTrue(ExamineDetailPolicy.mayShow(response, apcCandidate, Vec3.ZERO, true));
        assertFalse(ExamineDetailPolicy.mayShow(response, other, new Vec3(0, 0, 99), true));
    }

    private static ExamineSelector.Candidate candidate(ExamineTarget target, double distance) {
        return new ExamineSelector.Candidate(target, new Vec3(0, 0, distance), distance, "target",
                new AABB(0, 0, distance, 1, 1, distance + 1));
    }
}
