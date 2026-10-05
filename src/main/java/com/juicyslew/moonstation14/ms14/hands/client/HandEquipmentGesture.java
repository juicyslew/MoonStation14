package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import java.util.UUID;

/** Immutable pointer intent; a fresh settled snapshot must still prove the exact source at release. */
final class HandEquipmentGesture {
    private HandEquipmentGesture() { }

    record Drag(HandBarLayout.Layout.Tile source, String handId, String token, String itemId,
                int count, UUID accountId, UUID bodyId, long epoch, long revision) { }
    record Plan(BodyHandActionRequest.Action action, String handId) { }
    record WornStorePlan(BodyHandActionRequest.Action action, String handId, String hostToken, String childToken) { }

    record HeldStorePlan(String hostHandId, String hostToken, String childToken,
                         int x, int y, int rotation) { }

    static HeldStorePlan heldStorePlan(Drag drag, HandBarLayout.Layout.Tile target,
            BodyHandClientPolicy.InventoryView view, BodyHandStateSnapshot proof,
            BodyHandClientPolicy.GridView grid) {
        if (drag == null || view == null || proof == null || grid == null
                || !proof.accountId().equals(drag.accountId()) || !proof.bodyId().equals(drag.bodyId())
                || proof.epoch() != drag.epoch() || proof.revision() != drag.revision()
                || (drag.source() != HandBarLayout.Layout.Tile.FIRST && drag.source() != HandBarLayout.Layout.Tile.SECOND)
                || (target != HandBarLayout.Layout.Tile.FIRST && target != HandBarLayout.Layout.Tile.SECOND)
                || drag.source() == target || drag.count() <= 0 || !grid.canStore()) return null;
        var source = drag.source() == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
        var host = target == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
        if (source == null || host == null || !BodyHandClientPolicy.nominatedGridStoreEligible(
                source, drag.token(), drag.itemId(), drag.count()) || !drag.handId().equals(source.id())
                || host.token() == null || !host.token().equals(grid.host().hostToken())) return null;
        var id = net.minecraft.resources.ResourceLocation.tryParse(drag.itemId());
        if (id == null) return null;
        var fit = grid.occupancy().firstFit(drag.token(),
                com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.clientForId(id));
        return fit.accepted() ? new HeldStorePlan(host.id(), host.token(), drag.token(),
                fit.placement().anchor().x(), fit.placement().anchor().y(), fit.placement().rotation().degrees()) : null;
    }


    static WornStorePlan wornStorePlan(Drag drag, HandBarLayout.Layout.Tile target,
            BodyHandClientPolicy.InventoryView view, BodyHandStateSnapshot proof) {
        if (drag == null || view == null || proof == null
                || !proof.accountId().equals(drag.accountId()) || !proof.bodyId().equals(drag.bodyId())
                || proof.epoch() != drag.epoch() || proof.revision() != drag.revision()
                || target != HandBarLayout.Layout.Tile.BACK && target != HandBarLayout.Layout.Tile.BELT
                || drag.source() != HandBarLayout.Layout.Tile.FIRST && drag.source() != HandBarLayout.Layout.Tile.SECOND
                || drag.count() <= 0 || "moonstation14:bag".equals(drag.itemId())
                || "moonstation14:belt".equals(drag.itemId())) return null;
        var hand = drag.source() == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
        var equipment = target == HandBarLayout.Layout.Tile.BACK ? view.back() : view.belt();
        var grid = target == HandBarLayout.Layout.Tile.BACK ? view.backGrid() : view.beltGrid();
        if (hand == null || !BodyHandClientPolicy.nominatedGridStoreEligible(hand, drag.token(), drag.itemId(), drag.count())
                || !drag.handId().equals(hand.id())
                || equipment == null || equipment.slot().token() == null || grid == null
                || !grid.host().hostToken().equals(equipment.slot().token())) return null;
        var id = net.minecraft.resources.ResourceLocation.tryParse(drag.itemId());
        if (id == null) return null;
        boolean fits = false;
        for (int y = 0; y < grid.host().height() && !fits; y++) for (int x = 0; x < grid.host().width(); x++)
            if (grid.occupancy().place(drag.token(), com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.clientForId(id),
                    new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(x, y),
                    com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0).accepted()) { fits = true; break; }
        if (!fits) return null;
        var action = target == HandBarLayout.Layout.Tile.BACK ? BodyHandActionRequest.Action.SMART_STORE_BACK
                : BodyHandActionRequest.Action.SMART_STORE_BELT;
        return new WornStorePlan(action, hand.id(), equipment.slot().token(), drag.token());
    }

    static Drag begin(HandBarLayout.Layout.Tile source, BodyHandClientPolicy.InventoryView view,
                      BodyHandStateSnapshot proof) {
        if (source == null || view == null || proof == null) return null;
        BodyHandStateSnapshot.Hand hand = source == HandBarLayout.Layout.Tile.FIRST ? view.first()
                : source == HandBarLayout.Layout.Tile.SECOND ? view.second() : null;
        if (hand != null) {
            if (!hand.id().equals(view.activeHand()) || hand.token() == null || hand.count() != 1
                    || !("moonstation14:bag".equals(hand.itemId()) || "moonstation14:belt".equals(hand.itemId()))) return null;
            return new Drag(source, hand.id(), hand.token(), hand.itemId(), hand.count(),
                    proof.accountId(), proof.bodyId(), proof.epoch(), proof.revision());
        }
        var equipment = source == HandBarLayout.Layout.Tile.BACK ? view.back()
                : source == HandBarLayout.Layout.Tile.BELT ? view.belt() : null;
        if (equipment == null || equipment.slot().token() == null) return null;
        var slot = equipment.slot();
        return new Drag(source, view.activeHand(), slot.token(), slot.itemId(), slot.count(),
                proof.accountId(), proof.bodyId(), proof.epoch(), proof.revision());
    }

    static Drag beginAny(HandBarLayout.Layout.Tile source, BodyHandClientPolicy.InventoryView view,
                         BodyHandStateSnapshot proof) {
        if (source == null || view == null || proof == null) return null;
        var hand = source == HandBarLayout.Layout.Tile.FIRST ? view.first()
                : source == HandBarLayout.Layout.Tile.SECOND ? view.second() : null;
        if (hand == null || hand.token() == null) return null;
        return new Drag(source, hand.id(), hand.token(), hand.itemId(), hand.count(), proof.accountId(),
                proof.bodyId(), proof.epoch(), proof.revision());
    }

    static Plan plan(Drag drag, HandBarLayout.Layout.Tile target,
            BodyHandClientPolicy.InventoryView view, BodyHandStateSnapshot proof) {
        if (drag == null || target == null || view == null || proof == null
                || !proof.accountId().equals(drag.accountId()) || !proof.bodyId().equals(drag.bodyId())
                || proof.epoch() != drag.epoch()
                 || proof.revision() != drag.revision()) return null;
        if (drag.source() == HandBarLayout.Layout.Tile.BACK || drag.source() == HandBarLayout.Layout.Tile.BELT) {
            var hand = target == HandBarLayout.Layout.Tile.FIRST ? view.first()
                    : target == HandBarLayout.Layout.Tile.SECOND ? view.second() : null;
            var equipment = drag.source() == HandBarLayout.Layout.Tile.BACK ? view.back() : view.belt();
            if (hand == null || hand.token() != null || equipment == null
                    || !drag.token().equals(equipment.slot().token())
                    || !drag.itemId().equals(equipment.slot().itemId())
                    || drag.count() != equipment.slot().count()) return null;
            var action = drag.source() == HandBarLayout.Layout.Tile.BACK
                    ? BodyHandActionRequest.Action.UNEQUIP_BACK : BodyHandActionRequest.Action.UNEQUIP_BELT;
            return equipment.slot().token() != null ? new Plan(action, hand.id()) : null;
        }
        if (target == HandBarLayout.Layout.Tile.FIRST || target == HandBarLayout.Layout.Tile.SECOND) {
            var source = drag.source() == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
            var destination = target == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
            if (drag.source() == target || source.token() == null || !drag.token().equals(source.token())
                    || !drag.itemId().equals(source.itemId()) || drag.count() != source.count()
                    || destination.token() != null) return null;
            return new Plan(BodyHandActionRequest.Action.TRANSFER_HAND, destination.id());
        }
        if (!view.activeHand().equals(drag.handId())) return null;
        var hand = drag.source() == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
        if (!hand.id().equals(drag.handId()) || !drag.token().equals(hand.token())
                || !drag.itemId().equals(hand.itemId()) || hand.count() != drag.count()) return null;
        var equipment = target == HandBarLayout.Layout.Tile.BACK ? view.back()
                : target == HandBarLayout.Layout.Tile.BELT ? view.belt() : null;
        if (equipment == null || equipment.slot().token() != null) return null;
        var action = target == HandBarLayout.Layout.Tile.BACK ? BodyHandActionRequest.Action.EQUIP_BACK
                : BodyHandActionRequest.Action.EQUIP_BELT;
        return equipment.allows(action) ? new Plan(action, hand.id()) : null;
    }
}
