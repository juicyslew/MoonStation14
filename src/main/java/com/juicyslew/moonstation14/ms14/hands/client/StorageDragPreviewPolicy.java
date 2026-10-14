package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;

import java.util.List;

/** Resolves a drag ghost from retained presentation while keeping action proof independent. */
final class StorageDragPreviewPolicy {
    record Source(String itemId, int count, GridGeometry.Footprint footprint, String identity, boolean actionable,
                  ItemPresentation presentation) {
        Source(String itemId, int count, GridGeometry.Footprint footprint, String identity, boolean actionable) {
            this(itemId, count, footprint, identity, actionable, null);
        }
    }

    private StorageDragPreviewPolicy() { }

    static Source resolve(String token, List<BodyHandStateSnapshot.GridEntry> presented,
                          List<BodyHandStateSnapshot.GridEntry> fresh, BodyHandClientPolicy.InventoryView presentation,
                          BodyHandClientPolicy.InventoryView proof) {
        if (token == null) return null;
        var entry = presented == null ? null : presented.stream().filter(e -> token.equals(e.token())).findFirst().orElse(null);
        String item; int count; String identity; boolean valid; ItemPresentation itemPresentation;
        if (entry != null) {
            item = entry.itemId(); count = entry.count(); identity = "entry:" + token;
            itemPresentation = entry.presentation();
            var current = fresh == null ? null : fresh.stream().filter(e -> token.equals(e.token())).findFirst().orElse(null);
            valid = current != null && current.equals(entry);
        } else {
            var hand = handForToken(presentation, token);
            if (hand == null) return null;
            item = hand.itemId(); count = hand.count(); identity = "hand:" + token;
            itemPresentation = hand.presentation();
            var current = handForToken(proof, token);
            valid = current != null && token.equals(current.token()) && item.equals(current.itemId()) && count == current.count();
        }
        var id = net.minecraft.resources.ResourceLocation.tryParse(item == null ? "" : item);
        if (id == null) return null;
        return new Source(item, count, ItemFootprints.clientForId(id), identity, valid, itemPresentation);
    }

    private static BodyHandStateSnapshot.Hand handForToken(BodyHandClientPolicy.InventoryView view, String token) {
        if (view == null) return null;
        if (view.first() != null && token.equals(view.first().token())) return view.first();
        if (view.second() != null && token.equals(view.second().token())) return view.second();
        return null;
    }
}

