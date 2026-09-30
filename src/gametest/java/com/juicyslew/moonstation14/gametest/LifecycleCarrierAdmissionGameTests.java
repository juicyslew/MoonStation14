package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Value-level admission cases shared by first enrollment and living-body reconnect.
 * A GameTest fake is checked only for denial, never used to claim connected authority.
 */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleCarrierAdmissionGameTests {
    private LifecycleCarrierAdmissionGameTests() { }

    @GameTest(template = "empty")
    public static void emptyFirstJoinAndReconnectRemainEligible(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        require(CarrierHandInventoryGate.allows(fixture(account, false, null, empty(36))),
                "empty unmarked first join must remain eligible");
        var park = new CreativeParkedInventory(account, CreativeInventorySnapshot.capture(
                empty(36), empty(4), empty(1), ItemStack.EMPTY, 0, empty(27)));
        require(CarrierHandInventoryGate.allows(fixture(account, true, park, empty(36))),
                "empty reconnect with existing account-owned park must remain eligible");
        require(CarrierHandInventoryGate.allows(fixture(account, false, null, empty(36))),
                "empty unmarked reconnect must remain eligible");
        require(!CarrierHandInventoryGate.allows(FakePlayerFactory.getMinecraft(helper.getLevel())),
                "fake player must never qualify as connected authority");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dirtyAndMismatchedCarrierDeniedWithoutChangingInventory(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        var park = new CreativeParkedInventory(account, CreativeInventorySnapshot.capture(
                empty(36), empty(4), empty(1), ItemStack.EMPTY, 0, empty(27)));
        var main = empty(36);
        ItemStack original = new ItemStack(Items.DIAMOND, 7);
        main.set(0, original);
        require(!CarrierHandInventoryGate.allows(fixture(account, false, null, main)),
                "unmarked nonempty first join/reconnect must be refused");
        require(!CarrierHandInventoryGate.allows(fixture(account, true, park, main)),
                "dirty marked reconnect must be refused");
        require(main.get(0) == original && original.getCount() == 7,
                "denial must leave inventory item and count untouched");
        require(!CarrierHandInventoryGate.allows(fixture(account, true,
                new CreativeParkedInventory(UUID.randomUUID(), park.snapshot()), empty(36))),
                "park belonging to another account must be refused");
        require(!CarrierHandInventoryGate.allows(fixture(account, true, null, empty(36))),
                "unreadable marker must be refused");
        helper.succeed();
    }

    private static CarrierHandInventoryGate.Fixture fixture(UUID account, boolean marker,
                                                             CreativeParkedInventory park, List<ItemStack> main) {
        return new CarrierHandInventoryGate.Fixture(account, marker, park, main, empty(4), empty(1),
                ItemStack.EMPTY, empty(4), ItemStack.EMPTY, empty(27), empty(46), 0);
    }

    private static List<ItemStack> empty(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
