package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;

/** Pure intent selection from a settled, matching hand/equipment/grid proof. Never plans unequip. */
public final class SmartStorageGesture {
    private SmartStorageGesture() { }

    public record Plan(BodyHandActionRequest.Action action, String handId, String hostOrHandToken,
                       String childOrSourceToken) { }

    public static Plan plan(boolean belt, BodyHandClientPolicy.InventoryView view, BodyHandStateSnapshot proof) {
        if (view == null || proof == null || proof.reason() != BodyHandStateSnapshot.Reason.OK
                || proof.epoch() <= 0 || proof.revision() < 0 || proof.hands().size() != view.handCount()
                || !proof.activeHand().equals(view.activeHand())
                || !proof.hands().get(0).equals(view.first())
                || view.second() != null && !proof.hands().get(1).equals(view.second())) return null;
        var active = view.active();
        var equipment = belt ? view.belt() : view.back();
        if (active == null || equipment == null) return null;
        String slotId = belt ? "belt" : "back";
        var slots = proof.equipment().stream().filter(s -> slotId.equals(s.id())).toList();
        if (slots.size() != 1 || !slots.getFirst().equals(equipment.slot())) return null;
        var slot = slots.getFirst();
        if (slot.token() == null) {
            if (!equipment.canEquip() || active.token() == null || active.count() != 1
                    || !(belt ? "moonstation14:belt" : "moonstation14:bag").equals(active.itemId())) return null;
            return new Plan(belt ? BodyHandActionRequest.Action.EQUIP_BELT : BodyHandActionRequest.Action.EQUIP_BACK,
                    active.id(), active.token(), null);
        }
        var grid = belt ? view.beltGrid() : view.backGrid();
        var hosts = proof.grids().stream().filter(g -> slot.token().equals(g.hostToken())).toList();
        if (grid == null || hosts.size() != 1 || !hosts.getFirst().equals(grid.host())
                || !(belt ? "belt" : "bag").equals(grid.host().kind()) || slot.childToken() != null) return null;
        if (active.token() != null) {
            if (!grid.canStore() || active.count() > 64 || active.pouch()
                    || "moonstation14:bag".equals(active.itemId()) || "moonstation14:belt".equals(active.itemId())) return null;
            return new Plan(belt ? BodyHandActionRequest.Action.SMART_STORE_BELT : BodyHandActionRequest.Action.SMART_STORE_BACK,
                    active.id(), slot.token(), active.token());
        }
        var entries = grid.host().entries();
        if (!grid.canTake() || entries.isEmpty()) return null;
        return new Plan(belt ? BodyHandActionRequest.Action.SMART_TAKE_BELT : BodyHandActionRequest.Action.SMART_TAKE_BACK,
                active.id(), slot.token(), entries.getLast().token());
    }
}
