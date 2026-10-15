package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent;
import com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent;
import com.juicyslew.moonstation14.ms14.character.components.ThirstPrototypeComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerAttachment;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleStartupRuntime;
import com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Prototype-driven NPC loop. It plans here, but all actual locomotion is one shared-motor step. */
public final class NpcBehaviorSystem {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int MAX_REJECTED_SOURCES_PER_BRAIN = 128;
    private static final Map<UUID, Brain> BRAINS = new HashMap<>();
    private static final GroundedHarnessWorldStep MOTOR = new GroundedHarnessWorldStep();
    private static final GroundNpcRoutePlanner ROUTER = new GroundNpcRoutePlanner();
    private NpcBehaviorSystem() { }

    private static final class Brain {
        long nextPlan;
        UUID target;
        long npcIngestionOperationId;
        NpcBehaviorComponent.Task activeTask;
        Vec3 idlePoint;
        List<GroundNpcRoutePlanner.Waypoint> route = List.of();
        long idleUntil;
        long nextAttentionProbe;
        long attentionUntil;
        UUID attentionTarget;
        int idleWait;
        Vec3 targetPosition;
        long nextTargetRoute;
        Vec3 lastProgressPosition;
        long lastProgressTick;
        Vec3 fleeOrigin;
        Vec3 fleePoint;
        long fleeUntil;
        long nextFleeRoute;
        int fleeFallback;
        boolean fleeArrived;
        UUID lastAdmissionFailureTarget;
        com.juicyslew.moonstation14.ms14.ingestion.TimedIngestionAccess.WorldAdmissionFailure lastAdmissionFailure;
        com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.ProbeDiagnostic lastProbeDiagnostic;
        Vec3 lastFailureEye;
        Vec3 lastFailureLook;
        net.minecraft.world.phys.AABB lastFailureTargetBounds;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastFailureDimension;
        String lastRouteResult;
        UUID lastRouteFailureTarget;
        String lastMotorStep;
        final Map<UUID, Long> rejectedUntil = new HashMap<>();
    }
    record DebugSnapshot(NpcBehaviorComponent.Task task, Vec3 idlePoint, long idleUntil, int idleWait,
                         UUID itemTarget, List<GroundNpcRoutePlanner.Waypoint> route, UUID failedTarget,
                         com.juicyslew.moonstation14.ms14.ingestion.TimedIngestionAccess.WorldAdmissionFailure admissionFailure,
                         com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.ProbeDiagnostic probe,
                         Vec3 eye, Vec3 look, net.minecraft.world.phys.AABB targetBounds,
                         net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                           String routeResult, UUID attentionTarget, long attentionUntil, String motorStep) { }

    static DebugSnapshot debugSnapshot(Mob mob) {
        Brain brain = mob == null ? null : BRAINS.get(mob.getUUID());
        return brain == null ? null : new DebugSnapshot(brain.activeTask, brain.idlePoint, brain.idleUntil,
                brain.idleWait, brain.target, List.copyOf(brain.route), brain.lastAdmissionFailureTarget,
                brain.lastAdmissionFailure, brain.lastProbeDiagnostic, brain.lastFailureEye, brain.lastFailureLook,
                brain.lastFailureTargetBounds, brain.lastFailureDimension, brain.lastRouteResult,
                brain.attentionTarget, brain.attentionUntil, brain.lastMotorStep);
    }

    static Vec3 debugFleePoint(Mob mob) {
        Brain brain = mob == null ? null : BRAINS.get(mob.getUUID());
        return brain == null ? null : brain.fleePoint;
    }

    static long debugFleeUntil(Mob mob) {
        Brain brain = mob == null ? null : BRAINS.get(mob.getUUID());
        return brain == null ? 0 : brain.fleeUntil;
    }

    static int rejectedSourceCount(Mob mob) {
        Brain brain = mob == null ? null : BRAINS.get(mob.getUUID());
        return brain == null ? 0 : brain.rejectedUntil.size();
    }

    /** Explicit local post-escape flee adaptation; invoked by the canonical release completion callback. */
    public static boolean requestPostEscapeFlee(Mob mob, Vec3 formerRootPosition) {
        if (mob == null || formerRootPosition == null || !(mob.level() instanceof ServerLevel level)
                || !autonomousEscapeEligible(mob, level, CharacterIdentitySystem.resolveForActor(mob).orElse(null)))
            return false;
        Vec3 away = mob.position().subtract(formerRootPosition);
        away = new Vec3(away.x, 0, away.z);
        if (away.horizontalDistanceSqr() < 1.0e-6) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            away = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        }
        away = away.normalize();
        Vec3 requested = mob.position().add(away.normalize().scale(5));
        Vec3 goal = ROUTER.nearestSupportedPoint(mob, requested, 1.45);
        if (goal == null || goal.distanceTo(mob.position()) < .5) {
            Vec3 side = new Vec3(-away.z, 0, away.x).normalize().scale(4);
            goal = ROUTER.nearestSupportedPoint(mob, mob.position().add(side), 1.45);
        }
        if (goal != null && goal.distanceTo(mob.position()) < .5) goal = null;
        Brain brain = BRAINS.computeIfAbsent(mob.getUUID(), ignored -> new Brain());
        brain.fleeOrigin = formerRootPosition;
        brain.fleePoint = goal;
        brain.fleeUntil = level.getGameTime() + 100;
        brain.target = null;
        brain.idlePoint = null;
        brain.activeTask = null;
        brain.route = List.of();
        brain.fleeFallback = 0;
        brain.fleeArrived = false;
        brain.nextFleeRoute = level.getGameTime();
        Brain previous = BRAINS.get(mob.getUUID());
        if (previous != null && previous.npcIngestionOperationId != 0) {
            long ownedOperation = previous.npcIngestionOperationId;
            com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.cancelMatching(mob,
                    operation -> operation.kind().equals("ingestion") && operation.id() == ownedOperation);
            previous.npcIngestionOperationId = 0;
        }
        brain.nextPlan = brain.fleeUntil;
        return true;
    }

    /** Called once from EntityTickEvent.Pre; clears the motion marker on every failed enrollment. */
    public static void tick(Entity entity, ServerLevel level) {
        if (!(entity instanceof Mob mob) || !(mob instanceof NpcMovementDriven driven)) return;
        var resolved = CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        var policy = resolved == null ? null : resolved.component(NpcBehaviorComponent.class).orElse(null);
        if (policy == null) {
            driven.moonstation14$setNpcMovementDriven(false);
            BRAINS.remove(mob.getUUID());
            return;
        }

        // Vanilla NBT loading resets NoAI to false when the tag is absent. Resolved NPC policy
        // owns native-AI suppression, so restore it on every live tick before eligibility checks.
        mob.setNoAi(true);
        var containmentService = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var containedOwner = containmentService.ownerOf(mob).orElse(null);
        if (containedOwner == null && mob.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.PORTABLE_BODY_CONTAINMENT.get()) != null) {
            // A serialized relation is not custody authority, but it keeps an unresolved body inert
            // until its exact loaded carrier/world root proves the matching live key.
            driven.moonstation14$setNpcMovementDriven(false);
            BRAINS.remove(mob.getUUID());
            return;
        }
        if (containedOwner != null) {
            if (autonomousEscapeEligible(mob, level, resolved)
                    && resolved.component(com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent.class).isPresent())
                containedOwner.tryEscape(mob);
            driven.moonstation14$setNpcMovementDriven(false);
            BRAINS.remove(mob.getUUID());
            return;
        }
        if (!eligible(mob, level, resolved)) {
            driven.moonstation14$setNpcMovementDriven(false);
            BRAINS.remove(mob.getUUID());
            return;
        }

        if (com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(mob)) {
            driven.moonstation14$setNpcMovementDriven(true);
            Brain flying = BRAINS.computeIfAbsent(mob.getUUID(), ignored -> new Brain());
            flying.target = null;
            flying.route = List.of();
            flying.idlePoint = null;
            flying.activeTask = null;
            return;
        }

        Brain brain = BRAINS.computeIfAbsent(mob.getUUID(), ignored -> {
            Brain created = new Brain();
            // Avoid synchronizing every newly spawned NPC's expensive decision tick.
            created.nextPlan = level.getGameTime() + Math.floorMod(mob.getUUID().hashCode(),
                    Math.max(1, policy.planIntervalTicks()));
            created.lastProgressPosition = mob.position();
            created.lastProgressTick = level.getGameTime();
            return created;
        });
        if (yieldToExternalIngestion(mob, brain)) {
            brain.target = null;
            brain.route = List.of();
            driven.moonstation14$setNpcMovementDriven(false);
            return;
        }
        if (ownsActiveIngestion(mob, brain)) {
            holdActiveIngestion(mob, brain, driven, level);
            return;
        }
        long now = level.getGameTime();
        if (brain.fleeUntil > now) {
            brain.target = null;
            brain.idlePoint = null;
            if (!brain.fleeArrived && brain.route.isEmpty() && brain.nextFleeRoute <= now)
                routeFleeGoal(mob, brain, policy, now);
            if (brain.fleePoint != null && mob.position().distanceTo(brain.fleePoint) < .5
                    && ROUTER.hasSupportedSurface(mob, mob.position())) {
                brain.fleePoint = null;
                brain.route = List.of();
                brain.fleeArrived = true;
            }
        } else if (brain.fleeUntil != 0) {
            brain.fleeUntil = 0;
            brain.fleeOrigin = null;
            brain.fleePoint = null;
            brain.nextFleeRoute = 0;
            brain.fleeFallback = 0;
            brain.fleeArrived = false;
            brain.route = List.of();
            brain.nextPlan = Math.min(brain.nextPlan, now);
        }
        if (brain.fleeUntil <= now && now >= brain.nextPlan) {
            brain.nextPlan = now + policy.planIntervalTicks();
            plan(mob, level, resolved, policy, brain, now);
        }

        if (brain.fleeUntil <= now) updateIdleAttention(mob, level, resolved, brain, now);

        // plan may start a world bite; pin and stop locomotion in this same entity tick, rather
        // than letting waypoint steering move the body after the timed operation was registered.
        if (ownsActiveIngestion(mob, brain)) {
            holdActiveIngestion(mob, brain, driven, level);
            return;
        }

        if (brain.idlePoint != null && brain.route.isEmpty()) {
            double dx = mob.getX() - brain.idlePoint.x, dz = mob.getZ() - brain.idlePoint.z;
            if (dx * dx + dz * dz <= .25 && Math.abs(mob.getY() - brain.idlePoint.y) <= .35
                    && mob.onGround() && ROUTER.hasSupportedSurface(mob, mob.position())) {
                mob.setDeltaMovement(Vec3.ZERO);
                brain.idlePoint = null;
                brain.activeTask = null;
                brain.idleUntil = now + brain.idleWait;
            } else {
                // A failed/deferred idle route is not retried every entity tick. Reconsider it
                // on the next bounded decision cadence instead.
                if (brain.nextTargetRoute <= now) {
                    var route = ROUTER.routeResult(mob, brain.idlePoint);
                    brain.lastRouteResult = route.toString();
                    brain.nextTargetRoute = now + policy.planIntervalTicks();
                    if (route.status() == GroundNpcRoutePlanner.RouteStatus.NO_PATH) {
                        brain.idlePoint = null;
                        brain.activeTask = null;
                        brain.route = List.of();
                    } else if (route.status() != GroundNpcRoutePlanner.RouteStatus.DEFERRED)
                        brain.route = route.waypoints();
                }
            }
        }

        Vec3 desired = desiredPoint(mob, level, brain, policy, now);
        int wishX = 0, wishZ = 0;
        boolean jump = false;
        if (desired != null) {
            double dx = desired.x - mob.getX(), dz = desired.z - mob.getZ();
            GroundNpcRoutePlanner.Waypoint waypoint = brain.route.isEmpty() ? null : brain.route.get(0);
            if (waypoint != null) {
                desired = waypoint.position();
                dx = desired.x - mob.getX();
                dz = desired.z - mob.getZ();
                // Route nodes are support-surface heights; a grounded harness body may have up
                // to one block of transient vertical separation while settling from a jump/drop.
                // Pop a horizontally reached intermediate node so gravity can complete the edge.
                if (dx * dx + dz * dz < .16 && Math.abs(desired.y - mob.getY()) <= 1.05) {
                    brain.route = List.copyOf(brain.route.subList(1, brain.route.size()));
                    waypoint = brain.route.isEmpty() ? null : brain.route.get(0);
                    if (waypoint != null) {
                        desired = waypoint.position();
                        dx = desired.x - mob.getX();
                        dz = desired.z - mob.getZ();
                    }
                }
            }
            if (dx * dx + dz * dz > .16) {
                float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                int q = com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor.INPUT_QUANTIZATION;
                // The shared motor accepts local strafe/forward wishes and rotates them by yaw.
                // Since the NPC faces its waypoint, move forward; do not rotate a world-space
                // direction into local input a second time.
                double remaining = Math.hypot(dx, dz);
                wishZ = Math.max(100, (int) Math.round(q * Math.min(1d, remaining / .75)));
                Vec3 proposed = new Vec3(dx, 0, dz).normalize().scale(Math.min(.2, remaining));
                if (waypoint != null && waypoint.y() > mob.getY() + .15) jump = true;
                boolean plannedJump = jump;
                MoveCheck move = safeNext(mob, proposed);
                if (move == MoveCheck.BLOCKED) {
                    jump = plannedJump || canJumpForward(mob, proposed);
                    if (!jump) { wishX = 0; wishZ = 0; }
                } else if (move == MoveCheck.PIT && !jump && mob.onGround() && waypoint == null) {
                    // An explicit route waypoint has already passed loaded support and edge-clear
                    // checks; do not veto its validated one-block descent with this local probe.
                    // The pit guard still brakes unplanned direct steering.
                    wishX = 0; wishZ = 0;
                }
            } else if (brain.idlePoint != null && mob.onGround()
                    && ROUTER.hasSupportedSurface(mob, mob.position())) {
                double idleDx = brain.idlePoint.x - mob.getX(), idleDz = brain.idlePoint.z - mob.getZ();
                if (idleDx * idleDx + idleDz * idleDz <= .25
                        && Math.abs(mob.getY() - brain.idlePoint.y) <= .35) {
                    mob.setDeltaMovement(Vec3.ZERO);
                    brain.idlePoint = null;
                    brain.idleUntil = now + brain.idleWait;
                    brain.activeTask = null;
                    brain.route = List.of();
                }
            }
        }

        // Set only for this call boundary. stepNpc revalidates canonical authority and performs
        // exactly one grounded physics step; no vanilla navigation/move-control is executed.
        driven.moonstation14$setNpcMovementDriven(true);
        brain.lastMotorStep = "position=" + mob.position() + " waypoint="
                + (brain.route.isEmpty() ? null : brain.route.get(0)) + " wish=(" + wishX + "," + wishZ
                + ") jump=" + jump + " onGround=" + mob.onGround() + " velocity=" + mob.getDeltaMovement();
        MovementSpeedModifierComponent movement = resolved.component(MovementSpeedModifierComponent.class)
                .orElseThrow();
        // SS14 NPC steering supplies sprint input for ordinary HTN travel too. Preserve that
        // gait with the resolved absolute prototype sprint rate through the shared motor.
        double desiredSpeed = autonomousSpeedPerSecond(movement);
        MOTOR.stepNpc(mob, wishX, wishZ, jump, true, mob.getYRot(), desiredSpeed, () -> eligible(mob, level,
                CharacterIdentitySystem.resolveForActor(mob).orElse(null)));
        if (brain.target != null || brain.idlePoint != null) {
            Vec3 position = mob.position();
            if (brain.lastProgressPosition == null || position.distanceToSqr(brain.lastProgressPosition) >= .0225) {
                brain.lastProgressPosition = position;
                brain.lastProgressTick = now;
            } else if (now - brain.lastProgressTick >= 20) {
                // Replan after a bounded no-progress interval rather than steering forever on a
                // stale waypoint; the next route attempt remains on the decision cadence.
                brain.route = List.of();
                brain.nextTargetRoute = now + policy.planIntervalTicks();
                brain.lastProgressTick = now;
            }
        }
    }

    static double autonomousSpeedPerSecond(MovementSpeedModifierComponent movement) {
        return movement.sprintSpeed();
    }

    private static void updateIdleAttention(Mob mob, ServerLevel level, CharacterData character, Brain brain, long now) {
        var policy = character.component(com.juicyslew.moonstation14.ms14.character.components.NpcIdleAttentionComponent.class).orElse(null);
        boolean idle = policy != null && brain.target == null
                && brain.idlePoint == null && brain.idleUntil > now && mob.onGround()
                && mob.getDeltaMovement().horizontalDistanceSqr() < .0025;
        if (!idle) { brain.attentionTarget = null; brain.attentionUntil = 0; return; }
        Entity current = brain.attentionTarget == null ? null : level.getEntity(brain.attentionTarget);
        if (current instanceof net.minecraft.server.level.ServerPlayer player && visibleIdleTarget(mob, player, policy.range())
                && now < brain.attentionUntil) {
            face(mob, player.getEyePosition());
            return;
        }
        brain.attentionTarget = null;
        if (now < brain.nextAttentionProbe) return;
        brain.nextAttentionProbe = now + policy.minimumProbeTicks()
                + mob.getRandom().nextInt(policy.maximumProbeTicks() - policy.minimumProbeTicks() + 1);
        var candidates = level.players().stream().filter(player -> visibleIdleTarget(mob, player, policy.range()))
                .sorted(java.util.Comparator.comparingDouble(mob::distanceToSqr)).limit(1).toList();
        if (candidates.isEmpty()) return;
        var target = candidates.get(0);
        brain.attentionTarget = target.getUUID();
        brain.attentionUntil = now + policy.minimumDurationTicks()
                + mob.getRandom().nextInt(policy.maximumDurationTicks() - policy.minimumDurationTicks() + 1);
        face(mob, target.getEyePosition());
    }

    private static boolean visibleIdleTarget(Mob mob, net.minecraft.server.level.ServerPlayer player, float range) {
        if (player.level() != mob.level() || !player.isAlive() || player.isSpectator() || player.isRemoved()
                || player.getCamera() != player || mob.distanceToSqr(player) > range * range
                || !(mob.level() instanceof ServerLevel level)) return false;
        Vec3 from = mob.getEyePosition(), to = player.getEyePosition();
        int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to)));
        for (int index = 0; index <= samples; index++) {
            Vec3 point = from.lerp(to, index / (double) samples);
            if (!level.hasChunkAt(BlockPos.containing(point))) return false;
        }
        return level.clip(new net.minecraft.world.level.ClipContext(from, to,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob))
                .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Removes only transient NPC planning state; timed ingestion owns its own lifecycle and authority. */
    public static void onLeaveLevel(Entity entity) {
        if (!(entity instanceof Mob mob) || !(mob instanceof NpcMovementDriven driven)) return;
        if (!driven.moonstation14$isNpcMovementDriven() && !BRAINS.containsKey(mob.getUUID())) return;
        driven.moonstation14$setNpcMovementDriven(false);
        BRAINS.remove(mob.getUUID());
    }

    private static void plan(Mob mob, ServerLevel level, CharacterData character, NpcBehaviorComponent policy,
                             Brain brain, long now) {
        expireRejectedSources(brain.rejectedUntil, now);
        HungerAttachment hungerState = mob.getExistingDataOrNull(ModDataAttachments.HUNGER.get());
        ThirstAttachment thirstState = mob.getExistingDataOrNull(ModDataAttachments.THIRST.get());
        float hunger = hungerState == null ? Float.MAX_VALUE : hungerState.hunger();
        float thirst = thirstState == null ? Float.MAX_VALUE : thirstState.thirst();
        float peckish = character.component(HungerPrototypeComponent.class)
                .map(HungerPrototypeComponent::peckish).orElse(Float.MIN_VALUE);
        float parched = character.component(ThirstPrototypeComponent.class)
                .map(ThirstPrototypeComponent::parched).orElse(Float.MIN_VALUE);
        // Keep an active source pinned through its transaction; re-running candidate selection
        // while eating can switch targets and repeatedly start/reject the same bite.
        if (ownsActiveIngestion(mob, brain)) return;
        ItemEntity pinned = brain.target == null ? null : level.getEntity(brain.target) instanceof ItemEntity item
                && supported(item) ? item : null;
        boolean stillNeedsPinnedTask = pinned != null && mob.distanceTo(pinned) < policy.visionRange()
                && (brain.activeTask == NpcBehaviorComponent.Task.FOOD
                ? hunger <= peckish : brain.activeTask == NpcBehaviorComponent.Task.DRINK && thirst <= parched);
        List<ItemEntity> items = stillNeedsPinnedTask ? List.of(pinned)
                : level.getEntitiesOfClass(ItemEntity.class,
                        mob.getBoundingBox().inflate(policy.visionRange()), NpcBehaviorSystem::supported).stream()
                .sorted(java.util.Comparator.comparingDouble(mob::distanceTo)).limit(64).toList();
        if (!stillNeedsPinnedTask && brain.target != null) {
            brain.target = null;
            brain.targetPosition = null;
            brain.route = List.of();
        }
        List<NpcTaskPlanner.Candidate> candidates = new ArrayList<>();
        Map<NpcTaskPlanner.Candidate, UUID> sources = new java.util.IdentityHashMap<>();
        for (ItemEntity item : items) {
            if (candidates.size() >= 16) break;
            Long rejectedUntil = brain.rejectedUntil.get(item.getUUID());
            if (rejectedUntil != null) {
                if (now < rejectedUntil) continue;
                brain.rejectedUntil.remove(item.getUUID());
            }
            IngestionProfile profile = profile(item);
            if (profile == null) continue;
            IngestionProfile.Edible edible = profile.edible();
            NpcTaskPlanner.Need kind = edible.kind() == IngestionProfile.Kind.FOOD
                    ? NpcTaskPlanner.Need.FOOD : NpcTaskPlanner.Need.DRINK;
            double distance = mob.distanceTo(item);
            if (kind == NpcTaskPlanner.Need.FOOD && hunger > peckish || kind == NpcTaskPlanner.Need.DRINK && thirst > parched)
                continue;
            double value = nutritionValue(level, item, profile, kind);
            if (kind == NpcTaskPlanner.Need.FOOD ? value <= 0 : value <= 1) continue;
            // Selection uses cheap utility/distance facts. Pathfinding is performed only for
            // the selected candidate below, rather than once per visible food source.
            var candidate = new NpcTaskPlanner.Candidate(kind, distance, value, false, true, true);
            candidates.add(candidate);
            sources.put(candidate, item.getUUID());
        }
        UUID previousTarget = brain.target;
        NpcBehaviorComponent.Task previousTask = brain.activeTask;
        var selection = NpcTaskPlanner.select(policy, hunger, thirst, peckish, parched, candidates).orElse(null);
        if (selection != null && selection.task() == NpcBehaviorComponent.Task.FOOD && previousTarget != null
                && previousTask == NpcBehaviorComponent.Task.FOOD
                && candidates.stream().filter(candidate -> candidate.kind() == NpcTaskPlanner.Need.FOOD)
                .anyMatch(candidate -> previousTarget.equals(sources.get(candidate)))) {
            selection = new NpcTaskPlanner.Selection(NpcBehaviorComponent.Task.FOOD,
                    candidates.stream().filter(candidate -> previousTarget.equals(sources.get(candidate))).findFirst().orElseThrow());
        } else if (selection != null && selection.task() == NpcBehaviorComponent.Task.FOOD) {
            var varied = NpcTaskPlanner.selectNearbyFood(candidates, policy.visionRange(), mob.getRandom().nextDouble());
            if (varied.isPresent()) selection = new NpcTaskPlanner.Selection(NpcBehaviorComponent.Task.FOOD, varied.get());
        }
        List<GroundNpcRoutePlanner.Waypoint> previousRoute = brain.route;
        brain.target = null;
        if (selection == null || selection.target() == null) {
            if (!(selection != null && selection.task() == NpcBehaviorComponent.Task.IDLE
                    && previousTask == NpcBehaviorComponent.Task.IDLE && brain.idlePoint != null)) brain.route = List.of();
        }
        if (selection != null && selection.target() != null) {
            brain.target = sources.get(selection.target());
        }
        if (brain.target != null) {
            brain.activeTask = selection.task();
            if (java.util.Objects.equals(brain.target, previousTarget) && previousTask == brain.activeTask) {
                Vec3 newPosition = level.getEntity(brain.target) == null ? null : level.getEntity(brain.target).position();
                if (newPosition != null && (brain.targetPosition == null
                        || newPosition.distanceToSqr(brain.targetPosition) >= .16
                        || brain.route.isEmpty() && mob.position().distanceTo(newPosition) > policy.mealRange())) {
                    if (mob.position().distanceTo(newPosition) <= policy.mealRange()) brain.route = List.of();
                    else {
                        var route = ROUTER.routeResult(mob, newPosition);
                        applyTargetRouteResult(brain, brain.target, newPosition, route, now);
                    }
                } else brain.route = previousRoute;
            }
            else {
                Entity target = level.getEntity(brain.target);
                brain.targetPosition = target == null ? null : target.position();
                var route = target == null || mob.distanceTo(target) <= policy.mealRange() ? null
                        : ROUTER.routeResult(mob, target.position());
                if (route == null) brain.route = List.of();
                else applyTargetRouteResult(brain, brain.target, target.position(), route, now);
            }
            brain.idlePoint = null;
        }
        if (brain.target == null && selection != null && selection.task() == NpcBehaviorComponent.Task.IDLE
                && policy.tasks().contains(NpcBehaviorComponent.Task.IDLE) && now >= brain.idleUntil
                && brain.idlePoint == null) {
            brain.activeTask = NpcBehaviorComponent.Task.IDLE;
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double distance = 2 + Math.sqrt(mob.getRandom().nextDouble()) * Math.max(0, policy.idleRange() - 3);
            Vec3 requested = new Vec3(mob.getX() + Math.cos(angle) * distance, mob.getY(),
                    mob.getZ() + Math.sin(angle) * distance);
            Vec3 point = ROUTER.nearestSupportedPoint(mob, requested, 1.45);
            if (point != null && mob.position().distanceTo(point) <= policy.idleRange()) {
                brain.idlePoint = point;
                var route = ROUTER.routeResult(mob, point);
                brain.lastRouteResult = route.toString();
                if (route.status() == GroundNpcRoutePlanner.RouteStatus.NO_PATH
                        || route.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE && route.waypoints().isEmpty()) {
                    brain.idlePoint = null;
                    brain.activeTask = null;
                    brain.route = List.of();
                } else brain.route = route.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED
                        ? List.of() : route.waypoints();
                brain.nextTargetRoute = now + policy.planIntervalTicks();
            }
            if (brain.idlePoint == null) brain.activeTask = null;
            brain.idleWait = policy.idleMinimumTicks() + mob.getRandom().nextInt(Math.max(1,
                    policy.idleMaximumTicks() - policy.idleMinimumTicks() + 1));
        }
        if (brain.target != null) {
            Entity target = level.getEntity(brain.target);
            if (target instanceof ItemEntity item && mob.distanceTo(item) <= policy.mealRange()) {
                face(mob, item.getBoundingBox().getCenter());
                UUID targetId = item.getUUID();
                var admission = com.juicyslew.moonstation14.ms14.ingestion.TimedIngestionAccess.diagnoseWorldAdmission(mob,
                        targetId, null, 0, () -> eligible(mob, level,
                                CharacterIdentitySystem.resolveForActor(mob).orElse(null)));
                if (admission.admission().isEmpty()) {
                    if (brain.lastAdmissionFailure != admission.failure()
                            || !targetId.equals(brain.lastAdmissionFailureTarget)) {
                        brain.lastAdmissionFailureTarget = targetId;
                        brain.lastAdmissionFailure = admission.failure();
                        brain.lastProbeDiagnostic = admission.probe();
                        brain.lastFailureEye = mob.getEyePosition();
                        brain.lastFailureLook = mob.getLookAngle();
                        brain.lastFailureTargetBounds = item.getBoundingBox();
                        brain.lastFailureDimension = level.dimension();
                        LOGGER.info("NPC world-ingestion admission failed: actor={} source={} dimension={} reason={} "+
                                        "eye={} look={} targetBounds={} probe={}", mob.getUUID(), targetId,
                                level.dimension().location(), admission.failure(), brain.lastFailureEye,
                                brain.lastFailureLook, brain.lastFailureTargetBounds, admission.probe());
                    }
                    rejectSource(brain.rejectedUntil, targetId, now + 40);
                    brain.target = null;
                    brain.activeTask = null;
                    brain.route = List.of();
                    return;
                }
                var active = mob.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
                var ownRunning = active == null ? null : active.operations().stream().filter(operation ->
                        operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE
                                && operation.id() == brain.npcIngestionOperationId
                                && operation.kind().equals("ingestion") && operation.sourceKey().equals("world:" + targetId))
                        .findFirst().orElse(null);
                if (ownRunning == null) {
                    BodyTimedIngestionService.startWorldOwned(mob, targetId, null, 0, now,
                        () -> eligible(mob, level, CharacterIdentitySystem.resolveForActor(mob).orElse(null)));
                    var afterStart = mob.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
                    if (afterStart != null) brain.npcIngestionOperationId = afterStart.operations().stream()
                            .filter(operation -> operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE
                                    && operation.kind().equals("ingestion")
                                    && operation.sourceKey().equals("world:" + targetId))
                            .mapToLong(com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation::id)
                            .findFirst().orElse(0L);
                }
            }
        }
    }

    /** Shared handling for new and pinned-source replans: deferred is not a path failure. */
    private static void applyTargetRouteResult(Brain brain, UUID targetId, Vec3 targetPosition,
                                               GroundNpcRoutePlanner.RouteResult route, long now) {
        brain.lastRouteResult = route.toString();
        if (route.status() == GroundNpcRoutePlanner.RouteStatus.NO_PATH) {
            rejectSource(brain.rejectedUntil, targetId, now + 60);
            brain.target = null;
            brain.targetPosition = null;
            brain.activeTask = null;
            brain.route = List.of();
            return;
        }
        brain.targetPosition = targetPosition;
        brain.route = route.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE
                ? route.waypoints() : List.of();
    }

    static void expireRejectedSources(Map<UUID, Long> rejectedUntil, long now) {
        rejectedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    static void rejectSource(Map<UUID, Long> rejectedUntilBySource, UUID source, long rejectedUntil) {
        if (source == null) return;
        if (!rejectedUntilBySource.containsKey(source)
                && rejectedUntilBySource.size() >= MAX_REJECTED_SOURCES_PER_BRAIN) {
            UUID earliest = null;
            long earliestExpiry = Long.MAX_VALUE;
            for (var entry : rejectedUntilBySource.entrySet()) {
                if (entry.getValue() < earliestExpiry) {
                    earliest = entry.getKey();
                    earliestExpiry = entry.getValue();
                }
            }
            if (earliest != null) rejectedUntilBySource.remove(earliest);
        }
        rejectedUntilBySource.put(source, rejectedUntil);
    }

    private static Vec3 desiredPoint(Mob mob, ServerLevel level, Brain brain, NpcBehaviorComponent policy, long now) {
        if (brain.target != null) {
            Entity target = level.getEntity(brain.target);
            if (target instanceof ItemEntity item && supported(item)) {
                if (mob.distanceTo(item) <= policy.mealRange()) return item.position();
            }
            if (!(target instanceof ItemEntity item) || !supported(item)) brain.target = null;
        }
        if (!brain.route.isEmpty()) {
            return brain.route.get(0).position();
        }
        if (brain.idlePoint != null && brain.route.isEmpty()) return null;
        return null;
    }

    private static boolean eligible(Mob mob, ServerLevel level, CharacterData character) {
        return autonomousEscapeEligible(mob, level, character)
                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(mob).isEmpty();
    }

    /** Same player/mind/offline ownership gates as normal NPC behavior, omitting containment only. */
    private static boolean autonomousEscapeEligible(Mob mob, ServerLevel level, CharacterData character) {
        if (character == null || mob.isRemoved() || !mob.isAlive() || mob.isDeadOrDying() || !mob.isNoAi()
                || !mob.isAddedToLevel() || mob.level() != level || level.getEntity(mob.getUUID()) != mob
                || mob.isPassenger() || mob instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()
                || !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(mob)
                ) return false;
        if (character.component(NpcBehaviorComponent.class).isEmpty()) return false;
        var binding = PlayerCharacterHarnessBinder.binding(mob);
        return !LifecycleStartupRuntime.isExactOfflineOwnerBody(mob, binding);
    }

    private static boolean supported(ItemEntity item) {
        return item != null && item instanceof com.juicyslew.moonstation14.entities.LooseItemEntity
                && !item.isRemoved() && item.getTarget() == null && !item.hasPickUpDelay()
                && profile(item) != null && hasPositiveContents(item, profile(item));
    }

    private static IngestionProfile profile(ItemEntity item) {
        if (item == null || item.getItem().isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
        IngestionProfile profile = ModIngestionProfiles.serverLookup(id);
        return profile;
    }

    private static boolean hasPositiveContents(ItemEntity item, IngestionProfile profile) {
        var contents = item.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
        if (contents != null) return contents.centContents().values().stream().anyMatch(value -> value > 0);
        var source = profile.edible().sourceSolution();
        return source != null && source.contentsCents().values().stream().anyMatch(value -> value > 0);
    }

    static boolean debugSupportedItem(ItemEntity item) { return supported(item); }
    static double debugItemValue(ServerLevel level, ItemEntity item) {
        IngestionProfile profile = profile(item);
        return profile == null ? 0 : nutritionValue(level, item, profile,
                profile.edible().kind() == IngestionProfile.Kind.FOOD ? NpcTaskPlanner.Need.FOOD : NpcTaskPlanner.Need.DRINK);
    }

    private static double nutritionValue(ServerLevel level, ItemEntity item, IngestionProfile profile,
                                         NpcTaskPlanner.Need kind) {
        Map<ResourceLocation, Long> contents = new HashMap<>();
        var live = item == null ? null : item.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
        if (live != null) live.centContents().forEach((key, amount) -> contents.put(key.location(), amount));
        if (live == null && profile.edible().sourceSolution() != null)
            contents.putAll(profile.edible().sourceSolution().contentsCents());
        double total = 0;
        var catalog = ModReagents.catalog(level);
        for (var entry : contents.entrySet()) {
            if (entry.getValue() <= 0) continue;
            ReagentData reagent = catalog.get(entry.getKey());
            if (reagent == null) continue;
            double volume = entry.getValue() / 100d;
            for (var metabolism : reagent.metabolisms().values()) {
                for (EffectData effect : metabolism.effects()) {
                    if (kind == NpcTaskPlanner.Need.FOOD && effect instanceof EffectData.SatiateHunger hunger)
                        total += volume * Math.max(0, hunger.factor());
                    else if (kind == NpcTaskPlanner.Need.DRINK && effect instanceof EffectData.SatiateThirst thirst)
                        total += volume * Math.max(0, thirst.factor());
                }
            }
        }
        // SS14 food utility is nonzero nutrition; drink utility requires hydration greater than 1.
        return kind == NpcTaskPlanner.Need.DRINK ? total : (total > 0 ? 1d : 0d);
    }

    private enum MoveCheck { CLEAR, BLOCKED, PIT }

    /** One bounded route attempt per existing NPC plan cadence; DEFERRED retains the same goal. */
    private static void routeFleeGoal(Mob mob, Brain brain, NpcBehaviorComponent policy, long now) {
        if (brain.fleeFallback >= 4) {
            brain.nextFleeRoute = now + policy.planIntervalTicks();
            brain.fleeFallback = 0;
        }
        if (brain.fleePoint == null) {
            Vec3 away = mob.position().subtract(brain.fleeOrigin);
            away = new Vec3(away.x, 0, away.z);
            if (away.horizontalDistanceSqr() < 1.0e-6)
                away = new Vec3(Math.cos(mob.getYRot() * Math.PI / 180d), 0,
                        Math.sin(mob.getYRot() * Math.PI / 180d));
            away = away.normalize();
            Vec3 perpendicular = new Vec3(-away.z, 0, away.x);
            Vec3 direction = switch (brain.fleeFallback) {
                case 1 -> perpendicular;
                case 2 -> perpendicular.scale(-1);
                case 3 -> away.scale(-1);
                default -> away;
            };
            brain.fleePoint = ROUTER.nearestSupportedPoint(mob, mob.position().add(direction.scale(4)), 1.45);
            if (brain.fleePoint == null || brain.fleePoint.distanceTo(mob.position()) < .5) {
                brain.fleePoint = null;
                brain.fleeFallback++;
                brain.nextFleeRoute = now + policy.planIntervalTicks();
                return;
            }
        }
        var result = ROUTER.routeResult(mob, brain.fleePoint);
        brain.lastRouteResult = result.toString();
        brain.nextFleeRoute = now + policy.planIntervalTicks();
        if (result.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE) {
            brain.route = result.waypoints();
            if (brain.route.isEmpty()) {
                brain.fleePoint = null;
                brain.fleeArrived = true;
            }
        } else if (result.status() == GroundNpcRoutePlanner.RouteStatus.NO_PATH) {
            brain.fleePoint = null;
            brain.fleeFallback++;
        }
        // DEFERRED deliberately retains goal/fallback index and retries on the next plan cadence.
    }

    private static MoveCheck safeNext(Mob mob, Vec3 delta) {
        if (!(mob.level() instanceof ServerLevel level)) return MoveCheck.BLOCKED;
        Vec3 next = mob.position().add(delta);
        BlockPos feet = BlockPos.containing(next.x, next.y, next.z);
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) return MoveCheck.BLOCKED;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) return MoveCheck.BLOCKED;
        if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && !ROUTER.hasSupportedSurface(mob, next)) return MoveCheck.PIT;
        AABB box = mob.getBoundingBox().move(delta);
        if (!level.noCollision(mob, box)) return MoveCheck.BLOCKED;
        return MoveCheck.CLEAR;
    }

    private static boolean canJumpForward(Mob mob, Vec3 delta) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.onGround()) return false;
        Vec3 next = mob.position().add(delta);
        BlockPos feet = BlockPos.containing(next.x, next.y, next.z);
        if (!level.hasChunkAt(feet.above()) || !level.hasChunkAt(feet.above(2))) return false;
        if (!level.getBlockState(feet).isFaceSturdy(level, feet, Direction.UP)) return false;
        return level.noCollision(mob, mob.getBoundingBox().move(delta).move(0, 1, 0));
    }

    private static boolean loadedSupportedTarget(ServerLevel level, UUID targetId) {
        Entity entity = level.getEntity(targetId);
        return entity instanceof ItemEntity item && supported(item);
    }

    private static void holdActiveIngestion(Mob mob, Brain brain, NpcMovementDriven driven, ServerLevel level) {
        Entity target = brain.target == null ? null : level.getEntity(brain.target);
        if (target instanceof ItemEntity item) face(mob, item.getBoundingBox().getCenter());
        driven.moonstation14$setNpcMovementDriven(true);
        MOTOR.stepNpc(mob, 0, 0, false, true, mob.getYRot(), () -> eligible(mob, level,
                CharacterIdentitySystem.resolveForActor(mob).orElse(null)));
    }

    private static void face(Mob mob, Vec3 target) {
        double dx = target.x - mob.getX(), dz = target.z - mob.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        double horizontal = Math.hypot(dx, dz);
        mob.setXRot((float) Math.toDegrees(Math.atan2(mob.getEyeY() - target.y, horizontal)));
    }

    private static double polylineLength(Vec3 start, List<GroundNpcRoutePlanner.Waypoint> route) {
        double length = 0;
        Vec3 previous = start;
        for (var waypoint : route) {
            Vec3 next = waypoint.position();
            length += previous.distanceTo(next);
            previous = next;
        }
        return length;
    }

    private static boolean yieldToExternalIngestion(Mob mob, Brain brain) {
        var state = mob.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        if (state == null) {
            brain.npcIngestionOperationId = 0;
            return false;
        }
        boolean ownsActive = state.operations().stream().anyMatch(operation ->
                operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE
                        && operation.kind().equals("ingestion") && operation.id() == brain.npcIngestionOperationId);
        if (!ownsActive) brain.npcIngestionOperationId = 0;
        return state.operations().stream().anyMatch(operation ->
                operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE
                        && operation.kind().equals("ingestion") && operation.id() != brain.npcIngestionOperationId);
    }

    private static boolean ownsActiveIngestion(Mob mob, Brain brain) {
        var state = mob.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        return state != null && brain.npcIngestionOperationId != 0 && state.operations().stream().anyMatch(operation ->
                operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE
                        && operation.kind().equals("ingestion") && operation.id() == brain.npcIngestionOperationId);
    }
}
