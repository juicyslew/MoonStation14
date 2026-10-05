package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SmartStorageGestureTest {
    private static final String BAG = "moonstation14:bag", BELT = "moonstation14:belt";

    private static BodyHandStateSnapshot state(String active, String item, boolean worn, boolean belt, List<BodyHandStateSnapshot.GridEntry> entries) {
        var hands = List.of(new BodyHandStateSnapshot.Hand("left", item == null ? null : "held", item, item == null ? 0 : 1),
                new BodyHandStateSnapshot.Hand("right", null, null, 0));
        var slot = new BodyHandStateSnapshot.EquipmentSlot(belt ? "belt" : "back", worn ? "host" : null,
                worn ? belt ? BELT : BAG : null, worn ? 1 : 0, null, null, 0);
        var grid = worn ? List.of(new BodyHandStateSnapshot.GridHost("host", belt ? "belt" : "bag",
                belt ? 8 : 7, belt ? 2 : 4, entries)) : List.<BodyHandStateSnapshot.GridHost>of();
        return new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), UUID.randomUUID(),
                7, 3, active, hands, List.of(slot), grid);
    }

    private static BodyHandClientPolicy policy(BodyHandStateSnapshot state) {
        var policy = new BodyHandClientPolicy();
        policy.bind(state.accountId(), state.bodyId(), state.epoch());
        long query = policy.query(1);
        var reply = new BodyHandStateSnapshot(0, query, state.reason(), state.accountId(), state.bodyId(),
                state.epoch(), state.revision(), state.activeHand(), state.hands(), state.equipment(), state.grids());
        assertTrue(policy.accept(reply));
        return policy;
    }

    private static SmartStorageGesture.Plan plan(BodyHandClientPolicy policy, boolean belt) {
        return SmartStorageGesture.plan(belt, policy.inventoryView(), policy.snapshot());
    }

    @Test void emptySlotRequiresCorrectHostInActiveHand() {
        var bag = policy(state("left", BAG, false, false, List.of()));
        assertEquals(new SmartStorageGesture.Plan(Action.EQUIP_BACK, "left", "held", null), plan(bag, false));
        assertNull(plan(bag, true));
        assertNull(plan(policy(state("left", BELT, false, false, List.of())), false));
        assertNull(plan(policy(state("right", BAG, false, false, List.of())), false));
        assertEquals(Action.EQUIP_BELT, plan(policy(state("left", BELT, false, true, List.of())), true).action());
    }

    @Test void occupiedHostStoresOnlyEligibleActiveChildAndNeverUnequips() {
        var store = plan(policy(state("left", "minecraft:stone", true, false, List.of())), false);
        assertEquals(new SmartStorageGesture.Plan(Action.SMART_STORE_BACK, "left", "host", "held"), store);
        assertEquals(Action.SMART_STORE_BELT, plan(policy(state("left", "minecraft:stone", true, true, List.of())), true).action());
        assertNull(plan(policy(state("right", "minecraft:stone", true, false, List.of())), false));
        assertNull(plan(policy(state("left", BAG, true, false, List.of())), false));
        assertNull(plan(policy(state("left", BELT, true, false, List.of())), false));
    }

    @Test void emptyHandTakesLastEntryOnlyWithMatchingHostProof() {
        var entries = List.of(new BodyHandStateSnapshot.GridEntry("first", "minecraft:stone", 1, 0, 0, 0),
                new BodyHandStateSnapshot.GridEntry("last", "minecraft:dirt", 1, 1, 0, 0));
        var policy = policy(state("right", null, true, false, entries));
        assertEquals(new SmartStorageGesture.Plan(Action.SMART_TAKE_BACK, "right", "host", "last"), plan(policy, false));
        assertNull(plan(policy(state("right", null, true, false, List.of())), false));
        assertNull(plan(policy(state("left", "minecraft:stone", true, false, entries)), true));
        var proof = policy.snapshot();
        var wrongGrid = new BodyHandClientPolicy.InventoryView(policy.inventoryView().first(), policy.inventoryView().second(),
                policy.inventoryView().activeHand(), null, policy.inventoryView().back(), null,
                new BodyHandClientPolicy.GridView(new BodyHandStateSnapshot.GridHost("other", "bag", 7, 4, entries),
                        policy.inventoryView().backGrid().occupancy(), false, true));
        assertNull(SmartStorageGesture.plan(false, wrongGrid, proof));
        assertNull(SmartStorageGesture.plan(false, policy.inventoryView(),
                new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, proof.accountId(), proof.bodyId(),
                        proof.epoch(), proof.revision() + 1, proof.activeHand(), proof.hands(), proof.equipment(), List.of())));
    }

    @Test void pendingProofCannotPlanFromPresentationOrOldSnapshot() {
        var policy = policy(state("left", "minecraft:stone", true, false, List.of()));
        var old = policy.snapshot();
        assertNotNull(plan(policy, false));
        assertNotEquals(0, policy.action(2));
        assertNull(plan(policy, false));
        assertNull(SmartStorageGesture.plan(false, policy.presentationView(), old));
        assertNull(SmartStorageGesture.plan(false, null, old));
    }
}
