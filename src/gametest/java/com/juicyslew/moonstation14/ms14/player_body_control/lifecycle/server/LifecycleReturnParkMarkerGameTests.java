package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierSnapshotProbe;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Value fixtures: callbacks do not manufacture connected-player authority. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleReturnParkMarkerGameTests {
    private LifecycleReturnParkMarkerGameTests() { }

    private static CreativeParkedInventory park(UUID account) {
        var main = new java.util.ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
        main.set(0, new ItemStack(Items.DIAMOND));
        return new CreativeParkedInventory(account, CreativeInventorySnapshot.capture(main,
                Collections.nCopies(4, ItemStack.EMPTY), List.of(ItemStack.EMPTY), ItemStack.EMPTY, 0, List.of()));
    }

    @GameTest(template = "empty")
    public static void missingMarkerAfterSuccessfulSwitchCannotRollback(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        CreativeParkedInventory expected = park(account);
        AtomicInteger restores = new AtomicInteger(), disconnects = new AtomicInteger();
        require(LifecycleDevelopmentMode.returningBodySpectatorResultPolicy(true, GameType.CREATIVE, false,
                () -> true, () -> new CreativeCarrierTransition.Attempt(CreativeCarrierTransition.Result.PARKED,
                        false, CreativeCarrierSnapshotProbe.Reason.READY), () -> true, () -> GameType.SPECTATOR,
                () -> { throw new AssertionError("no rollback on successful switch"); }, () -> { },
                disconnects::incrementAndGet, () -> { }, () -> true) == LifecycleDevelopmentMode.ReturnSwitch.COMMITTED,
                "successful switch committed");
        require(!LifecycleExistingBodyReconnect.returnParkIntact(null, expected, account, true),
                "missing marker is not the pinned snapshot");
        require(!LifecycleDevelopmentMode.restoreParkPolicy(GameType.CREATIVE, expected, null, account,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                () -> true, () -> { throw new AssertionError("no sync"); }, disconnects::incrementAndGet),
                "rollback must fail closed without marker");
        require(restores.get() == 0 && disconnects.get() == 1, "no synthetic park or second owner");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactMarkerRestoresOnceAndReplacementFails(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        CreativeParkedInventory expected = park(account), replacement = park(account);
        AtomicInteger restores = new AtomicInteger(), syncs = new AtomicInteger(), disconnects = new AtomicInteger();
        require(!LifecycleExistingBodyReconnect.returnParkIntact(replacement, expected, account, true),
                "same account cannot substitute another marker");
        require(LifecycleExistingBodyReconnect.returnParkIntact(expected, expected, account, true),
                "exact clean marker remains pinned");
        require(LifecycleDevelopmentMode.restoreParkPolicy(GameType.CREATIVE, expected, expected, account,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                () -> true, syncs::incrementAndGet, disconnects::incrementAndGet), "exact snapshot restored");
        require(!LifecycleDevelopmentMode.restoreParkPolicy(GameType.CREATIVE, expected, replacement, account,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                () -> true, syncs::incrementAndGet, disconnects::incrementAndGet), "replacement rejected");
        require(restores.get() == 1 && syncs.get() == 1 && disconnects.get() == 1, "only exact marker consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledSwitchAlreadyRestoredSkipsSecondRestore(GameTestHelper helper) {
        AtomicInteger restores = new AtomicInteger(), disconnects = new AtomicInteger();
        require(LifecycleDevelopmentMode.returningBodySpectatorResultPolicy(true, GameType.CREATIVE, false,
                () -> true, () -> new CreativeCarrierTransition.Attempt(CreativeCarrierTransition.Result.PARKED,
                        false, CreativeCarrierSnapshotProbe.Reason.READY), () -> false, () -> GameType.CREATIVE,
                () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                () -> { }, disconnects::incrementAndGet, () -> { }, () -> true)
                == LifecycleDevelopmentMode.ReturnSwitch.ALREADY_RESTORED, "explicit switch rollback recognized");
        require(restores.get() == 1 && disconnects.get() == 0, "caller has no second restore to perform");
        require(LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                false, () -> true, () -> true, () -> { throw new AssertionError("unmarked ghost must not restore"); },
                () -> { }, disconnects::incrementAndGet), "original unmarked empty ghost exit remains valid");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
