package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the persisted-clock adapter through the real BE updatePower/serverTick path. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisposalUnitClockGameTests {
    private DisposalUnitClockGameTests() { }

    @GameTest(batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void futurePersistedClockRebasesBeforePowerCallback(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(pos, DisposalUnitBlockEntityBlock(), 3);
        var original = (DisposalUnitBlockEntity) level.getBlockEntity(pos);
        require(original != null, "unit installed");
        original.setGameTestPowerOverride(true);
        original.engage();

        CompoundTag saved = new CompoundTag();
        saved.putLong("LastTick", level.getGameTime());
        long futureNow = Math.addExact(level.getGameTime(), 100);
        saved.putLong("TimingNow", futureNow);
        saved.putLong("TimingPressure", futureNow + 5);
        saved.putLong("TimingVisual", futureNow);
        saved.putLong("TimingFlush", futureNow + 5);
        saved.putLong("TimingExit", futureNow - 1);
        saved.putBoolean("TimingEngaged", true);
        saved.putBoolean("TimingPowered", true);
        saved.putBoolean("TimingAnchored", true);

        var reloaded = new DisposalUnitBlockEntity(pos, level.getBlockState(pos));
        reloaded.setLevel(level);
        reloaded.loadAdditional(saved, level.registryAccess());
        level.setBlockEntity(reloaded);
        reloaded.setGameTestPowerOverride(true);

        try {
            reloaded.updatePower(true);
        } catch (Throwable failure) {
            throw new GameTestAssertException("updatePower callback must admit persisted future clock: " + failure);
        }
        var timing = reloaded.timingSnapshot();
        require(timing.logicalTick() == level.getGameTime(), "callback rebases logical clock to current world tick");
        require(timing.pressureDeadlineTick() - timing.logicalTick() == 5, "five recharge ticks remain after rebase");
        require(timing.nextFlushTick() != null && timing.nextFlushTick() - timing.logicalTick() == 5,
                "manual flush deadline remains five ticks away after rebase");
        require(timing.engaged(), "manual engagement survives serialized restore");
        require(timing.lastExitAttemptTick() != null && timing.lastExitAttemptTick() == level.getGameTime() - 1,
                "active exit cooldown keeps its elapsed age across origin rebase");
        require(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalUnitTimingKernel.restore(
                reloaded.prototype(), timing).snapshot().equals(timing),
                "rebased negative past-origin cooldown timestamp survives a subsequent timing restore");

        long worldTick = level.getGameTime();
        DisposalUnitBlockEntity.serverTick((net.minecraft.server.level.ServerLevel) level, pos,
                level.getBlockState(pos), reloaded);
        var afterOrdinaryTick = reloaded.timingSnapshot();
        require(afterOrdinaryTick.logicalTick() == worldTick,
                "ordinary serverTick in the same world tick is idempotent after power callback");
        require(afterOrdinaryTick.pressureDeadlineTick() - afterOrdinaryTick.logicalTick() == 5
                        && afterOrdinaryTick.nextFlushTick() != null
                        && afterOrdinaryTick.nextFlushTick() - afterOrdinaryTick.logicalTick() == 5,
                "same-tick ordinary serverTick does not consume recharge or manual-flush time");
        require(afterOrdinaryTick.lastExitAttemptTick().equals(timing.lastExitAttemptTick()),
                "same-tick ordinary serverTick preserves exit cooldown age");
        helper.succeed();
    }

    private static net.minecraft.world.level.block.state.BlockState DisposalUnitBlockEntityBlock() {
        return com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
