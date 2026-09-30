package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/** Callback policy, not synthetic connected-player authority. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleCreativeExitHandoffGameTests {
    private LifecycleCreativeExitHandoffGameTests() { }

    @GameTest(template = "empty")
    public static void successfulCreativeExitRestoresOnlyExactCleanPark(GameTestHelper helper) {
        AtomicInteger restores = new AtomicInteger();
        AtomicInteger syncs = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        var restore = (java.util.function.Supplier<CreativeCarrierTransition.Result>) () -> {
            restores.incrementAndGet();
            return CreativeCarrierTransition.Result.RESTORED;
        };
        require(LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                true, () -> true, () -> true, restore, syncs::incrementAndGet, disconnects::incrementAndGet),
                "exact parked exit restored");
        require(LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                 false, () -> true, () -> true, restore, syncs::incrementAndGet, disconnects::incrementAndGet),
                 "pinned unmarked empty carrier needs no restore");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(false, true, GameType.CREATIVE,
                true, () -> true, () -> true, restore, syncs::incrementAndGet, disconnects::incrementAndGet),
                "non-owner is ignored");
        require(restores.get() == 1 && syncs.get() == 1 && disconnects.get() == 0,
                "only owned marked exit writes and syncs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledAndFailedExitNeverRestoresIntoCarrier(GameTestHelper helper) {
        AtomicInteger restores = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        var restore = (java.util.function.Supplier<CreativeCarrierTransition.Result>) () -> {
            restores.incrementAndGet();
            return CreativeCarrierTransition.Result.DENIED;
        };
        var disconnect = (Runnable) disconnects::incrementAndGet;
        var noSync = (Runnable) () -> { throw new AssertionError("unexpected sync"); };
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, false, GameType.SPECTATOR,
                true, () -> true, () -> true, restore, noSync, disconnect), "canceled Spectator stays parked");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, false, GameType.CREATIVE,
                false, () -> false, () -> false, restore, noSync, disconnect),
                "already-restored Creative is not disconnected by a redundant mode request");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, false, GameType.CREATIVE,
                true, () -> true, () -> true, restore, noSync, disconnect), "ambiguous Creative disconnects");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                true, () -> true, () -> false, restore, noSync, disconnect), "occupied carrier disconnects without restore");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                true, () -> true, () -> true, restore, noSync, disconnect), "restore denial disconnects");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                false, () -> false, () -> false, restore, noSync, disconnect), "unmarked occupied carrier disconnects");
        require(restores.get() == 1 && disconnects.get() == 4, "no unsafe extra restore");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void conflictingProfileRetainsMarkerAndDisconnects(GameTestHelper helper) {
        AtomicInteger restores = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                true, () -> false, () -> true, () -> {
                    restores.incrementAndGet();
                    return CreativeCarrierTransition.Result.RESTORED;
                }, () -> { throw new AssertionError("must not sync"); }, disconnects::incrementAndGet),
                "conflicting pinned profile denies marked restore");
        require(restores.get() == 0 && disconnects.get() == 1, "marker was never consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ghostParkRegistrationPolicyRestoresOnceAndRejectsUnsafeCarrier(GameTestHelper helper) {
        int[] calls = new int[3];
        require(LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                true, () -> true, () -> true, () -> {
                    calls[0]++;
                    return CreativeCarrierTransition.Result.RESTORED;
                }, () -> calls[1]++, () -> calls[2]++), "registered saved claim restores");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                true, () -> false, () -> true, () -> {
                    calls[0]++;
                    return CreativeCarrierTransition.Result.RESTORED;
                }, () -> calls[1]++, () -> calls[2]++), "mismatched saved claim keeps marker");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                true, () -> true, () -> false, () -> {
                    calls[0]++;
                    return CreativeCarrierTransition.Result.RESTORED;
                }, () -> calls[1]++, () -> calls[2]++), "dirty carrier keeps marker");
        require(!LifecycleDevelopmentMode.creativeExitHandoffPolicy(true, true, GameType.CREATIVE,
                true, () -> true, () -> true, () -> {
                    calls[0]++;
                    return CreativeCarrierTransition.Result.RECOVERY_REQUIRED;
                }, () -> calls[1]++, () -> calls[2]++), "failed restore keeps marker");
        require(calls[0] == 2 && calls[1] == 1 && calls[2] == 3, "only verified clean attempts write");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
