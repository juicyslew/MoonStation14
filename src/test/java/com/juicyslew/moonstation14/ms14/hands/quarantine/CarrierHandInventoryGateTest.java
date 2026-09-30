package com.juicyslew.moonstation14.ms14.hands.quarantine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CarrierHandInventoryGateTest {
    private static final UUID ACCOUNT = UUID.randomUUID();

    private static List<ItemStack> empty(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }

    private static CarrierHandInventoryGate.Fixture fixture(boolean marker, CreativeParkedInventory park,
            List<ItemStack> main, List<ItemStack> armor, List<ItemStack> offhand, ItemStack cursor,
            List<ItemStack> crafting, ItemStack result, List<ItemStack> ender, List<ItemStack> menu) {
        return new CarrierHandInventoryGate.Fixture(ACCOUNT, marker, park, main, armor, offhand,
                cursor, crafting, result, ender, menu, 0);
    }

    private static CarrierHandInventoryGate.Fixture clean(boolean marker, CreativeParkedInventory park) {
        return fixture(marker, park, empty(36), empty(4), empty(1), ItemStack.EMPTY,
                empty(4), ItemStack.EMPTY, empty(27), empty(46));
    }

    private static CreativeParkedInventory park(UUID account) {
        return new CreativeParkedInventory(account, CreativeInventorySnapshot.capture(
                empty(36), empty(4), empty(1), ItemStack.EMPTY, 0, empty(27)));
    }

    @Test
    void cleanUnmarkedPrototypeAndMatchingParkAreEligible() {
        assertTrue(CarrierHandInventoryGate.allows(clean(false, null)));
        assertTrue(CarrierHandInventoryGate.allows(clean(true, park(ACCOUNT))));
        assertFalse(CarrierHandInventoryGate.allows((net.minecraft.server.level.ServerPlayer) null));
    }

    @Test
    void markerMustBePresentReadableAndAccountOwned() {
        assertFalse(CarrierHandInventoryGate.allows(clean(true, null)));
        assertFalse(CarrierHandInventoryGate.allows(clean(false, park(ACCOUNT))));
        assertFalse(CarrierHandInventoryGate.allows(clean(true, park(UUID.randomUUID()))));
    }

    @Test
    void everyCompartmentIsRequiredCleanWithOrWithoutMarker() {
        for (boolean marker : List.of(false, true)) {
            var park = marker ? park(ACCOUNT) : null;
            var main = empty(36); main.set(0, new ItemStack(Items.STONE));
            assertFalse(CarrierHandInventoryGate.allows(fixture(marker, park, main, empty(4), empty(1),
                    ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(46))));
            var armor = empty(4); armor.set(0, new ItemStack(Items.STONE));
            assertFalse(CarrierHandInventoryGate.allows(fixture(marker, park, empty(36), armor, empty(1),
                    ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(46))));
            var offhand = empty(1); offhand.set(0, new ItemStack(Items.STONE));
            assertFalse(CarrierHandInventoryGate.allows(fixture(marker, park, empty(36), empty(4), offhand,
                    ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(46))));
            var craft = empty(4); craft.set(0, new ItemStack(Items.STONE));
            assertFalse(CarrierHandInventoryGate.allows(fixture(marker, park, empty(36), empty(4), empty(1),
                    ItemStack.EMPTY, craft, ItemStack.EMPTY, empty(27), empty(46))));
            var ender = empty(27); ender.set(0, new ItemStack(Items.STONE));
            assertFalse(CarrierHandInventoryGate.allows(fixture(marker, park, empty(36), empty(4), empty(1),
                    ItemStack.EMPTY, empty(4), ItemStack.EMPTY, ender, empty(46))));
            var menu = empty(46); menu.set(9, new ItemStack(Items.STONE));
            assertFalse(CarrierHandInventoryGate.allows(fixture(marker, park, empty(36), empty(4), empty(1),
                    ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), menu)));
            assertFalse(CarrierHandInventoryGate.allows(fixture(marker, park, empty(36), empty(4), empty(1),
                    new ItemStack(Items.STONE), empty(4), ItemStack.EMPTY, empty(27), empty(46))));
            assertFalse(CarrierHandInventoryGate.allows(fixture(marker, park, empty(36), empty(4), empty(1),
                    ItemStack.EMPTY, empty(4), new ItemStack(Items.STONE), empty(27), empty(46))));
        }
    }

    @Test
    void malformedSizesNullAndNoncanonicalEmptyAreDenied() {
        assertFalse(CarrierHandInventoryGate.allows((CarrierHandInventoryGate.Fixture) null));
        var clean = clean(false, null);
        assertFalse(CarrierHandInventoryGate.allows(fixture(false, null, empty(35), empty(4), empty(1),
                ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(46))));
        assertFalse(CarrierHandInventoryGate.allows(fixture(false, null, empty(36), empty(4), empty(1),
                ItemStack.EMPTY, empty(3), ItemStack.EMPTY, empty(27), empty(46))));
        assertFalse(CarrierHandInventoryGate.allows(fixture(false, null, empty(36), empty(4), empty(1),
                ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(28), empty(46))));
        assertFalse(CarrierHandInventoryGate.allows(fixture(false, null, empty(36), empty(4), empty(1),
                ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(45))));
        assertFalse(CarrierHandInventoryGate.allows(new CarrierHandInventoryGate.Fixture(ACCOUNT, false, null,
                clean.main(), clean.armor(), clean.offhand(), clean.cursor(), clean.crafting(),
                clean.result(), clean.ender(), clean.menuSlots(), 9)));
        var main = empty(36); main.set(0, new ItemStack(Items.AIR, 0));
        assertFalse(CarrierHandInventoryGate.allows(fixture(false, null, main, empty(4), empty(1),
                ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(46))));
    }
}
