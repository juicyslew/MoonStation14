package com.juicyslew.moonstation14.ms14.hands.transfer;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import java.util.Objects;
import java.util.UUID;

/** Pure domain location. These records describe identity only, never Minecraft objects. */
public sealed interface ItemLocation permits ItemLocation.BodyHand, ItemLocation.BodyEquipment,
        ItemLocation.StorageCell, ItemLocation.WorldEntity {
    int MAX_ID_LENGTH = 64;

    record BodyHand(UUID bodyId, String handId) implements ItemLocation {
        public BodyHand { Objects.requireNonNull(bodyId, "bodyId"); validateId(handId, "handId"); }
    }

    record BodyEquipment(UUID bodyId, String slotId) implements ItemLocation {
        public BodyEquipment { Objects.requireNonNull(bodyId, "bodyId"); validateId(slotId, "slotId"); }
    }

    /** A generic cell in item-owned storage; it is not a body-owned grid. */
    record StorageCell(ItemToken container, String anchor, Orientation orientation, String cellId)
            implements ItemLocation {
        public StorageCell {
            Objects.requireNonNull(container, "container");
            validateId(anchor, "anchor");
            Objects.requireNonNull(orientation, "orientation");
            validateId(cellId, "cellId");
        }
    }

    enum Orientation { UNSPECIFIED, NORTH, EAST, SOUTH, WEST }

    record WorldEntity(UUID worldId, UUID entityId) implements ItemLocation {
        public WorldEntity { Objects.requireNonNull(worldId, "worldId"); Objects.requireNonNull(entityId, "entityId"); }
    }

    private static void validateId(String value, String name) {
        if (value == null || value.isBlank() || value.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(name + " must be nonblank and at most " + MAX_ID_LENGTH + " characters");
        }
    }
}
