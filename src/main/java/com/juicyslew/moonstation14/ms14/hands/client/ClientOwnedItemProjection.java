package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Projects an item stack only from a matching confirmed ownership snapshot. */
public final class ClientOwnedItemProjection {
    private ClientOwnedItemProjection() { }

    public static Optional<ItemStack> stack(BodyHandStateSnapshot snapshot, UUID body, long epoch,
            long revision, String activeHand, Location location, String slotId, String token) {
        if (snapshot == null || body == null || activeHand == null || location == null || token == null
                || snapshot.reason() != BodyHandStateSnapshot.Reason.OK || !body.equals(snapshot.bodyId())
                || epoch != snapshot.epoch() || revision != snapshot.revision()
                || !activeHand.equals(snapshot.activeHand())) return Optional.empty();
        ItemPresentation value = null;
        if (location == Location.HAND) {
            var entry = snapshot.hands().stream().filter(h -> h.id().equals(slotId)
                    && Objects.equals(h.token(), token)).findFirst().orElse(null);
            if (entry != null) value = entry.presentation();
        } else {
            var entry = snapshot.equipment().stream().filter(e -> e.id().equals(slotId)
                    && Objects.equals(e.token(), token)).findFirst().orElse(null);
            if (entry != null) value = entry.presentation();
        }
        return value == null ? Optional.empty() : Optional.of(value.stack().copy());
    }

    public enum Location { HAND, EQUIPMENT }
}
