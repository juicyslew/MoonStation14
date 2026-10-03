package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.juicyslew.moonstation14.ms14.hands.client.HandBarLayout.Layout.Tile.*;
import static org.junit.jupiter.api.Assertions.*;

class HandEquipmentGestureTest {
    private static final String BAG = "moonstation14:bag", BELT_ITEM = "moonstation14:belt";

    private BodyHandStateSnapshot snapshot(String active, String firstItem, String secondItem, boolean back, boolean belt) {
        var first = firstItem == null ? new BodyHandStateSnapshot.Hand("left", null, null, 0)
                : new BodyHandStateSnapshot.Hand("left", "first", firstItem, 1);
        var second = secondItem == null ? new BodyHandStateSnapshot.Hand("right", null, null, 0)
                : new BodyHandStateSnapshot.Hand("right", "second", secondItem, 1);
        var backSlot = new BodyHandStateSnapshot.EquipmentSlot("back", back ? "wornBag" : null,
                back ? BAG : null, back ? 1 : 0, null, null, 0);
        var beltSlot = new BodyHandStateSnapshot.EquipmentSlot("belt", belt ? "wornBelt" : null,
                belt ? BELT_ITEM : null, belt ? 1 : 0, null, null, 0);
        return new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 7, 3, active, List.of(first, second), List.of(backSlot, beltSlot));
    }

    private BodyHandClientPolicy.InventoryView view(BodyHandStateSnapshot proof) {
        var first = proof.hands().get(0); var second = proof.hands().get(1);
        var active = proof.activeHand().equals(first.id()) ? first : second;
        var back = proof.equipment().get(0); var belt = proof.equipment().get(1);
        return new BodyHandClientPolicy.InventoryView(first, second, proof.activeHand(),
                new BodyHandClientPolicy.EquipmentView(belt, belt.token() == null && BELT_ITEM.equals(active.itemId()),
                        belt.token() != null && active.token() == null, false, false),
                new BodyHandClientPolicy.EquipmentView(back, back.token() == null && BAG.equals(active.itemId()),
                        back.token() != null && active.token() == null, false, false), null, null);
    }

    @Test void onlyMatchingActiveHandCanEquipIntoEmptyCorrectSlot() {
        var proof = snapshot("right", null, BAG, false, false);
        assertNull(HandEquipmentGesture.begin(FIRST, view(proof), proof));
        var drag = HandEquipmentGesture.begin(SECOND, view(proof), proof);
        assertEquals("right", drag.handId());
        assertEquals(new HandEquipmentGesture.Plan(BodyHandActionRequest.Action.EQUIP_BACK, "right"),
                HandEquipmentGesture.plan(drag, BACK, view(proof), proof));
        assertNull(HandEquipmentGesture.plan(drag, BELT, view(proof), proof));
        assertEquals(new HandEquipmentGesture.Plan(BodyHandActionRequest.Action.TRANSFER_HAND, "left"),
                HandEquipmentGesture.plan(drag, FIRST, view(proof), proof));
        assertNull(HandEquipmentGesture.plan(drag, BACK, null, proof)); // pending query/action
        assertNull(HandEquipmentGesture.plan(drag, BACK, view(proof), null)); // lost authority
        var otherBody = snapshot("right", null, BAG, false, false);
        assertNull(HandEquipmentGesture.plan(drag, BACK, view(otherBody), otherBody));
        assertNull(HandEquipmentGesture.plan(drag, BACK, view(snapshot("right", null, BAG, true, false)), proof));
    }

    @Test void wornEquipmentMovesIntoEitherEmptyHand() {
        var proof = snapshot("right", BAG, null, true, true);
        var drag = HandEquipmentGesture.begin(BACK, view(proof), proof);
        assertEquals(new HandEquipmentGesture.Plan(BodyHandActionRequest.Action.UNEQUIP_BACK, "right"),
                HandEquipmentGesture.plan(drag, SECOND, view(proof), proof));
        var nonActive = snapshot("right", null, BAG, true, false);
        var otherDrag = HandEquipmentGesture.begin(BACK, view(nonActive), nonActive);
        assertEquals(new HandEquipmentGesture.Plan(BodyHandActionRequest.Action.UNEQUIP_BACK, "left"),
                HandEquipmentGesture.plan(otherDrag, FIRST, view(nonActive), nonActive));
        assertNull(HandEquipmentGesture.plan(otherDrag, FIRST, null, nonActive));
        assertNull(HandEquipmentGesture.plan(otherDrag, FIRST, view(nonActive), null));
        assertNull(HandEquipmentGesture.plan(otherDrag, FIRST, view(proof), proof));
        assertNull(HandEquipmentGesture.plan(drag, FIRST, view(proof), proof));
        assertNull(HandEquipmentGesture.plan(drag, BELT, view(proof), proof));
        var occupied = snapshot("right", BAG, BAG, true, true);
        assertNull(HandEquipmentGesture.plan(drag, SECOND, view(occupied), proof));
        assertNull(HandEquipmentGesture.plan(drag, SECOND, view(proof),
                new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, proof.accountId(), proof.bodyId(),
                        proof.epoch(), proof.revision() + 1, proof.activeHand(), proof.hands(), proof.equipment())));
        assertEquals(new HandEquipmentGesture.Plan(BodyHandActionRequest.Action.UNEQUIP_BELT, "right"),
                HandEquipmentGesture.plan(HandEquipmentGesture.begin(BELT, view(proof), proof), SECOND, view(proof), proof));
    }

    @Test void smartStoreUsesNominatedInactiveHandAndValidatesWornHost() {
        var base = snapshot("left", BAG, "minecraft:stone", true, true);
        var second = base.hands().get(1);
        var belt = base.equipment().get(1);
        var policy = new BodyHandClientPolicy();
        policy.bind(base.accountId(), base.bodyId(), base.epoch());
        long query = policy.query(1);
        var proof = new BodyHandStateSnapshot(0, query, base.reason(), base.accountId(), base.bodyId(), base.epoch(),
                base.revision(), base.activeHand(), base.hands(), base.equipment(), List.of(
                new BodyHandStateSnapshot.GridHost("wornBag", "bag", 7, 4, List.of()),
                new BodyHandStateSnapshot.GridHost("wornBelt", "belt", 8, 2, List.of())));
        assertTrue(policy.accept(proof));
        var view = policy.inventoryView();
        var drag = new HandEquipmentGesture.Drag(SECOND, second.id(), second.token(), second.itemId(), second.count(),
                base.accountId(), base.bodyId(), base.epoch(), base.revision());
        assertEquals(BodyHandActionRequest.Action.SMART_STORE_BACK,
                HandEquipmentGesture.wornStorePlan(drag, BACK, view, policy.snapshot()).action());
        assertEquals(BodyHandActionRequest.Action.SMART_STORE_BELT,
                HandEquipmentGesture.wornStorePlan(drag, BELT, view, policy.snapshot()).action());
        assertNull(HandEquipmentGesture.wornStorePlan(drag, BACK, view,
                new BodyHandStateSnapshot(0, query, base.reason(), base.accountId(), base.bodyId(), base.epoch(),
                        base.revision() + 1, base.activeHand(), base.hands(), base.equipment(), proof.grids())));
        var wrongHost = new BodyHandClientPolicy.InventoryView(view.first(), view.second(), view.activeHand(), view.belt(),
                view.back(), view.beltGrid(), new BodyHandClientPolicy.GridView(
                new BodyHandStateSnapshot.GridHost("stale", "bag", 7, 4, List.of()), view.backGrid().occupancy(), false, false));
        assertNull(HandEquipmentGesture.wornStorePlan(drag, BACK, wrongHost, policy.snapshot()));
    }

    @Test void heldGridStorePlansFromEitherHandAgainstFreshHostProof() {
        var base = snapshot("left", BAG, "minecraft:stone", false, false);
        var proof = new BodyHandStateSnapshot(0, 1, base.reason(), base.accountId(), base.bodyId(), base.epoch(),
                base.revision(), base.activeHand(), base.hands(), base.equipment(), List.of(
                new BodyHandStateSnapshot.GridHost("first", "bag", 7, 4, List.of())));
        var policy = new BodyHandClientPolicy();
        policy.bind(proof.accountId(), proof.bodyId(), proof.epoch());
        long query = policy.query(1);
        var reply = new BodyHandStateSnapshot(0, query, proof.reason(), proof.accountId(), proof.bodyId(),
                proof.epoch(), proof.revision(), proof.activeHand(), proof.hands(), proof.equipment(), proof.grids());
        assertTrue(policy.accept(reply));
        var view = policy.inventoryView();
        var drag = HandEquipmentGesture.beginAny(SECOND, view, policy.snapshot());
        var plan = HandEquipmentGesture.heldStorePlan(drag, FIRST, view, policy.snapshot(), policy.heldGrid("first", false));
        assertNotNull(plan);
        assertEquals("left", plan.hostHandId());
        assertEquals("first", plan.hostToken());
        assertEquals("second", plan.childToken());

        var reverseBase = snapshot("right", "minecraft:stone", BAG, false, false);
        var reverseProof = new BodyHandStateSnapshot(0, 1, reverseBase.reason(), reverseBase.accountId(), reverseBase.bodyId(),
                reverseBase.epoch(), reverseBase.revision(), reverseBase.activeHand(), reverseBase.hands(), reverseBase.equipment(), List.of(
                new BodyHandStateSnapshot.GridHost("second", "bag", 7, 4, List.of())));
        var reverse = new BodyHandClientPolicy();
        reverse.bind(reverseProof.accountId(), reverseProof.bodyId(), reverseProof.epoch());
        long reverseQuery = reverse.query(1);
        assertTrue(reverse.accept(new BodyHandStateSnapshot(0, reverseQuery, reverseProof.reason(), reverseProof.accountId(),
                reverseProof.bodyId(), reverseProof.epoch(), reverseProof.revision(), reverseProof.activeHand(),
                reverseProof.hands(), reverseProof.equipment(), reverseProof.grids())));
        var reverseView = reverse.inventoryView();
        assertNotNull(HandEquipmentGesture.heldStorePlan(HandEquipmentGesture.beginAny(FIRST, reverseView, reverse.snapshot()),
                SECOND, reverseView, reverse.snapshot(), reverse.heldGrid("second", false)));
        assertNull(HandEquipmentGesture.heldStorePlan(drag, FIRST, view, policy.snapshot(), null));
        assertNull(HandEquipmentGesture.heldStorePlan(drag, SECOND, view, policy.snapshot(), policy.heldGrid("first", false)));
        var stale = new BodyHandStateSnapshot(0, query, proof.reason(), proof.accountId(), proof.bodyId(), proof.epoch(),
                proof.revision() + 1, proof.activeHand(), proof.hands(), proof.equipment(), proof.grids());
        assertNull(HandEquipmentGesture.heldStorePlan(drag, FIRST, view, stale, policy.heldGrid("first", false)));
    }
}
