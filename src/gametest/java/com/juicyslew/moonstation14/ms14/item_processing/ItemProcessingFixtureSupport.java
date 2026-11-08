package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.gametest.framework.GameTestHelper;

/** Shared physical-source fixture settling for item-processing GameTests. */
final class ItemProcessingFixtureSupport {
    private ItemProcessingFixtureSupport() { }

    static void awaitSettledSource(GameTestHelper helper, ItemProcessingFixtureCleanup cleanup,
            LooseItemEntity source, Runnable ready) {
        awaitSettledSource(helper, cleanup, source, 0, null, 0, ready);
    }

    private static void awaitSettledSource(GameTestHelper helper, ItemProcessingFixtureCleanup cleanup,
            LooseItemEntity source, int waited, net.minecraft.world.phys.Vec3 priorPosition, int stableTicks, Runnable ready) {
        if (!cleanup.isOpen()) return;
        if (source.isRemoved()) {
            cleanup.finish(new AssertionError("native loose-item source removed while settling; waited=" + waited));
            return;
        }
        var position = source.position();
        var velocity = source.getDeltaMovement();
        boolean stationary = velocity.lengthSqr() <= 1.0e-6
                && (priorPosition == null || position.distanceToSqr(priorPosition) <= 1.0e-8);
        int settled = stationary ? stableTicks + 1 : 0;
        if (settled >= 2) {
            try { ready.run(); } catch (Throwable failure) { cleanup.finish(failure); }
            return;
        }
        if (waited >= 40) {
            cleanup.finish(new AssertionError("native loose-item source did not settle within 40 ticks; position="
                    + position + ", velocity=" + velocity + ", stableTicks=" + settled));
            return;
        }
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1,
                () -> awaitSettledSource(helper, cleanup, source, waited + 1, position, settled, ready));
    }
}
