package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestTags({"timed-interaction", "ingestion", "damage", "hands"})
@PrefixGameTestTemplate(false)
public final class TimedInteractionDamageGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private TimedInteractionDamageGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 45)
    public static void actualDamageCancelsBeforeIngestionCompletes(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        LiveHands hands = body.getData(ModDataAttachments.LIVE_HANDS.get());
        var source = bottle(body, hands, 10f);
        final LiveHands liveHands = body.getData(ModDataAttachments.LIVE_HANDS.get());
        require(BodyTimedIngestionService.startHeldOwned(body, liveHands.activeHand(), source, liveHands.revision(), 10, () -> true).name().equals("OK"), "held ingestion starts");
        float initial = body.getHealth();
        helper.runAfterDelay(10, () -> {
            require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 1f), 1f, true,
                    DamageSystem.Cause.COMBAT) == DamageSystem.Result.APPLIED, "actual physical typed damage applied");
            float afterDamage = body.getHealth();
            body.heal(initial - afterDamage);
            require(Math.abs(body.getHealth() - initial) < .001f, "immediate healing restores actual health");
            require(Math.abs(afterDamage - (initial - .2f)) < .001f, "one typed damage projects to .2 health");
            TimedOperation op = operation(body);
            require(op.phase() == TimedOperation.Phase.CANCELLED, "damage event cancels operation despite same-frame healing");
            require(body.getData(ModDataAttachments.LIVE_HANDS.get()).token(liveHands.activeHand()).orElseThrow().equals(source), "source remains unchanged");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5, () -> {
                require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o -> o.id() == op.id() && o.phase() == TimedOperation.Phase.CANCELLED), "terminal cancellation retained for ten ticks");
                require(body.getData(ModDataAttachments.LIVE_HANDS.get()).token(liveHands.activeHand()).orElseThrow().equals(source), "cancelled operation produces no dose");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 6, () -> {
                    require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().noneMatch(o -> o.id() == op.id()), "terminal record pruned after retention window");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 50)
    public static void subthresholdRejectedDamageAndHealingDoNotCancel(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        LiveHands hands = body.getData(ModDataAttachments.LIVE_HANDS.get());
        String source = bottle(body, hands, 5f);
        final LiveHands liveHands = body.getData(ModDataAttachments.LIVE_HANDS.get());
        BodyTimedIngestionService.startHeldOwned(body, liveHands.activeHand(), source, liveHands.revision(), 11, () -> true);
        helper.runAfterDelay(2, () -> {
            body.invulnerableTime = 0;
            body.hurt(helper.getLevel().damageSources().generic(), 0f);
            require(Math.abs(body.getHealth() - body.getMaxHealth()) < .001f, "zero native damage leaves health unchanged");
            require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o -> o.phase() == TimedOperation.Phase.ACTIVE), "zero native damage does not cancel operation");
            require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, .5f), 1f, true,
                    DamageSystem.Cause.COMBAT) == DamageSystem.Result.APPLIED, "half physical typed damage applied");
            require(Math.abs(body.getHealth() - (body.getMaxHealth() - .1f)) < .001f, "actual final health confirms .1 health / .5 typed damage");
            body.heal(1f);
            require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o -> o.phase() == TimedOperation.Phase.ACTIVE), "subthreshold damage, rejection, and healing leave timer active");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 19, () -> {
                var currentHands = body.getData(ModDataAttachments.LIVE_HANDS.get());
                var remaining = currentHands.stackCopy(liveHands.activeHand()).orElseThrow().get(ModDataComponents.REAGENT.get());
                require(remaining != null && remaining.contents().isEmpty(), "dose leaves the retained bottle empty");
                require(currentHands.token(liveHands.activeHand()).orElseThrow().equals(source), "empty bottle remains in the hand");
                require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o -> o.phase() == TimedOperation.Phase.COMPLETED), "operation completed, not cancelled");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 35)
    public static void moveCancellationAndGenericCallbackCompletion(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        AtomicInteger breakMoveCalls = new AtomicInteger();
        require(TimedInteractionSystem.start(body, null, 12, "generic", "move", 20, false, true,
                () -> true, () -> { breakMoveCalls.incrementAndGet(); return TimedInteractionSystem.Completion.STOP; }).name().equals("STARTED"), "movement-sensitive generic operation starts");
        require(TimedInteractionSystem.start(body, null, 13, "generic", "still", 20, false, false,
                () -> true, () -> { breakMoveCalls.incrementAndGet(); return TimedInteractionSystem.Completion.STOP; }).name().equals("STARTED"), "movement-insensitive generic operation starts");
        long moveId = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().get(0).id();
        long stillId = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().get(1).id();
        helper.runAfterDelay(1, () -> body.setPos(body.getX() + 1, body.getY(), body.getZ()));
        helper.runAfterDelay(3, () -> {
            var operations = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations();
            require(operations.stream().anyMatch(o -> o.id() == moveId && o.phase() == TimedOperation.Phase.CANCELLED), "move-enabled operation cancels after movement");
            require(breakMoveCalls.get() == 0, "cancelled movement callback does not run");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 18, () -> {
                var laterOperations = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations();
                require(laterOperations.stream().noneMatch(o -> o.id() == moveId), "cancelled movement record prunes after retention");
                require(laterOperations.stream().anyMatch(o -> o.id() == stillId && o.phase() == TimedOperation.Phase.COMPLETED), "generic callback completes despite movement when breakMove is false");
                require(breakMoveCalls.get() == 1, "only the still-valid callback runs once");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 11, () -> {
                    require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().noneMatch(o -> o.id() == moveId || o.id() == stillId), "both terminal records prune");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 35)
    @GameTestTags({"construction"})
    public static void thresholdCancellationKeepsConstructionPresentationAndRefundCallback(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        AtomicInteger refunds = new AtomicInteger();
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 2));
        require(TimedInteractionSystem.start(body, null, 23, "construction_graph", "threshold-refund",
                20, true, false, 3.0, () -> true, () -> TimedInteractionSystem.Completion.STOP,
                java.util.Optional.of(target), refunds::incrementAndGet).name().equals("STARTED"),
                "threshold-aware construction operation starts");
        var initial = TimedInteractionSystem.snapshot(body);
        require(initial.views().size() == 1 && initial.views().get(0).phase() == TimedOperation.Phase.ACTIVE
                && initial.views().get(0).worldTarget().equals(java.util.Optional.of(target)),
                "live snapshot immediately publishes work target");

        TimedInteractionSystem.damage(body, 2.9);
        require(operation(body).phase() == TimedOperation.Phase.ACTIVE && refunds.get() == 0,
                "subthreshold typed damage neither cancels nor refunds");
        TimedInteractionSystem.damage(body, 3.0);
        require(operation(body).phase() == TimedOperation.Phase.CANCELLED && refunds.get() == 1,
                "threshold typed damage cancels and invokes refund once");
        var cancelled = TimedInteractionSystem.snapshot(body);
        require(cancelled.views().get(0).worldTarget().equals(java.util.Optional.of(target)),
                "cancelled terminal snapshot retains the published work target");
        TimedInteractionSystem.damage(body, 3.0);
        require(refunds.get() == 1, "repeated damage cannot refund twice");
        helper.runAfterDelay(12, () -> {
            require(refunds.get() == 1, "terminal pruning cannot refund twice");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 120)
    @GameTestTags({"portable-escape"})
    public static void portableEscapeCompletesOnceAndRepeatedInputDoesNotRestart(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        EscapeCustody custody = new EscapeCustody(body.position());
        require(PortableBodyEscapeService.requestForCapability(body, null, 1, 100, custody)
                == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED, "escape begins");
        require(TimedInteractionSystem.snapshot(body).views().stream()
                .anyMatch(view -> view.originatingActionSequence() == 1),
                "escape progress snapshot carries the originating action sequence");
        long start = operation(body).startTick();
        helper.runAfterDelay(25, () -> {
            require(PortableBodyEscapeService.requestForCapability(body, null, 2, 100, custody)
                    == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED, "repeat input preserves attempt");
            require(operation(body).startTick() == start && operation(body).phase() == TimedOperation.Phase.ACTIVE, "no reset/cancel");
        });
        helper.runAfterDelay(102, () -> {
            require(operation(body).phase() == TimedOperation.Phase.COMPLETED && custody.releases == 1, "one release at due time");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    @GameTestTags({"portable-escape"})
    public static void portableEscapeMovementRelativeDistanceAndCustodyInterrupt(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        EscapeCustody custody = new EscapeCustody(body.position().add(1, 0, 0));
        PortableBodyEscapeService.requestForCapability(body, null, 1, 20, custody);
        long actorMoveId = operation(body).id();
        helper.runAfterDelay(1, () -> body.setPos(body.getX() + .31, body.getY(), body.getZ()));
        helper.runAfterDelay(3, () -> {
            require(operation(body, actorMoveId).phase() == TimedOperation.Phase.CANCELLED && custody.releases == 0, "actor movement interrupts");
            body.setPos(body.getX() - .31, body.getY(), body.getZ());
            custody.root = body.position().add(1, 0, 0);
            require(PortableBodyEscapeService.requestForCapability(body, null, 2, 20, custody)
                    == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED,
                    "stationary actor starts target-distance attempt");
            custody.root = custody.root.add(.31, 0, 0);
            long targetMoveId = operation(body).id();
            helper.runAfterDelay(3, () -> {
                require(operation(body, targetMoveId).phase() == TimedOperation.Phase.CANCELLED
                                && custody.releases == 0,
                        "target separation change over .3 cancels while still inside 1.5 range");
                custody.current = false;
                require(PortableBodyEscapeService.requestForCapability(body, null, 3, 20, custody)
                                == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED,
                        "stale custody cannot begin a new attempt after drop");
                require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                                .filter(op -> op.kind().equals(PortableBodyEscapeService.OPERATION_KIND))
                                .noneMatch(op -> op.phase() == TimedOperation.Phase.ACTIVE),
                        "drop leaves no active escape operation");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    @GameTestTags({"portable-escape"})
    public static void portableEscapeRangeAndAccessibilityAreIndependentFromMovement(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        EscapeCustody boundaryCustody = new EscapeCustody(body.position().add(1.5, 0, 0));
        require(PortableBodyEscapeService.requestForCapability(body, null, 1, 20, boundaryCustody).name().equals("STARTED"),
                "equal 1.5 range boundary admitted");
        long boundaryId = operation(body).id();
        boundaryCustody.current = false;
        helper.runAfterDelay(1, () -> {
            require(operation(body, boundaryId).phase() == TimedOperation.Phase.CANCELLED,
                    "stale custody cancellation is observed on shared scheduler tick");
            EscapeCustody outOfRange = new EscapeCustody(body.position().add(1.5001, 0, 0));
            require(PortableBodyEscapeService.requestForCapability(body, null, 2, 20, outOfRange)
                    == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED,
                    "out-of-range custody denied without motion");
            EscapeCustody inaccessible = new EscapeCustody(body.position());
            inaccessible.accessible = false;
            require(PortableBodyEscapeService.requestForCapability(body, null, 3, 20, inaccessible)
                    == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED,
                    "inaccessible custody denied");
            EscapeCustody losesAccess = new EscapeCustody(body.position());
            require(PortableBodyEscapeService.requestForCapability(body, null, 4, 20, losesAccess).name().equals("STARTED"), "accessible custody starts");
            long accessId = operation(body).id();
            helper.runAfterDelay(1, () -> losesAccess.accessible = false);
            helper.runAfterDelay(3, () -> {
                require(operation(body, accessId).phase() == TimedOperation.Phase.CANCELLED
                                && losesAccess.releases == 0,
                        "lost accessibility cancels without release");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 10)
    @GameTestTags({"portable-escape"})
    public static void portableEscapeStationaryTargetSeparationProbes(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        EscapeCustody halfBlock = new EscapeCustody(body.position().add(.5, 0, 0));
        require(PortableBodyEscapeService.requestForCapability(body, null, 1, 20, halfBlock).name().equals("STARTED"),
                "stationary target at .5 separation admitted");
        helper.runAfterDelay(2, () -> {
            require(operation(body).phase() == TimedOperation.Phase.ACTIVE, "stationary .5 separation does not interrupt");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 110)
    @GameTestTags({"portable-escape", "character-prototype", "portable-escape-failure"})
    public static void resolvedEscapeCapabilityDrivesProductionDuration(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var body = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(body != null, "ordinary composed mouse created");
        BlockPos absolute = helper.absolutePos(new BlockPos(2, 1, 2));
        body.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        require(level.addFreshEntity(body), "ordinary mouse joins and resolves its composed character");
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var anchor = new com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner() {
            public java.util.UUID identity() { return body.getUUID(); }
            public net.minecraft.server.level.ServerLevel level() { return level; }
            public net.minecraft.world.phys.Vec3 position() { return body.position(); }
            public Stage stage() { return Stage.STATIONARY; }
            public boolean isValid() { return true; }
            public boolean tryEscape(net.minecraft.world.entity.LivingEntity ignored) { return false; }
        };
        require(containment.bind(body, anchor), "scheduler fixture anchors the living mouse without a competing body");
        EscapeCustody custody = new EscapeCustody(body.position());
        require(PortableBodyEscapeService.request(body, null, 1, custody)
                == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED,
                "resolved mouse capability starts production request");
        var operation = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().get(0);
        require(operation.durationTicks() == 100, "resolved five-second default schedules 100 ticks");
        helper.runAfterDelay(102, () -> {
            require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                            .anyMatch(current -> current.id() == operation.id() && current.phase() == TimedOperation.Phase.COMPLETED)
                            && custody.releases == 1,
                    "resolved default drives completion");
            containment.unbind(body, anchor);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    @GameTestTags({"portable-escape", "character-prototype"})
    public static void resolvedCriticalMouseCannotStartPortableEscape(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "resolved mouse prototype creates actor");
        BlockPos absolute = helper.absolutePos(new BlockPos(2, 1, 2));
        mouse.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        require(level.addFreshEntity(mouse), "resolved mouse joins world");
        var custody = new EscapeCustody(mouse.position());
        require(DamageSystem.applyHealthChange(mouse, Map.of(DamageKeys.BLUNT, 10f), 1f, true)
                        == DamageSystem.Result.APPLIED
                        && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(mouse)
                        && mouse.isAlive(),
                "real typed damage creates a Critical mouse while Minecraft entity remains alive");
        require(!CharacterControlSystem.canAct(mouse), "Critical mouse cannot act");
        require(PortableBodyEscapeService.request(mouse, null, 1, custody)
                        == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED,
                "resolved production escape admission denies incapacitated mouse");
        require(level.getEntity(mouse.getUUID()) == mouse && custody.current && custody.releases == 0
                        && mouse.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()) == null,
                "denial preserves the exact body and custody without creating timed progress");
        mouse.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    @GameTestTags({"portable-escape", "character-prototype"})
    public static void stunBlocksEscapeAdmissionAndCancelsActiveAttempt(GameTestHelper helper) {
        Villager deniedBody = body(helper, new BlockPos(2, 1, 2));
        require(CharacterControlSystem.applyStun(deniedBody, 20) && !CharacterControlSystem.canAct(deniedBody),
                "resolved stunnable HUMAN receives a real action-blocking status");
        var deniedCustody = new EscapeCustody(deniedBody.position());
        var capability = new com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent(5f);
        require(PortableBodyEscapeService.requestForResolvedCapability(deniedBody, null, 1, capability, deniedCustody)
                        == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED
                        && deniedBody.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()) == null
                        && deniedCustody.current && deniedCustody.releases == 0,
                "stun denies admission without consuming custody or creating progress");

        Villager activeBody = body(helper, new BlockPos(5, 1, 2));
        var activeCustody = new EscapeCustody(activeBody.position());
        require(PortableBodyEscapeService.requestForResolvedCapability(activeBody, null, 2, capability, activeCustody)
                        == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED,
                "eligible actor starts timed operation");
        long operationId = operation(activeBody).id();
        require(CharacterControlSystem.applyStun(activeBody, 20), "active actor receives actual stun status");
        helper.runAfterDelay(2, () -> {
            require(operation(activeBody, operationId).phase() == TimedOperation.Phase.CANCELLED
                            && activeCustody.current && activeCustody.releases == 0
                            && helper.getLevel().getEntity(activeBody.getUUID()) == activeBody,
                    "new incapacity cancels active operation and preserves body/custody");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 10)
    @GameTestTags({"portable-escape", "character-prototype"})
    public static void absentResolvedEscapeCapabilityDeniesProductionRequest(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        EscapeCustody custody = new EscapeCustody(body.position());
        require(PortableBodyEscapeService.request(body, null, 1, custody)
                == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED,
                "human with no escape composition is denied");
        require(custody.releases == 0 && body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()) == null,
                "absent capability creates no operation or release");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 70)
    @GameTestTags({"portable-escape"})
    public static void resolvedDurationOverrideChangesScheduledCompletion(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        EscapeCustody custody = new EscapeCustody(body.position());
        require(PortableBodyEscapeService.requestForResolvedCapability(body, null, 1,
                        new com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent(3f), custody)
                        == com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED,
                "resolved three-second override starts");
        require(operation(body).durationTicks() == 60, "override converts three seconds to 60 ticks");
        helper.runAfterDelay(61, () -> {
            require(operation(body).phase() == TimedOperation.Phase.COMPLETED && custody.releases == 1,
                    "override drives scheduled completion");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 30)
    @GameTestTags({"portable-escape", "damage"})
    public static void portableEscapeDamageAndFailedRemovalAreSafe(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        EscapeCustody custody = new EscapeCustody(body.position());
        PortableBodyEscapeService.requestForCapability(body, null, 1, 20, custody);
        TimedInteractionSystem.damage(body, .99);
        require(operation(body).phase() == TimedOperation.Phase.ACTIVE, "subthreshold damage is ignored");
        TimedInteractionSystem.damage(body, 1);
        require(operation(body).phase() == TimedOperation.Phase.CANCELLED && custody.releases == 0, "committed threshold damage cancels");
        custody = new EscapeCustody(body.position());
        custody.releaseAllowed = false;
        EscapeCustody failed = custody;
        PortableBodyEscapeService.requestForCapability(body, null, 2, 2, failed);
        helper.runAfterDelay(4, () -> {
            require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                    .filter(op -> op.kind().equals(PortableBodyEscapeService.OPERATION_KIND)).anyMatch(op -> op.phase() == TimedOperation.Phase.CANCELLED), "failed removal denies completion");
            require(failed.releases == 0 && failed.current, "failure does not unbind custody");
            helper.succeed();
        });
    }

    private static final class EscapeCustody implements PortableBodyEscapeService.Custody {
        Vec3 root; boolean current = true, removable = true, accessible = true, releaseAllowed = true; int releases;
        EscapeCustody(Vec3 root) { this.root = root; }
        public boolean isCurrent() { return current; }
        public boolean canRemoveReference() { return removable; }
        public boolean isAccessible() { return accessible; }
        public Vec3 rootPosition() { return root; }
        public boolean releaseOriginalBody() { if (!current || !removable || !releaseAllowed) return false; releases++; current = false; return true; }
    }

    private static Villager body(GameTestHelper helper, BlockPos pos) {
        return body(helper, pos, ModCharacters.HUMAN_ID);
    }

    private static Villager body(GameTestHelper helper, BlockPos pos, ResourceLocation characterId) {
        var level = helper.getLevel();
        level.setBlock(helper.absolutePos(pos).below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true); body.setNoGravity(true);
        BlockPos absolute = helper.absolutePos(pos);
        body.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, characterId), "character body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "body and hands initialized");
        return body;
    }

    private static String bottle(Villager body, LiveHands hands, float units) {
        ItemStack filled = new ItemStack(ModItems.BOTTLE.get());
        filled.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, units)));
        var inserted = hands.putWhole(hands.revision(), hands.activeHand(),
                new com.juicyslew.moonstation14.ms14.hands.ItemToken("timed-damage-water-bottle"), filled);
        require(inserted.succeeded(), "positive-water bottle placed in active hand");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        return inserted.state().token(inserted.state().activeHand()).orElseThrow();
    }

    private static TimedOperation operation(Villager body) {
        return body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().get(0);
    }

    private static TimedOperation operation(Villager body, long id) {
        return body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                .filter(operation -> operation.id() == id).findFirst().orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

