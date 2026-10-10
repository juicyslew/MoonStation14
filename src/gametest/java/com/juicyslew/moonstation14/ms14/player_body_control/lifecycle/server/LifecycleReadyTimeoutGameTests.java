package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the exact transition dispatcher used by the server timeout branch; persistence is covered by JUnit. */
@GameTestTags({"lifecycle", "lifecycle-ready-timeout"})
@PrefixGameTestTemplate(false)
public final class LifecycleReadyTimeoutGameTests {
    private LifecycleReadyTimeoutGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14")
    public static void exactTimeoutParksBeforeDisconnectAndStaleOwnerFailsClosed(GameTestHelper helper) {
        AtomicInteger sequence = new AtomicInteger();
        boolean handled = LifecycleCharacterSessionControl.timeoutCleanupDecision(true, () -> {
            require(sequence.compareAndSet(0, 1), "durable park is first");
            return true;
        }, () -> { throw new AssertionError("exact current owner does not take recovery branch"); }, parked -> {
            require(parked && sequence.compareAndSet(1, 2), "disconnect follows successful durable park");
        });
        require(handled && sequence.get() == 2, "timeout transition completed");

        AtomicInteger staleCleanup = new AtomicInteger();
        AtomicInteger recovery = new AtomicInteger();
        require(!LifecycleCharacterSessionControl.timeoutCleanupDecision(false,
                () -> { staleCleanup.incrementAndGet(); return true; }, recovery::incrementAndGet,
                ignored -> { throw new AssertionError("stale owner must not disconnect current player"); }),
                "stale owner rejected");
        require(staleCleanup.get() == 0 && recovery.get() == 1, "stale owner cannot park new epoch");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
