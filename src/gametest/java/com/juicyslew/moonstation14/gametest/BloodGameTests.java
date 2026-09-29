package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.blood.BloodSystem;
import com.juicyslew.moonstation14.ms14.blood.BloodComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import com.mojang.serialization.JsonOps;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BloodGameTests {
    private BloodGameTests() { }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void enrollsOnceIntoAuthoritativeBloodstream(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            // Model an old scalar-only save, not the already-enrolled join-time pig.
            pig.removeData(ModDataAttachments.BLOODSTREAM.get());
            pig.setData(ModDataAttachments.BLOOD.get(), new BloodComponent(2, false).toAttachment());
            if (!BloodSystem.reconcile(pig) || !pig.hasData(ModDataAttachments.BLOODSTREAM.get()))
                throw new GameTestAssertException("configured pig must initialize bloodstream");
            var before = MS14Provider.getDetached(pig, MS14Bridges.BLOODSTREAM).toComponent();
            BloodSystem.reconcile(pig);
            var after = MS14Provider.getDetached(pig, MS14Bridges.BLOODSTREAM).toComponent();
            if (!before.equals(after) || !before.contents().equals(java.util.Map.of(ModReagents.createKey("blood"), 150f)))
                throw new GameTestAssertException("reconciliation must not duplicate or refill reference mixture");
            if (MS14Provider.getDetached(pig, MS14Bridges.BLOOD).bleedRate() != 0)
                throw new GameTestAssertException("legacy scalar bleed must be discarded at migration");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyPigSulfurBloodFailsClosedWithoutMutation(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            var sulfurBlood = ModReagents.createKey("sulfurblood");
            var oldFill = new ReagentAttachment(java.util.Map.of(sulfurBlood, 3f));
            pig.setData(ModDataAttachments.BLOODSTREAM.get(), oldFill);
            for (int retry = 0; retry < 2; retry++) {
                if (BloodSystem.reconcile(pig) || BloodSystem.state(pig).isPresent()
                        || BloodSystem.applyEffectDelta(pig, 1f, 1f, false)
                            != BloodSystem.EffectAdjustmentResult.SKIPPED_UNSUPPORTED
                        || !pig.getData(ModDataAttachments.BLOODSTREAM.get()).equals(oldFill))
                    throw new GameTestAssertException("saved sulfur pig must stay untouched and inert");
            }
            pig.removeData(ModDataAttachments.BLOODSTREAM.get());
            pig.removeData(ModDataAttachments.BLOOD.get());
            pig.setData(ModDataAttachments.REAGENT.get(), oldFill);
            if (BloodSystem.reconcile(pig) || com.juicyslew.moonstation14.ms14.blood.BloodstreamStorage.reconcile(pig)
                    || !pig.getData(ModDataAttachments.REAGENT.get()).equals(oldFill)
                    || pig.hasData(ModDataAttachments.BLOODSTREAM.get()))
                throw new GameTestAssertException("legacy sulfur store must not migrate or refill");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bleedTickRemovesActualSolution(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            if (!BloodSystem.reconcile(pig))
                throw new GameTestAssertException("bloodstream should initialize");
            float adjustment = 1f - (float) MS14Provider.getDetached(pig, MS14Bridges.BLOOD).bleedRate();
            if (BloodSystem.applyEffectDelta(pig, adjustment, 1f, true)
                    != BloodSystem.EffectAdjustmentResult.APPLIED)
                throw new GameTestAssertException("bleed state should initialize");
            if (pig.hasData(ModDataAttachments.REAGENT.get()))
                throw new GameTestAssertException("living bloodstream must not also attach generic REAGENT storage");
            long initial = MS14Provider.getDetached(pig, MS14Bridges.BLOODSTREAM).totalUnits();
            for (long time = 0; time < 100; time++) {
                if (BloodSystem.tickIfDue(pig, time)) {
                    long after = MS14Provider.getDetached(pig, MS14Bridges.BLOODSTREAM).totalUnits();
                    if (initial - after != 100L)
                        throw new GameTestAssertException("bleed tick must remove one actual solution unit");
                    helper.succeed();
                    return;
                }
            }
            throw new GameTestAssertException("no configured blood tick occurred");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void emptyInitializedBloodstreamSurvivesCodecReloadWithoutRefill(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            if (!BloodSystem.reconcile(pig)) throw new GameTestAssertException("pig must enroll before depletion");
            var empty = new ReagentAttachment();
            pig.setData(ModDataAttachments.BLOODSTREAM.get(), empty);
            var encoded = ReagentAttachment.CODEC.encodeStart(JsonOps.INSTANCE, empty).getOrThrow();
            var reloaded = ReagentAttachment.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            if (!reloaded.isEmpty()) throw new GameTestAssertException("empty bloodstream codec roundtrip must stay empty");

            pig.setData(ModDataAttachments.BLOODSTREAM.get(), reloaded);
            if (!pig.hasData(ModDataAttachments.BLOODSTREAM.get()) || !BloodSystem.reconcile(pig))
                throw new GameTestAssertException("empty initialized bloodstream must remain valid");
            if (!MS14Provider.getDetached(pig, MS14Bridges.BLOODSTREAM).isEmpty())
                throw new GameTestAssertException("reconcile must not refill an initialized depleted stream");
            helper.succeed();
        });
    }
}
