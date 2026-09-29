package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.blood.BloodSystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BloodSpillGameTests {
    private BloodSpillGameTests() { }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static long blood(Pig pig) {
        return MS14Provider.getDetached(pig, MS14Bridges.BLOODSTREAM).totalUnits();
    }

    private static long temporary(Pig pig) {
        return pig.getData(ModDataAttachments.PENDING_BLOOD_SPILL.get()).totalUnits();
    }

    private static String spillState(Pig pig) {
        var policy = BloodSystem.resolvePolicy(pig).orElseThrow();
        var puddle = pig.level().getBlockEntity(pig.blockPosition());
        return " [bloodstreamCents=" + blood(pig) + ", temporaryCents=" + temporary(pig)
                + ", bleedRate=" + MS14Provider.getDetached(pig, MS14Bridges.BLOOD).bleedRate()
                + ", puddleCents=" + (puddle instanceof PuddleBlockEntity
                        ? MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).totalUnits() : "none")
                + ", policy={reference=" + policy.referenceSolution()
                + ", refresh=" + policy.bloodRefreshPerUpdate()
                + ", decay=" + policy.bleedDecayPerUpdate()
                + ", interval=" + policy.updateIntervalSeconds()
                + ", threshold=" + policy.bleedPuddleThreshold() + "}]";
    }

    private static void tick(Pig pig, long from) {
        for (long time = from; time < from + 60; time++) if (BloodSystem.tickIfDue(pig, time)) return;
        throw new GameTestAssertException("no prototype-scheduled blood update");
    }

    private static void bleed(Pig pig, float amount, long from) {
        var state = MS14Provider.getDetached(pig, MS14Bridges.BLOOD);
        check(state.initialized() && BloodSystem.resolvePolicy(pig).isPresent(), "pig must have initialized blood policy");
        state.set(amount, true);
        MS14Provider.update(pig, MS14Bridges.BLOOD, state);
        tick(pig, from);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void deadPigDrainsWithoutRefreshOrBloodlossAndSpillsSavedBatchOnce(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            check(BloodSystem.reconcile(pig), "living pig must initialize before death");
            var bloodKey = ModReagents.createKey("blood");
            MS14Provider.update(pig, MS14Bridges.BLOODSTREAM,
                    new ReagentAttachment(Map.of(bloodKey, 3f)));
            var state = MS14Provider.getDetached(pig, MS14Bridges.BLOOD);
            state.set(1.25, true);
            MS14Provider.update(pig, MS14Bridges.BLOOD, state);
            pig.setData(ModDataAttachments.PENDING_BLOOD_SPILL.get(),
                    new ReagentAttachment(Map.of(bloodKey, 1.25f)));
            pig.setHealth(0f);
            check(!pig.isAlive(), "fixture must be dead");
            var damageBefore = pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
            tick(pig, 0);
            check(blood(pig) == 175 && temporary(pig) == 0,
                    "dead bleed removes 125 cents without 100-cent refresh and clears saved batch");
            var puddle = helper.getLevel().getBlockEntity(pig.blockPosition());
            check(puddle instanceof PuddleBlockEntity
                    && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).snapshotUnits()
                            .equals(Map.of(bloodKey, 250L)),
                    "saved overthreshold batch and dead bleed get one combined spill");
            check(java.util.Objects.equals(damageBefore, pig.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()))
                    && pig.getHealth() == 0f, "dead update must not apply bloodloss damage or healing");
            check(!pig.hasData(ModDataAttachments.REAGENT.get()), "dead bloodstream must not create generic reagent");
            // Bleed decay still runs; the next update has no saved batch to spill again.
            long decayedDrain = ReagentUnits.fromDouble(
                    MS14Provider.getDetached(pig, MS14Bridges.BLOOD).bleedRate());
            tick(pig, 60);
            check(blood(pig) == 175 - decayedDrain && temporary(pig) == decayedDrain
                    && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).totalUnits() == 250,
                    "second dead update drains decayed rate but cannot retry previous batch" + spillState(pig)
                            + ", expectedDrain=" + decayedDrain);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void uninitializedDeadPigCannotEnrollOrRefresh(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            pig.removeData(ModDataAttachments.BLOOD.get());
            pig.removeData(ModDataAttachments.BLOODSTREAM.get());
            pig.removeData(ModDataAttachments.CHARACTER_IDENTITY.get());
            pig.setHealth(0f);
            check(!BloodSystem.tickIfDue(pig, Math.floorMod(-pig.getId(), 60))
                    && !pig.hasData(ModDataAttachments.BLOOD.get())
                    && !pig.hasData(ModDataAttachments.BLOODSTREAM.get())
                    && !pig.hasData(ModDataAttachments.CHARACTER_IDENTITY.get())
                    && !pig.hasData(ModDataAttachments.REAGENT.get()),
                    "unbound dead actor must not enroll or create blood/reagent storage");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void belowAndEqualThresholdBatchThenStrictlyGreaterCreatesPuddle(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            check(BloodSystem.reconcile(pig), "pig must have configured blood");
            long initial = blood(pig);
            // 0.5 + 0.5 = threshold exactly: no placement, even on the next due update.
            bleed(pig, .5f, 0);
            check(temporary(pig) == 50 && blood(pig) == initial - 50,
                    "subthreshold mixture retained; initialCents=" + initial + spillState(pig));
            bleed(pig, .5f, 60);
            check(temporary(pig) == 100 && blood(pig) == initial - 50,
                    "equality retains both removed batches while refresh restores the first 50 cents; initialCents="
                            + initial + spillState(pig));
            check(!helper.getLevel().getBlockState(pig.blockPosition()).is(ModBlocks.PUDDLE.get()),
                    "equality must not create a puddle");
            // The temporary solution is persisted and copied without a separate queue.
            var saved = ReagentAttachment.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                    pig.getData(ModDataAttachments.PENDING_BLOOD_SPILL.get())).getOrThrow();
            pig.setData(ModDataAttachments.PENDING_BLOOD_SPILL.get(),
                    ReagentAttachment.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow());
            Pig clone = helper.spawn(EntityType.PIG, new BlockPos(3, 1, 2));
            check(BloodSystem.copyToClone(pig, clone) && temporary(clone) == 100,
                    "subthreshold batch must survive codec and clone");
            bleed(pig, .25f, 120);
            check(temporary(pig) == 0 && blood(pig) == initial - 25,
                    "trigger clears batch after removal and refresh restores the prior 50 cents; initialCents="
                            + initial + spillState(pig));
            var puddle = helper.getLevel().getBlockEntity(pig.blockPosition());
            check(puddle instanceof PuddleBlockEntity && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT)
                    .snapshotUnits().equals(Map.of(ModReagents.createKey("blood"), 125L)),
                    "new puddle must receive exact accumulated mixture");
            check(!pig.hasData(ModDataAttachments.REAGENT.get()), "blood must not use generic entity reagent");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void existingNearlyFullPuddleAdmitsOnlyCapacityThenDiscardsRemainder(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            check(BloodSystem.reconcile(pig), "pig enrollment");
            BlockPos feet = pig.blockPosition();
            helper.getLevel().setBlock(feet, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
            var puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(feet);
            var water = ModReagents.createKey("water");
            MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(water, 999.75f)));
            long before = blood(pig);
            bleed(pig, 1.25f, 0);
            check(blood(pig) == before - 125 && temporary(pig) == 0, "spill must clear without backpressure");
            check(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).snapshotUnits().equals(
                    Map.of(water, 99_975L, ModReagents.createKey("blood"), 25L)),
                    "only 25 cents fit in 1000-unit puddle; 100 discarded");
            // A second bleed must still drain the stream despite the full destination.
            bleed(pig, 1.25f, 60);
            check(blood(pig) == before - 150 && temporary(pig) == 0
                    && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).totalUnits() == 100_000L,
                    "full puddle discards next batch and does not throttle bleeding; beforeCents="
                            + before + spillState(pig));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blockedPlacementAndSavedOverthresholdBatchGetOneLossyAttempt(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            check(BloodSystem.reconcile(pig), "pig enrollment");
            // getOnPos is below the entity; neither that block nor its above cell is a puddle.
            helper.getLevel().setBlock(pig.blockPosition(), Blocks.STONE.defaultBlockState(), 3);
            pig.setData(ModDataAttachments.PENDING_BLOOD_SPILL.get(),
                     new ReagentAttachment(Map.of(ModReagents.createKey("blood"), 1.25f)));
            long before = blood(pig);
            tick(pig, 0); // valid saved overthreshold batch: migrate by one immediate attempt
            check(temporary(pig) == 0 && blood(pig) == before, "saved batch cleared, no fictitious blood removal");
            bleed(pig, 1.25f, 60);
            check(temporary(pig) == 0 && blood(pig) == before - 125,
                    "blocked placement discards batch but still drains blood");
            check(!pig.hasData(ModDataAttachments.REAGENT.get()), "no generic living reagent");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unmappedHostCannotMutateBloodOrTemporaryState(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            check(BloodSystem.resolvePolicy(zombie).isEmpty(), "zombie is unmapped");
            check(!BloodSystem.tickIfDue(zombie, 0) && !zombie.hasData(ModDataAttachments.BLOODSTREAM.get())
                    && !zombie.hasData(ModDataAttachments.PENDING_BLOOD_SPILL.get()),
                    "absent policy cannot create or mutate blood state");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mappedHumanAndPigShareThresholdAndInvalidSavedBatchFailsClosed(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setNoAi(true);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 1, 2));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            check(BloodSystem.reconcile(villager) && BloodSystem.reconcile(pig),
                    "both mapped hosts must initialize their own policy");
            check(BloodSystem.resolvePolicy(villager).orElseThrow().bleedPuddleThreshold() == 1.0
                     && BloodSystem.resolvePolicy(pig).orElseThrow().bleedPuddleThreshold() == 1.0,
                    "species thresholds are prototype-owned");
            var invalid = new ReagentAttachment(Map.of(ModReagents.createKey("not_registered_blood"), 1f));
            pig.setData(ModDataAttachments.PENDING_BLOOD_SPILL.get(), invalid);
            long before = blood(pig);
            check(!BloodSystem.tickIfDue(pig, Math.floorMod(-pig.getId(), 60))
                    && blood(pig) == before && pig.getData(ModDataAttachments.PENDING_BLOOD_SPILL.get()).equals(invalid),
                    "invalid saved temporary constituents must not remove or spill blood");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oversizedSavedBatchDoesNotSearchSecondPuddle(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            check(BloodSystem.reconcile(pig), "pig enrollment");
            BlockPos feet = pig.blockPosition();
            BlockPos north = feet.north();
            helper.getLevel().setBlock(north, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
            var neighbor = helper.getLevel().getBlockEntity(north);
            long before = blood(pig);
            pig.setData(ModDataAttachments.PENDING_BLOOD_SPILL.get(),
                     new ReagentAttachment(Map.of(ModReagents.createKey("blood"), 1_001f)));
            tick(pig, 0);
            var destination = helper.getLevel().getBlockEntity(feet);
            check(destination instanceof PuddleBlockEntity
                    && MS14Provider.getDetached(destination, MS14Bridges.REAGENT).totalUnits() == 100_000L
                    && MS14Provider.getDetached(neighbor, MS14Bridges.REAGENT).isEmpty()
                    && temporary(pig) == 0 && blood(pig) == before,
                    "one destination accepts 1000, discards excess 1, never routes to neighbor");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bleedingRemovesActualMixedConstituentsIntoOrdinaryPuddle(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            check(BloodSystem.reconcile(pig), "pig enrollment");
            var sulfur = ModReagents.createKey("blood");
            var water = ModReagents.createKey("water");
            MS14Provider.update(pig, MS14Bridges.BLOODSTREAM,
                     new ReagentAttachment(Map.of(sulfur, 3f, water, 1f)));
            bleed(pig, 1.25f, 0);
            check(MS14Provider.getDetached(pig, MS14Bridges.BLOODSTREAM).snapshotUnits().equals(
                     Map.of(sulfur, 300L, water, 75L)), "bloodstream loses exact proportional constituents");
            var puddle = helper.getLevel().getBlockEntity(pig.blockPosition());
            check(puddle instanceof PuddleBlockEntity && MS14Provider.getDetached(puddle, MS14Bridges.REAGENT)
                     .snapshotUnits().equals(Map.of(sulfur, 100L, water, 25L)) && temporary(pig) == 0,
                    "ordinary puddle receives the removed mixture rather than synthetic species-only blood");
            helper.succeed();
        });
    }
}
