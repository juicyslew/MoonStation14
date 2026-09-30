package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Callback policy tests; a GameTest fake cannot exercise connected-player parking. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleDeadClaimCarrierGameTests {
    private LifecycleDeadClaimCarrierGameTests() { }

    @GameTest(template = "empty")
    public static void creativePopulatedAndEmptyParkBeforeSpectator(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        for (boolean populated : new boolean[] {true, false}) {
            var main = empty(36);
            if (populated) main.set(0, new ItemStack(Items.DIAMOND, 7));
            var original = main.get(0);
            AtomicInteger parks = new AtomicInteger();
            var result = LifecycleFirstJoinHandler.admitDeadClaimCarrier(GameType.CREATIVE, false, () -> {
                parks.incrementAndGet();
                main.set(0, ItemStack.EMPTY); // fixture models the transition, not production player authority
                return CreativeCarrierTransition.Result.PARKED;
            }, () -> CarrierHandInventoryGate.allows(fixture(account, false, null, main)));
            require(result == LifecycleFirstJoinHandler.DeadClaimAdmission.NEWLY_PARKED && parks.get() == 1,
                    "both Creative inventories must park before admission");
            require(!populated || original.getCount() == 7, "snapshot source must not be mutated");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dirtySurvivalAndMismatchedMarkerCannotSwitch(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        var main = empty(36);
        ItemStack original = new ItemStack(Items.DIAMOND, 7);
        main.set(0, original);
        var noPark = (java.util.function.Supplier<CreativeCarrierTransition.Result>) () -> {
            throw new AssertionError("must not park");
        };
        require(LifecycleFirstJoinHandler.admitDeadClaimCarrier(GameType.SURVIVAL, false, noPark,
                () -> CarrierHandInventoryGate.allows(fixture(account, false, null, main)))
                == LifecycleFirstJoinHandler.DeadClaimAdmission.DENIED, "nonempty Survival denied");
        var foreign = new CreativeParkedInventory(UUID.randomUUID(), CreativeInventorySnapshot.capture(
                empty(36), empty(4), empty(1), ItemStack.EMPTY, 0, empty(27)));
        require(LifecycleFirstJoinHandler.admitDeadClaimCarrier(GameType.CREATIVE, true, noPark,
                () -> CarrierHandInventoryGate.allows(fixture(account, true, foreign, empty(36))))
                == LifecycleFirstJoinHandler.DeadClaimAdmission.DENIED, "foreign marker denied without parking");
        require(main.get(0) == original && original.getCount() == 7, "denial must not clear or drop items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledSwitchRestoresOnlyExactCleanNewPark(GameTestHelper helper) {
        AtomicInteger restores = new AtomicInteger();
        AtomicInteger syncs = new AtomicInteger();
        var restore = (java.util.function.Supplier<CreativeCarrierTransition.Result>) () -> {
            restores.incrementAndGet();
            return CreativeCarrierTransition.Result.RESTORED;
        };
        var sync = (Runnable) syncs::incrementAndGet;
        require(!LifecycleFirstJoinHandler.switchDeadClaimCarrier(true, () -> false, () -> GameType.CREATIVE,
                () -> true, () -> true, restore, sync), "canceled switch cannot prepare ghost");
        require(!LifecycleFirstJoinHandler.switchDeadClaimCarrier(true, () -> { throw new IllegalStateException(); },
                () -> GameType.CREATIVE, () -> true, () -> true, restore, sync), "throwing switch restores if safe");
        require(!LifecycleFirstJoinHandler.switchDeadClaimCarrier(true, () -> false, () -> GameType.CREATIVE,
                () -> true, () -> false, restore, sync), "ambiguous claim retains park");
        require(!LifecycleFirstJoinHandler.switchDeadClaimCarrier(true, () -> false, () -> GameType.CREATIVE,
                () -> false, () -> true, restore, sync), "dirty carrier retains park");
        require(!LifecycleFirstJoinHandler.switchDeadClaimCarrier(true, () -> false, () -> GameType.SPECTATOR,
                () -> true, () -> true, restore, sync), "Spectator never restores Creative items");
        require(!LifecycleFirstJoinHandler.switchDeadClaimCarrier(false, () -> false, () -> GameType.CREATIVE,
                () -> true, () -> true, restore, sync), "preexisting park never restored by login");
        require(restores.get() == 2 && syncs.get() == 2, "only exact clean newly parked Creative restores and syncs");
        require(LifecycleFirstJoinHandler.switchDeadClaimCarrier(false, () -> true, () -> GameType.SPECTATOR,
                () -> true, () -> true, restore, sync), "clean Spectator proceeds");
        require(!LifecycleFirstJoinHandler.switchDeadClaimCarrier(false, () -> true, () -> GameType.SPECTATOR,
                () -> false, () -> true, restore, sync), "dirty post-switch carrier cannot prepare ghost");
        helper.succeed();
    }

    private static CarrierHandInventoryGate.Fixture fixture(UUID account, boolean marker,
                                                              CreativeParkedInventory park, java.util.List<ItemStack> main) {
        return new CarrierHandInventoryGate.Fixture(account, marker, park, main, empty(4), empty(1),
                ItemStack.EMPTY, empty(InventoryMenu.CRAFT_SLOT_COUNT), ItemStack.EMPTY, empty(27), empty(46), 0);
    }

    private static java.util.List<ItemStack> empty(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
