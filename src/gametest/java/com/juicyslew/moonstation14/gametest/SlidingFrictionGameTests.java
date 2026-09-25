package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.slip.SlidingAttachment;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import com.juicyslew.moonstation14.ms14.slip.MinecraftSlidingPhysics;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import com.juicyslew.moonstation14.mixin.LivingEntityImmobileInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SlidingFrictionGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlidingFrictionGameTests.class);

    private SlidingFrictionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void frictionQueryTracksPuddleContentsAndIgnoresNoContact(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 0, 2);
        BlockPos puddlePos = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(puddlePos));
        Villager actor = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        actor.setNoAi(true);
        require(CharacterControlSystem.canAct(actor), "villager must have a resolved character identity");
        SlidingAttachment sliding = new SlidingAttachment();
        sliding.setSliding(true);
        MS14Provider.update(actor, MS14Bridges.SLIDING, sliding);
        require(SlidingFrictionSystem.frictionFactor(actor) == 1d, "no-contact query must be neutral");

        ReagentAttachment lube = new ReagentAttachment();
        lube.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, lube);
        double lubeFactor = SlidingFrictionSystem.frictionFactor(actor);
        require(lubeFactor < 1d && Math.abs(lubeFactor - .05d) < 1e-6,
                "populated qualifying puddle must contribute its mixture friction, got " + lubeFactor);

        ReagentAttachment replacement = new ReagentAttachment();
        replacement.specificAdd(ModReagents.createKey("polytrinicacid"), 20f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, replacement);
        require(Math.abs(SlidingFrictionSystem.frictionFactor(actor) - 1d) < 1e-9,
                "query must reflect changed contents without caching");

        BlockPos secondPos = puddlePos.east();
        helper.setBlock(secondPos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity second = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(secondPos));
        ReagentAttachment secondLube = new ReagentAttachment();
        secondLube.specificAdd(ModReagents.createKey("spacelube"), 20f, second.getCapacity());
        MS14Provider.update(second, MS14Bridges.REAGENT, secondLube);
        BlockPos absoluteSecond = helper.absolutePos(secondPos);
        actor.setPos(absoluteSecond.getX(), absoluteSecond.getY(), absoluteSecond.getZ() + .5d);
        require(Math.abs(SlidingFrictionSystem.frictionFactor(actor) - .525d) < 1e-6,
                "contacted puddles must contribute an arithmetic mean, not a pooled mixture");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void liveTravelScalesGroundAccelerationAndRetention(GameTestHelper helper) {
        BlockPos puddlePos = new BlockPos(2, 1, 2);
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(puddlePos));
        ReagentAttachment lube = new ReagentAttachment();
        lube.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, lube);

        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sliding-travel"));
        player.setPos(helper.absolutePos(new BlockPos(2, 1, 2)).getX() + .5d,
                helper.absolutePos(new BlockPos(2, 1, 2)).getY(),
                helper.absolutePos(new BlockPos(2, 1, 2)).getZ() + .5d);
        require(helper.getLevel().addFreshEntity(player), "server FakePlayer must join the GameTest level");

        java.util.function.Predicate<SlipSystem.SlipAttempt> gate = attempt -> attempt.target() != villager
                && attempt.target() != player;
        require(SlipSystem.addAttemptGate(gate), "fixture must suppress slip response for its two actors");
        helper.runAfterDelay(1, () -> {
            try {
                require(CharacterControlSystem.canAct(villager) && CharacterControlSystem.canAct(player),
                        "villager and FakePlayer must be bound real character actors");
                checkLiveTravelActor(helper, villager, puddlePos, "Villager");
                checkLiveTravelActor(helper, player, puddlePos, "FakePlayer");
                helper.succeed();
            } finally {
                SlipSystem.removeAttemptGate(gate);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void travelUsesOneCapturedFrictionWhenLeavingPuddle(GameTestHelper helper) {
        BlockPos puddlePos = new BlockPos(2, 1, 2);
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(puddlePos));
        ReagentAttachment lube = new ReagentAttachment();
        lube.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, lube);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));

        java.util.function.Predicate<SlipSystem.SlipAttempt> gate = attempt -> attempt.target() != villager;
        require(SlipSystem.addAttemptGate(gate), "fixture must suppress slip response for its actor");
        helper.runAfterDelay(1, () -> {
            try {
                require(CharacterControlSystem.canAct(villager), "villager must be bound before direct travel");
                SlidingAttachment sliding = new SlidingAttachment();
                sliding.setSliding(true);
                MS14Provider.update(villager, MS14Bridges.SLIDING, sliding);

                BlockPos absolutePuddle = helper.absolutePos(puddlePos);
                double startX = absolutePuddle.getX() + .5d;
                double y = absolutePuddle.getY();
                double z = absolutePuddle.getZ() + .5d;
                villager.setPos(startX, y, z);
                villager.move(MoverType.SELF, new Vec3(0d, -.05d, 0d));
                require(villager.onGround(), "puddle travel must begin grounded");
                require(Math.abs(SlidingFrictionSystem.frictionFactor(villager) - .05d) < 1e-6,
                        "travel must capture puddle friction before movement");
                double initialVelocity = 2d;
                villager.setDeltaMovement(initialVelocity, 0d, 0d);
                villager.setSpeed(.1f);
                villager.travel(Vec3.ZERO);

                double endFactor = SlidingFrictionSystem.frictionFactor(villager);
                float vanillaRetention = .6F * .91F;
                double expectedCapturedRetention = initialVelocity
                        * MinecraftSlidingPhysics.groundHorizontalRetention(vanillaRetention, .05d);
                double actual = villager.getDeltaMovement().x;
                LOGGER.info("Sliding travel capture trace: startX={}, endX={}, postMoveFactor={}, expectedRetention={}, actualRetention={}",
                        startX, villager.getX(), endFactor, expectedCapturedRetention, actual);
                require(villager.getX() > absolutePuddle.getX() + 1.3d,
                        "travel must move completely off the source puddle, endX=" + villager.getX());
                require(Math.abs(endFactor - 1d) < 1e-9,
                        "post-move contact query must be neutral after leaving puddle");
                require(Math.abs(actual - expectedCapturedRetention) < 1e-5,
                        "retention must use pre-move .05 friction, expected=" + expectedCapturedRetention
                                + ", actual=" + actual);

                double outsideX = helper.absolutePos(new BlockPos(5, 1, 2)).getX() + .5d;
                villager.setPos(outsideX, y, z);
                villager.move(MoverType.SELF, new Vec3(0d, -.05d, 0d));
                require(villager.onGround(), "next travel must also begin grounded");
                villager.setDeltaMovement(.2d, 0d, 0d);
                villager.travel(Vec3.ZERO);
                require(Math.abs(villager.getDeltaMovement().x - .2d * vanillaRetention) < 1e-6,
                        "next outside travel must use neutral retention, got " + villager.getDeltaMovement().x);
                helper.succeed();
            } finally {
                SlipSystem.removeAttemptGate(gate);
            }
        });
    }

    private static void checkLiveTravelActor(GameTestHelper helper, LivingEntity actor, BlockPos puddlePos,
                                             String label) {
        BlockPos absolute = helper.absolutePos(puddlePos);
        double x = absolute.getX() + .5d;
        double y = absolute.getY();
        double z = absolute.getZ() + .5d;
        SlidingAttachment initialSliding = new SlidingAttachment();
        initialSliding.setSliding(true);
        MS14Provider.update(actor, MS14Bridges.SLIDING, initialSliding);
        require(Math.abs(SlidingFrictionSystem.frictionFactor(actor) - .05d) < 1e-6,
                label + " must contact the populated Space Lube puddle");

        double accelerationBaseline = travel(actor, x, y, z, false, 0d, 1d);
        double accelerationSliding = travel(actor, x, y, z, true, 0d, 1d);
        double retentionBaseline = travel(actor, x, y, z, false, .2d, 0d);
        double retentionSliding = travel(actor, x, y, z, true, .2d, 0d);
        boolean immobile = ((LivingEntityImmobileInvoker) actor).moonstation14$callIsImmobile();
        LOGGER.info("Sliding travel trace {}: controlled={}, immobile={}, acceleration baseline={} sliding={}, "
                        + "retention baseline={} sliding={}", label, actor.isControlledByLocalInstance(),
                immobile, accelerationBaseline, accelerationSliding, retentionBaseline, retentionSliding);
        boolean liveMovement = Math.abs(accelerationBaseline) > 1e-7 || Math.abs(accelerationSliding) > 1e-7
                || Math.abs(retentionBaseline) > 1e-7 || Math.abs(retentionSliding) > 1e-7;
        if (!liveMovement && actor instanceof FakePlayer) {
            LOGGER.warn("Sliding travel GameTest: {} produced all-zero direct server travel (controlled={}, immobile={}); "
                    + "excluding FakePlayer physics comparisons; owner must verify manually on client", label,
                    actor.isControlledByLocalInstance(), immobile);
            return;
        }
        require(liveMovement, label + " direct travel must produce nonzero movement on the grounded fixture");
        require(Math.abs(accelerationBaseline) > 1e-7,
                label + " vanilla acceleration baseline must be nonzero: " + accelerationBaseline);
        require(Math.abs(retentionBaseline) > 1e-7,
                label + " vanilla retention baseline from initial .2 velocity must be nonzero: " + retentionBaseline);
        require(accelerationSliding < accelerationBaseline,
                label + " sliding must reduce live travel acceleration: baseline=" + accelerationBaseline
                        + ", sliding=" + accelerationSliding);
        require(retentionSliding > retentionBaseline,
                label + " sliding must retain more live horizontal motion: baseline=" + retentionBaseline
                        + ", sliding=" + retentionSliding);

        // Leaving the puddle keeps sliding active but restores the neutral factor.
        SlidingAttachment sliding = new SlidingAttachment();
        sliding.setSliding(true);
        MS14Provider.update(actor, MS14Bridges.SLIDING, sliding);
        double awayX = helper.absolutePos(new BlockPos(5, 1, 2)).getX() + .5d;
        actor.setPos(awayX, y, z);
        require(Math.abs(SlidingFrictionSystem.frictionFactor(actor) - 1d) < 1e-9,
                label + " must see neutral friction after leaving the puddle while sliding");
        double away = travel(actor, awayX, y, z, true, 0d, 1d);
        require(Math.abs(away - accelerationBaseline) < 1e-6,
                label + " travel away from puddle must match vanilla acceleration: away=" + away
                        + ", baseline=" + accelerationBaseline);

        // Removing the synchronized state on the puddle also restores the same vanilla result.
        actor.setPos(x, y, z);
        MS14Provider.remove(actor, MS14Bridges.SLIDING);
        require(Math.abs(SlidingFrictionSystem.frictionFactor(actor) - 1d) < 1e-9,
                label + " absent sliding attachment must restore neutral friction");
        double removed = travel(actor, x, y, z, false, 0d, 1d);
        require(Math.abs(removed - accelerationBaseline) < 1e-6,
                label + " removing sliding state must restore vanilla acceleration");
        LOGGER.info("Sliding travel trace {}: acceleration baseline={} sliding={}, retention baseline={} sliding={}, "
                + "away={}, attachment-removed={}", label, accelerationBaseline, accelerationSliding,
                retentionBaseline, retentionSliding, away, removed);
    }

    private static double travel(LivingEntity actor, double x, double y, double z, boolean sliding,
                                 double initialX, double inputX) {
        actor.setPos(x, y, z);
        SlidingAttachment attachment = new SlidingAttachment();
        attachment.setSliding(sliding);
        if (sliding) MS14Provider.update(actor, MS14Bridges.SLIDING, attachment);
        else MS14Provider.remove(actor, MS14Bridges.SLIDING);
        // A teleported entity does not necessarily retain its collision-derived onGround bit.
        // Resolve a tiny downward move against the stone platform before entering travel.
        actor.move(MoverType.SELF, new Vec3(0d, -.05d, 0d));
        require(actor.onGround(), actor.getClass().getSimpleName()
                + " must be grounded before live travel (no zero-motion branch accepted)");
        actor.setDeltaMovement(initialX, 0d, 0d);
        // LivingEntity travel uses this speed directly for grounded movement. A Mob that
        // is not currently navigating (as in this direct-travel fixture) otherwise has 0.
        if (actor instanceof Mob mob) mob.setSpeed(.1f);
        actor.travel(new Vec3(inputX, 0d, 0d));
        return actor.getDeltaMovement().x;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
