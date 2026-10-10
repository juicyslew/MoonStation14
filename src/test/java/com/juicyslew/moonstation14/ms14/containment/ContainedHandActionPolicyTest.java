package com.juicyslew.moonstation14.ms14.containment;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainedHandActionPolicyTest {
    @Test void bodyOwnedActionsAllowedAndWorldActionsDenied() {
        for (var action : BodyHandActionRequest.Action.values()) {
            boolean bodyLocal = switch (action) {
                case SELECT_HAND, TRANSFER_HAND, EQUIP_BELT, EQUIP_BACK, UNEQUIP_BELT, UNEQUIP_BACK, EQUIP_SLOT, UNEQUIP_SLOT,
                        STORE_BELT, STORE_BACK, TAKE_BELT, TAKE_BACK,
                        GRID_STORE_BELT, GRID_STORE_BACK, GRID_TAKE_BELT, GRID_TAKE_BACK,
                        GRID_MOVE_BELT, GRID_MOVE_BACK, HELD_GRID_STORE, HELD_GRID_TAKE, HELD_GRID_MOVE,
                        SMART_STORE_BELT, SMART_STORE_BACK, SMART_TAKE_BELT, SMART_TAKE_BACK,
                        GRID_CROSS_BELT_TO_BACK, GRID_CROSS_BACK_TO_BELT,
                        GRID_BODY_WORN_TO_HELD, GRID_BODY_HELD_TO_WORN, GRID_BODY_HELD_TO_HELD,
                        GRID_BODY_TO_EQUIPMENT, GRID_EQUIPMENT_TO_BODY, QUICK_EQUIP,
                        USE_HELD, USE_IN_HAND, PRIME_GRENADE, CYCLE_SYRINGE_AMOUNT, MOVE_EQUIPMENT, STOP_PULL, DROP -> true;
                case ACTIVATE_TARGET -> false;
                default -> false;
            };
            assertEquals(bodyLocal, DisposalContainedActionPolicy.permitsBodyHandAction(action), action.name());
        }
        assertFalse(DisposalContainedActionPolicy.permitsBodyHandAction(null));
    }
}
