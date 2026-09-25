package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.slip.SlipEvent;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectInstance;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementSlipChainDiagnosticsGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(MovementSlipChainDiagnosticsGameTests.class);

    private MovementSlipChainDiagnosticsGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void distinctSpaceLubeSourcesDoNotRelaunchWhileSliding(GameTestHelper helper) {
        int[] sourceX = {2, 5};
        List<PuddleBlockEntity> puddles = new ArrayList<>();
        for (int x = 1; x <= 8; x++) helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
        for (int x : sourceX) {
            BlockPos puddlePos = new BlockPos(x, 1, 2);
            helper.setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState());
            PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel()
                    .getBlockEntity(helper.absolutePos(puddlePos));
            ReagentAttachment contents = new ReagentAttachment();
            contents.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
            require(contents.snapshotUnits().getOrDefault(ModReagents.createKey("spacelube"), 0L) == 2000L,
                    "each source must start with 20u Space Lube for strict slip qualification");
            MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
            puddles.add(puddle);
        }

        FakePlayer player = new FakePlayer((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "slip-chain-diagnostic"));
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 2));
        player.setPos(start.getX() + .5d, start.getY(), start.getZ() + .5d);
        require(helper.getLevel().addFreshEntity(player), "diagnostic FakePlayer must join the server level");

        List<SlipEvent> events = new ArrayList<>();
        List<Vec3> eventVelocities = new ArrayList<>();
        Vec3[] firstLaunchVelocity = {null};
        Vec3[] secondPreVelocity = {null};
        Vec3[] secondPostVelocity = {null};
        java.util.function.Consumer<SlipEvent> listener = event -> {
            if (event.target() == player) {
                events.add(event);
                eventVelocities.add(player.getDeltaMovement());
            }
        };
        require(SlipSystem.addListener(listener), "diagnostic slip listener registration");
        AtomicBoolean cleaned = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (cleaned.compareAndSet(false, true)) SlipSystem.removeListener(listener);
        };
        helper.runAfterDelay(29, cleanup);

        helper.runAfterDelay(2, () -> {
            try {
                require(CharacterControlSystem.canAct(player), "FakePlayer must be a bound character actor");
                // These controlled samples test admission at a second source while still
                // sliding. Physical contact-exit reconciliation is covered separately.
                Vec3[] deltas = {
                        new Vec3(.6d, 0d, 0d), // first source: .6 movement -> .9 launch
                        new Vec3(.6d, 0d, 0d),
                        new Vec3(1.5d, 0d, 0d),
                        new Vec3(.4d, 0d, 0d),
                        new Vec3(.9d, 0d, 0d) // second source sees the measured first launch
                };
                for (int tick = 0; tick < deltas.length; tick++) {
                    Vec3 before = player.position();
                    Vec3 preVelocity = player.getDeltaMovement();
                    player.move(MoverType.SELF, deltas[tick]);
                    Vec3 accepted = player.position().subtract(before);
                    require(accepted.lengthSqr() > 1.0e-8d && accepted.lengthSqr() <= 2.25d,
                            "real FakePlayer move must yield a nonzero accepted sample <= 1.5; tick=" + tick
                                    + ", requested=" + deltas[tick] + ", accepted=" + accepted);
                    SlipSystem.onAcceptedPlayerMovement(player, accepted);
                    Vec3 postVelocity = player.getDeltaMovement();
                    if (tick == 0) firstLaunchVelocity[0] = postVelocity;
                    if (tick == 4) {
                        secondPreVelocity[0] = preVelocity;
                        secondPostVelocity[0] = postVelocity;
                    }
                    LOGGER.info("Slip chain movement tick {}: requested={}, accepted={}, preVelocity={}, "
                                    + "postVelocity={}, events={}, stunned={}, sliding={}, position={}",
                            tick, deltas[tick], accepted, preVelocity, postVelocity, events.size(),
                            CharacterControlSystem.isStunned(player), SlipSystem.isSliding(player), player.position());
                }

                LOGGER.info("Slip chain summary: eventCount={}, sourcePositions={}, finalVelocity={}, "
                                + "knockdown={}, sliding={}, sourceVolumes={}", events.size(),
                                events.stream().map(event -> event.sourcePosition().toString()).toList(),
                        player.getDeltaMovement(), CharacterControlSystem.isStunned(player),
                        SlipSystem.isSliding(player), puddles.stream()
                                .map(puddle -> MS14Provider.get(puddle, MS14Bridges.REAGENT).snapshotUnits()).toList());

                // Do not force admission or mask a latch failure: each observed event must
                // be from a distinct prepared Space Lube source and carry super-slippery data.
                require(events.size() == 2, "two distinct qualifying sources should admit exactly two valid contacts "
                        + "during the controlled samples; "
                        + "observed=" + events.stream().map(event -> event.sourcePosition().toString()).toList());
                for (int index = 0; index < events.size(); index++) {
                    SlipEvent event = events.get(index);
                    require(event.target() == player && event.solution().superSlippery(),
                            "event " + index + " must be a super-slippery admission for the diagnostic player");
                    require(event.sourcePosition().getX() == helper.absolutePos(new BlockPos(sourceX[index], 1, 2)).getX(),
                            "event " + index + " must identify a distinct expected source tile: " + event.sourcePosition());
                    require(event.solution().launchVelocityMultiplier() == 1.5d,
                            "Space Lube source must report the expected 1.5 launch multiplier");
                }
                require(!events.get(0).wasSliding() && events.get(1).wasSliding(),
                        "first admitted contact starts sliding; second source event observes existing sliding");
                require(eventVelocities.get(0).equals(Vec3.ZERO),
                        "the real first entry must launch from its pre-event velocity");
                require(firstLaunchVelocity[0] != null
                                && eventVelocities.get(1).equals(firstLaunchVelocity[0])
                                && secondPreVelocity[0].equals(firstLaunchVelocity[0])
                                && secondPostVelocity[0].equals(firstLaunchVelocity[0])
                                && player.getDeltaMovement().equals(firstLaunchVelocity[0]),
                        "the second qualifying source must preserve the measured first launch velocity; "
                                + "first launch=" + firstLaunchVelocity[0] + ", event velocities=" + eventVelocities
                                + ", second pre/post=" + secondPreVelocity[0] + "/" + secondPostVelocity[0]
                                + ", final=" + player.getDeltaMovement());
                require(CharacterControlSystem.isStunned(player) && SlipSystem.isSliding(player),
                        "successful knockdown admissions must leave the FakePlayer stunned and sliding");
                cleanup.run();
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void separatedSuperSlipperySourcesRelaunchAfterContactExit(GameTestHelper helper) {
        int[] sourceX = {2, 5};
        for (int x = 1; x <= 7; x++) helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
        for (int x : sourceX) {
            BlockPos position = new BlockPos(x, 1, 2);
            helper.setBlock(position, ModBlocks.PUDDLE.get().defaultBlockState());
            PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel()
                    .getBlockEntity(helper.absolutePos(position));
            ReagentAttachment contents = new ReagentAttachment();
            contents.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
            MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
        }

        Villager target = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 2));
        target.setNoAi(true);
        List<SlipEvent> events = new ArrayList<>();
        java.util.function.Consumer<SlipEvent> listener = event -> {
            if (event.target() == target) events.add(event);
        };
        require(SlipSystem.addListener(listener), "separated-source observer registration");
        helper.runAfterDelay(29, () -> SlipSystem.removeListener(listener));

        helper.runAfterDelay(2, () -> {
            try {
                BlockPos first = helper.absolutePos(new BlockPos(sourceX[0], 1, 2));
                target.setPos(first.getX() + .5d, first.getY(), first.getZ() + .5d);
                target.setDeltaMovement(.2d, 0d, 0d);
                SlipSystem.onPuddleContact((net.minecraft.server.level.ServerLevel) helper.getLevel(), first, target);
                require(events.size() == 1 && !events.get(0).wasSliding() && SlipSystem.isSliding(target),
                        "first qualifying source must start sliding");
                require(SlidingFrictionSystem.hasQualifyingContact(target),
                        "feet contact on the first source must qualify for sliding");
                require(CharacterControlSystem.isStunned(target), "first source must leave knockdown active");
                StatusEffectAttachment statuses = target.getExistingDataOrNull(
                        com.juicyslew.moonstation14.component.ModDataAttachments.STATUS_EFFECT.get());
                var knockdownKey = ModStatusEffects.createKey("knockdown");
                StatusEffectInstance knockdown = statuses.get(knockdownKey).orElseThrow();

                // Reconcile while still contacting the first source, then move wholly into the gap.
                SlipSystem.reconcileTarget(target);
                require(SlipSystem.isSliding(target), "continued qualifying contact must retain sliding marker");
                target.setPos(helper.absolutePos(new BlockPos(3, 1, 2)).getX() + .5d,
                        helper.absolutePos(new BlockPos(3, 1, 2)).getY(),
                        helper.absolutePos(new BlockPos(3, 1, 2)).getZ() + .5d);
                SlipSystem.reconcileTarget(target);
                require(!SlipSystem.isSliding(target) && !target.hasData(
                                com.juicyslew.moonstation14.component.ModDataAttachments.SLIDING.get()),
                        "leaving all qualifying contacts must clear only the sliding projection");
                require(CharacterControlSystem.isStunned(target)
                                && statuses.get(knockdownKey).orElseThrow().equals(knockdown),
                        "contact exit must preserve knockdown status and timing");

                BlockPos second = helper.absolutePos(new BlockPos(sourceX[1], 1, 2));
                target.setPos(second.getX() + .5d, second.getY(), second.getZ() + .5d);
                target.setDeltaMovement(.2d, 0d, 0d);
                SlipSystem.onPuddleContact((net.minecraft.server.level.ServerLevel) helper.getLevel(), second, target);
                require(events.size() == 2 && !events.get(1).wasSliding() && SlipSystem.isSliding(target),
                        "second source after exit must relaunch once despite existing knockdown");
                require(CharacterControlSystem.isStunned(target)
                                && StatusEffectSystem.hasStatus(((IStatusEffectTrait) target).toHandleSelf(),
                                helper.getLevel(), knockdownKey),
                        "knocked-down relaunch must not apply a new stun; existing knockdown remains active");
                require(target.getDeltaMovement().distanceToSqr(new Vec3(.3d, 0d, 0d)) < 1.0e-12d,
                        "relaunch after exit must apply source momentum once; velocity=" + target.getDeltaMovement());
                helper.succeed();
            } finally {
                SlipSystem.removeListener(listener);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void clearingKnockdownDoesNotRearmLatchedPuddleUntilExitAndReentry(GameTestHelper helper) {
        BlockPos sourceLocal = new BlockPos(4, 1, 2);
        BlockPos floorLocal = new BlockPos(3, 0, 2);
        for (int x = 1; x <= 8; x++) helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
        helper.setBlock(sourceLocal, ModBlocks.PUDDLE.get().defaultBlockState());
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(sourceLocal));
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("spacelube"), 20f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);

        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        FakePlayer player = new FakePlayer(level,
                new GameProfile(UUID.randomUUID(), "slip-latch-diagnostic"));
        BlockPos start = helper.absolutePos(floorLocal);
        player.setPos(start.getX() + .5d, start.getY() + 1d, start.getZ() + .5d);
        require(level.addFreshEntity(player), "diagnostic FakePlayer must join the server level");

        BlockPos source = helper.absolutePos(sourceLocal);
        List<SlipEvent> events = new ArrayList<>();
        List<String> allTargetEvents = new ArrayList<>();
        String[] diagnosticPhase = {"setup"};
        java.util.function.Consumer<SlipEvent> listener = event -> {
            if (event.target() == player && event.sourcePosition().equals(source)) events.add(event);
        };
        java.util.function.Consumer<SlipEvent> allTargetListener = event -> {
            if (event.target() == player) {
                String trace = "phase=" + diagnosticPhase[0] + ", source=" + event.sourcePosition()
                        + ", wasSliding=" + event.wasSliding();
                allTargetEvents.add(trace);
                LOGGER.info("Slip latch all-source event: {}, position={}, sliding={}, stunned={}", trace,
                        player.position(), SlipSystem.isSliding(player), CharacterControlSystem.isStunned(player));
            }
        };
        require(SlipSystem.addListener(listener), "source-filtered diagnostic slip listener registration");
        require(SlipSystem.addListener(allTargetListener), "all-source diagnostic slip listener registration");
        AtomicBoolean cleaned = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (cleaned.compareAndSet(false, true)) {
                SlipSystem.removeListener(listener);
                SlipSystem.removeListener(allTargetListener);
            }
        };
        helper.runAfterDelay(29, cleanup);

        helper.runAfterDelay(2, () -> {
            try {
                require(CharacterControlSystem.canAct(player), "FakePlayer must resolve as a bound character actor");
                AABB sourceShape = level.getBlockState(source).getShape(level, source,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()).bounds().move(source);
                LOGGER.info("Slip latch diagnostic initial: position={}, bounds={}, sliding={}, stunned={}, "
                                + "source={}, sourceBounds={}", player.position(), player.getBoundingBox(),
                        SlipSystem.isSliding(player), CharacterControlSystem.isStunned(player), source, sourceShape);

                Vec3 entryRequested = new Vec3(.6d, 0d, 0d);
                diagnosticPhase[0] = "before-entry-move";
                LOGGER.info("Slip latch diagnostic before move: phase={}, position={}, slidingAttachment={}, "
                                + "sliding={}, stunned={}, eventCount={}, allTargetEvents={}", diagnosticPhase[0],
                        player.position(), player.getExistingDataOrNull(
                                com.juicyslew.moonstation14.component.ModDataAttachments.SLIDING.get()),
                        SlipSystem.isSliding(player), CharacterControlSystem.isStunned(player), events.size(), allTargetEvents);
                Vec3 entry = moveAccepted(player, entryRequested, "entry");
                diagnosticPhase[0] = "after-entry-move-before-hook";
                LOGGER.info("Slip latch diagnostic after move before hook: phase={}, accepted={}, position={}, "
                                + "slidingAttachment={}, sliding={}, stunned={}, ownSourceEvents={}, allTargetEvents={}",
                        diagnosticPhase[0], entry, player.position(), player.getExistingDataOrNull(
                                com.juicyslew.moonstation14.component.ModDataAttachments.SLIDING.get()),
                        SlipSystem.isSliding(player), CharacterControlSystem.isStunned(player), events.size(), allTargetEvents);
                diagnosticPhase[0] = "during-entry-explicit-hook";
                SlipSystem.onAcceptedPlayerMovement(player, entry);
                diagnosticPhase[0] = "after-entry-explicit-hook";
                double entryEndRatio = SlipSystem.intersectionRatio(player.getBoundingBox(), sourceShape, source);
                LOGGER.info("Slip latch diagnostic entry: phase={}, requested={}, accepted={}, ratio={}, events={}, "
                                + "eventSliding={}, slidingAttachment={}, marker={}, stunned={}, sourceUnits={}, "
                                + "position={}, allTargetEvents={}",
                        diagnosticPhase[0],
                        entryRequested, entry, entryEndRatio,
                        events.size(), events.isEmpty() ? null : events.get(0).wasSliding(),
                        player.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.SLIDING.get()),
                        SlipSystem.isSliding(player), CharacterControlSystem.isStunned(player),
                        MS14Provider.get(puddle, MS14Bridges.REAGENT).snapshotUnits(), player.position(), allTargetEvents);
                require(events.size() == 1, "entry must emit exactly one source event; count=" + events.size()
                        + ", position=" + player.position() + ", all-target event trace=" + allTargetEvents);
                require(!events.get(0).wasSliding(), "first entry must report wasSliding=false; position="
                        + player.position() + ", all-target event trace=" + allTargetEvents);
                require(events.get(0).source() == puddle && events.get(0).sourcePosition().equals(source),
                        "first event must identify the exact diagnostic puddle");
                require(entryEndRatio >= .3d, "entry must finish in qualifying virtual source area; ratio="
                        + entryEndRatio);
                require(CharacterControlSystem.isStunned(player) && SlipSystem.isSliding(player),
                        "first entry must establish knockdown and sliding projection");

                StatusEffectSystem.clearAll(((IStatusEffectTrait) player).toHandleSelf(), level);
                SlipSystem.reconcileTarget(player);
                require(!CharacterControlSystem.isStunned(player), "clearing statuses must remove knockdown");
                require(!SlipSystem.isSliding(player) && !player.hasData(
                                com.juicyslew.moonstation14.component.ModDataAttachments.SLIDING.get()),
                        "reconcile must clear Sliding marker after knockdown removal");

                Vec3 withinRequested = new Vec3(.06d, 0d, 0d);
                Vec3 within = moveAccepted(player, withinRequested, "within-source");
                double withinRatio = SlipSystem.intersectionRatio(player.getBoundingBox(), sourceShape, source);
                SlipSystem.onAcceptedPlayerMovement(player, within);
                LOGGER.info("Slip latch diagnostic within-source: requested={}, accepted={}, speed={}, ratio={}, "
                                + "events={}, sliding={}, sourceUnits={}", withinRequested, within,
                        within.length() * 20d, withinRatio, events.size(), SlipSystem.isSliding(player),
                        MS14Provider.get(puddle, MS14Bridges.REAGENT).snapshotUnits());
                require(within.length() * 20d >= 1d,
                        "within-source accepted speed must meet Space Lube's 1 block/s threshold; accepted=" + within);
                require(withinRatio >= .3d, "small movement must remain inside qualifying source; ratio=" + withinRatio);
                require(events.size() == 1,
                        "standing/moving within a latched puddle must not emit a second event; count=" + events.size());

                Vec3 exitRequested = new Vec3(1.5d, 0d, 0d);
                AABB beforeExit = player.getBoundingBox();
                double exitStartRatio = SlipSystem.intersectionRatio(beforeExit, sourceShape, source);
                Vec3 exit = moveAccepted(player, exitRequested, "exit");
                double exitEndRatio = SlipSystem.intersectionRatio(player.getBoundingBox(), sourceShape, source);
                LOGGER.info("Slip latch diagnostic exit: requested={}, accepted={}, startRatio={}, endRatio={}, "
                                + "events={}, position={}, sourceUnits={}", exitRequested, exit, exitStartRatio,
                        exitEndRatio, events.size(), player.position(),
                        MS14Provider.get(puddle, MS14Bridges.REAGENT).snapshotUnits());
                require(exit.length() <= 1.5d, "accepted exit movement must be no more than 1.5; accepted=" + exit);
                require(exitStartRatio >= .3d && exitEndRatio < .3d,
                        "exit must cross virtual trigger threshold: start=" + exitStartRatio + ", end=" + exitEndRatio);
                require(events.size() == 1, "source exit must not emit an event; count=" + events.size());
                SlipSystem.onAcceptedPlayerMovement(player, exit);
                SlipSystem.reconcileTarget(player);
                require(events.size() == 1, "reconciling a complete exit must not emit an event; count=" + events.size());
                long remainingCents = MS14Provider.get(puddle, MS14Bridges.REAGENT).snapshotUnits()
                        .getOrDefault(ModReagents.createKey("spacelube"), 0L);
                require(remainingCents > 1500L,
                        "source must retain >15u after first slip so reentry remains qualifying; cents=" + remainingCents);

                Vec3 reentryRequested = new Vec3(-1.5d, 0d, 0d);
                Vec3 reentry = moveAccepted(player, reentryRequested, "reentry");
                SlipSystem.onAcceptedPlayerMovement(player, reentry);
                double reentryRatio = SlipSystem.intersectionRatio(player.getBoundingBox(), sourceShape, source);
                LOGGER.info("Slip latch diagnostic reentry: requested={}, accepted={}, speed={}, ratio={}, "
                                + "events={}, sourcePositions={}, sliding={}, sourceUnits={}", reentryRequested,
                        reentry, reentry.length() * 20d, reentryRatio, events.size(),
                        events.stream().map(event -> event.sourcePosition().toString()).toList(),
                        SlipSystem.isSliding(player), MS14Provider.get(puddle, MS14Bridges.REAGENT).snapshotUnits());
                require(reentryRatio >= .3d, "reentry must finish in qualifying virtual source area; ratio="
                        + reentryRatio);
                require(events.size() == 2, "reentry after full exit must emit exactly a second event; count="
                        + events.size());
                require(events.get(1).source() == puddle && events.get(1).sourcePosition().equals(source),
                        "second event must identify the same source puddle");
                require(!events.get(1).wasSliding(),
                        "second event after status clear and exit must independently report wasSliding=false");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    private static Vec3 moveAccepted(FakePlayer player, Vec3 requested, String label) {
        Vec3 before = player.position();
        player.move(MoverType.SELF, requested);
        Vec3 accepted = player.position().subtract(before);
        require(accepted.lengthSqr() > 1.0e-8d && accepted.lengthSqr() <= 2.25d,
                "real " + label + " movement must yield nonzero accepted displacement <= 1.5; requested="
                        + requested + ", accepted=" + accepted);
        return accepted;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
