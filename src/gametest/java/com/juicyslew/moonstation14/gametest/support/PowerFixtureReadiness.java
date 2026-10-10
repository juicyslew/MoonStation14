package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Bounded GameTest polling for loaded power fixtures; never grants or fabricates power state. */
public final class PowerFixtureReadiness {
    private PowerFixtureReadiness() { }

    public static boolean topologyReadyWithKnownPorts(ServerLevel level, CableFaceNode... ports) {
        if (level == null || ports == null || !PowerGraphService.topologyReady(level)) return false;
        for (CableFaceNode port : ports) {
            if (port == null || PowerGraphService.state(level, port).knowledge() != LoadedPowerGraph.Knowledge.KNOWN)
                return false;
        }
        return true;
    }

    /** Polls with a fixed server-test tick deadline while retaining caller-owned domain assertions. */
    public static void await(GameTestHelper helper, ServerLevel level, long maxHelperTicks,
                             BooleanSupplier ready, Supplier<String> diagnostic, Runnable onReady) {
        Objects.requireNonNull(helper);
        Objects.requireNonNull(level);
        Objects.requireNonNull(ready);
        Objects.requireNonNull(diagnostic);
        Objects.requireNonNull(onReady);
        if (maxHelperTicks < 1) throw new IllegalArgumentException("readiness deadline must be positive");
        poll(helper, level, helper.getTick(), level.getGameTime(), maxHelperTicks, ready, diagnostic, onReady);
    }

    private static void poll(GameTestHelper helper, ServerLevel level, long startHelperTick, long startLevelGameTime,
                             long maxHelperTicks,
                             BooleanSupplier ready, Supplier<String> diagnostic, Runnable onReady) {
        if (ready.getAsBoolean()) {
            onReady.run();
            return;
        }
        long elapsed = helper.getTick() - startHelperTick;
        long elapsedLevelTicks = level.getGameTime() - startLevelGameTime;
        if (elapsed >= maxHelperTicks || elapsedLevelTicks >= maxHelperTicks)
            throw new GameTestAssertException("loaded power fixture did not become authoritative before bounded deadline; elapsedHelperTicks="
                    + elapsed + "/" + maxHelperTicks + " elapsedLevelTicks=" + elapsedLevelTicks + "/" + maxHelperTicks
                    + " levelGameTime=" + level.getGameTime() + " state=" + diagnostic.get());
        BoundedGameTestWait.safeDelay(helper, 1, () -> poll(helper, level, startHelperTick, startLevelGameTime,
                maxHelperTicks, ready, diagnostic, onReady));
    }
}
