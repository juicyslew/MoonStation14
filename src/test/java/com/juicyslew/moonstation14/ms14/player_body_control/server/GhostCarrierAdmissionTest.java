package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostCarrierAdmissionTest {
    private static java.util.List<ItemStack> empty(int count) {
        return new ArrayList<>(Collections.nCopies(count, ItemStack.EMPTY));
    }

    private static CarrierHandInventoryGate.Fixture fixture(UUID account, CreativeParkedInventory park,
                                                              java.util.List<ItemStack> main) {
        return new CarrierHandInventoryGate.Fixture(account, park != null, park, main, empty(4), empty(1),
                ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(46), 0);
    }

    @Test
    void creativeMayParkButOtherModesNeverOwnNonemptyCarrier() {
        UUID account = UUID.randomUUID();
        var occupied = empty(36);
        occupied.set(0, new ItemStack(Items.DIAMOND));
        assertTrue(GhostMobHarnessControl.carrierAdmitted(GameType.CREATIVE, fixture(account, null, occupied)));
        assertFalse(GhostMobHarnessControl.carrierAdmitted(GameType.SPECTATOR, fixture(account, null, occupied)));
        assertFalse(GhostMobHarnessControl.carrierAdmitted(GameType.SURVIVAL, fixture(account, null, occupied)));
        assertFalse(GhostMobHarnessControl.carrierAdmitted(GameType.ADVENTURE, fixture(account, null, empty(36))));
        assertTrue(GhostMobHarnessControl.carrierAdmitted(GameType.SPECTATOR, fixture(account, null, empty(36))));

        var snapshot = CreativeInventorySnapshot.capture(empty(36), empty(4), empty(1), ItemStack.EMPTY, 0, empty(27));
        var park = new CreativeParkedInventory(account, snapshot);
        assertTrue(GhostMobHarnessControl.carrierAdmitted(GameType.CREATIVE, fixture(account, park, empty(36))));
        assertFalse(GhostMobHarnessControl.carrierAdmitted(GameType.CREATIVE,
                fixture(account, new CreativeParkedInventory(UUID.randomUUID(), snapshot), empty(36))));
        assertFalse(GhostMobHarnessControl.carrierAdmitted(GameType.CREATIVE, fixture(account, park, occupied)));
    }
}
