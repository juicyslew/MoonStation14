package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"tesla", "tesla-lifecycle"})
@PrefixGameTestTemplate(false)
public final class TeslaMiniLifetimeGameTests {
    private TeslaMiniLifetimeGameTests() { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=80)
    public static void registeredMiniExpiresAtAuthoredLifetimeIndependentOfMainOrFeed(GameTestHelper h) {
        var level = (net.minecraft.server.level.ServerLevel) h.getLevel();
        var mini = ModEntities.TESLA_ENERGY_BALL.get().create(level);
        check(mini != null && mini.initializeSourceProfile(ModTeslaProfiles.MINI_ID), "resolved mini profile initializes");
        Double authoredSeconds = ModTeslaProfiles.balls(level).get(ModTeslaProfiles.MINI_ID).timedDespawnSeconds();
        check(authoredSeconds != null, "resolved mini profile authors a timed lifetime");
        long lifetimeTicks = (long) Math.ceil(authoredSeconds * 20);
        check(lifetimeTicks == 2400, "authored mini lifetime resolves to expected ticks: " + lifetimeTicks);

        mini.moveTo(h.absolutePos(new net.minecraft.core.BlockPos(2, 4, 2)), 0, 0);
        var saved = new net.minecraft.nbt.CompoundTag();
        mini.saveWithoutId(saved);
        saved.putLong("AgeTicks", lifetimeTicks - 2);
        mini.load(saved);
        check(level.addFreshEntity(mini), "registered mini added without a main or feeder");
        h.runAfterDelay(1, () -> {
            check(mini.isAlive(), "mini remains alive at lifetimeTicks - 1");
            h.runAfterDelay(1, () -> {
                check(mini.isRemoved(), "mini expires at authored lifetimeTicks");
                h.succeed();
            });
        });
    }
}
