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
    private static final AirNpcRoutePlanner AIR_ROUTER = new AirNpcRoutePlanner();
    private NpcBehaviorSystem() { }

    static final class Brain {
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
        NpcFlightController.State flightPhase;
        double verticalWish;
        double cruiseY = Double.NaN;
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
        BlockPos cleanTarget;
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
    public record DebugSnapshot(NpcBehaviorComponent.Task task, Vec3 idlePoint, long idleUntil, int idleWait,
                         UUID itemTarget, List<GroundNpcRoutePlanner.Waypoint> route, UUID failedTarget,
                         com.juicyslew.moonstation14.ms14.ingestion.TimedIngestionAccess.WorldAdmissionFailure admissionFailure,
                         com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.ProbeDiagnostic probe,
                         Vec3 eye, Vec3 look, net.minecraft.world.phys.AABB targetBounds,
                         net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                            String routeResult, UUID attentionTarget, long attentionUntil, String motorStep,
                          NpcFlightController.Phase flightPhase, Vec3 phaseGoal, long phaseStarted,
                          long phaseDeadline, double cruiseY) {
        public String flightPhaseName() { return flightPhase == null ? null : flightPhase.name(); }
    }

    public static DebugSnapshot debugSnapshot(Mob mob) {
        Brain brain = mob == null ? null : BRAINS.get(mob.getUUID());
        return brain == null ? null : new DebugSnapshot(brain.activeTask, brain.idlePoint, brain.idleUntil,
                brain.idleWait, brain.target, List.copyOf(brain.route), brain.lastAdmissionFailureTarget,
                brain.lastAdmissionFailure, brain.lastProbeDiagnostic, brain.lastFailureEye, brain.lastFailureLook,
                brain.lastFailureTargetBounds, brain.lastFailureDimension, brain.lastRouteResult,
                brain.attentionTarget, brain.attentionUntil, brain.lastMotorStep,
                brain.flightPhase == null ? null : brain.flightPhase.phase(),
                brain.flightPhase == null ? null : brain.flightPhase.goal(),
                brain.flightPhase == null ? 0 : brain.flightPhase.phaseStarted(),
                brain.flightPhase == null ? 0 : brain.flightPhase.phaseStarted() + brain.flightPhase.phaseDuration(),
                brain.cruiseY);
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
        com.juicyslew.moonstation14.ms14.animals.AbsorbentCleaningService.tick(mob, level.getGameTime());

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
            // External ballistic motion owns both steering and flight intent. Never let an
            // interrupted ground/landing phase leak into the natural-flight handoff.
            flying.flightPhase = null;
            flying.cruiseY = Double.NaN;
            flying.verticalWish = 0;
            flying.nextTargetRoute = 0;
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
        var flight = resolved.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElse(null);
        if (flight != null) {
            var flightState = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(mob);
            if (brain.flightPhase == null) {
                if (flightState == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.FLYING) {
                    // Recovery begins a fresh autonomous leg anchored to its actual position;
                    // subsequent destinations then share this stable cruise reference.
                    brain.idleUntil = now;
                    brain.cruiseY = Math.max(level.getMinBuildHeight() + 1, mob.getY());
                    brain.flightPhase = new NpcFlightController.State(NpcFlightController.Phase.AIR_TRAVEL, now,
                            NpcFlightController.duration(flight.npcAirborneTicks(), mob.getRandom().nextDouble()), null);
                } else brain.flightPhase = flightState == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED
                    ? NpcFlightController.State.grounded(now, NpcFlightController.duration(flight.npcGroundedTicks(), mob.getRandom().nextDouble()))
                    : new NpcFlightController.State(flightState == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.LANDING
                    ? NpcFlightController.Phase.LANDING : NpcFlightController.Phase.AIR_TRAVEL, now,
                    NpcFlightController.duration(flight.npcAirborneTicks(), mob.getRandom().nextDouble()), null);
            }
            if (flightState == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.LANDING
                    && brain.flightPhase.phase() != NpcFlightController.Phase.LANDING) {
                Vec3 landing = ROUTER.nearestSupportedPoint(mob,
                        new Vec3(mob.getX(), mob.getY() - flight.npcCruiseHeight(), mob.getZ()), 8);
                brain.flightPhase = brain.flightPhase.transition(NpcFlightController.Phase.LANDING, now, 0, landing);
                brain.idlePoint = landing;
                brain.route = List.of();
            }
            if (flightState == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.LANDING
                    && brain.flightPhase.phase() == NpcFlightController.Phase.LANDING
                    && brain.flightPhase.goal() == null) {
                Vec3 landing = ROUTER.nearestSupportedPoint(mob,
                        new Vec3(mob.getX(), mob.getY() - flight.npcCruiseHeight(), mob.getZ()), 8);
                if (landing != null) {
                    brain.flightPhase = brain.flightPhase.transition(NpcFlightController.Phase.LANDING, now, 0, landing);
                    brain.idlePoint = landing;
                }
            }
            if (flightState == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED) {
                if (brain.flightPhase.phase() != NpcFlightController.Phase.GROUND_WANDER
                        && brain.flightPhase.phase() != NpcFlightController.Phase.TAKEOFF)
                    brain.flightPhase = NpcFlightController.State.grounded(now,
                            NpcFlightController.duration(flight.npcGroundedTicks(), mob.getRandom().nextDouble()));
                if (brain.flightPhase.phase() == NpcFlightController.Phase.GROUND_WANDER
                        && NpcFlightController.expired(brain.flightPhase, now)) {
                    double preference = cruisePreference(flight.npcCruiseHeight(), mob.getRandom().nextDouble());
                    brain.cruiseY = safeCruiseY(mob, preference);
                    brain.flightPhase = brain.flightPhase.transition(NpcFlightController.Phase.TAKEOFF, now, 0,
                            new Vec3(mob.getX(), brain.cruiseY, mob.getZ()));
                    brain.route = List.of(); brain.idlePoint = brain.flightPhase.goal(); brain.nextTargetRoute = now;
                    com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.requestTakeoff(mob);
                }
            } else if (brain.flightPhase.phase() == NpcFlightController.Phase.TAKEOFF
                    && NpcFlightController.takeoffCleared(mob.getY(), brain.flightPhase.goal().y())) {
                brain.flightPhase = brain.flightPhase.transition(NpcFlightController.Phase.AIR_TRAVEL, now,
                        NpcFlightController.duration(flight.npcAirborneTicks(), mob.getRandom().nextDouble()), null);
            } else if (brain.flightPhase.phase() == NpcFlightController.Phase.AIR_TRAVEL
                    && NpcFlightController.expired(brain.flightPhase, now)
                    && brain.target == null) {
                Vec3 landing = ROUTER.nearestSupportedPoint(mob,
                        new Vec3(mob.getX(), mob.getY() - flight.npcCruiseHeight(), mob.getZ()), 8);
                if (landing != null) {
                    Vec3 approach = landing.add(0, Math.max(1.5, flight.npcCruiseHeight()), 0);
                    var route = AIR_ROUTER.routeResult(mob, approach);
                    if (route.status() == AirNpcRoutePlanner.RouteStatus.ROUTE) {
                        brain.flightPhase = brain.flightPhase.transition(NpcFlightController.Phase.LAND_APPROACH, now,
                                0, approach);
                        brain.idlePoint = approach;
                        brain.route = route.waypoints();
                    } else if (ROUTER.hasSupportedSurface(mob, mob.position())) {
                        brain.flightPhase = brain.flightPhase.transition(NpcFlightController.Phase.LANDING, now, 0, landing);
                        brain.idlePoint = landing;
                    }
                } else {
                    // Keep retrying only on the ordinary decision cadence.
                    brain.flightPhase = brain.flightPhase.transition(NpcFlightController.Phase.AIR_TRAVEL,
                            now, policy.planIntervalTicks(), null);
                }
            }
            if (brain.flightPhase.phase() == NpcFlightController.Phase.LANDING
                    && flightState == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED) {
                brain.flightPhase = NpcFlightController.State.grounded(now,
                        NpcFlightController.duration(flight.npcGroundedTicks(), mob.getRandom().nextDouble()));
                brain.idlePoint = null;
            }
        }
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
        boolean committedFlightManeuver = flight != null && brain.flightPhase != null
                && NpcFlightController.protectsCommittedManeuver(brain.flightPhase.phase());
        if (brain.fleeUntil <= now && !committedFlightManeuver && now >= brain.nextPlan) {
            brain.nextPlan = now + policy.planIntervalTicks();
            plan(mob, level, resolved, policy, brain, now);
        }

        // Only a resolved retaliation component grants a provoked target. Passive NPCs
        // retain ordinary food/drink/idle behavior after taking damage.
        var provoked = com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.target(mob, now);
        if (provoked != null && brain.fleeUntil <= now) {
            brain.target = provoked.getUUID();
            brain.targetPosition = provoked.position();
            brain.idlePoint = null;
            brain.activeTask = NpcBehaviorComponent.Task.RETALIATE;
            var melee = resolved.component(com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent.class).orElse(null);
            double attackRange = melee == null ? 1.6 : melee.range();
            if (com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.withinReach(mob, provoked, attackRange)) {
                brain.route = List.of();
                if (mob.getSensing().hasLineOfSight(provoked)) {
                    mob.setTarget(provoked);
                    com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob, provoked, now);
                }
            } else if (now >= brain.nextTargetRoute) {
                var route = ROUTER.routeResult(mob, provoked.position());
                applyTargetRouteResult(brain, provoked.getUUID(), provoked.position(), route, now);
                brain.nextTargetRoute = now + policy.planIntervalTicks();
            }
        } else if (brain.activeTask == NpcBehaviorComponent.Task.RETALIATE) {
            brain.target = null;
            brain.activeTask = null;
            brain.route = List.of();
            mob.setTarget(null);
        }

        if (brain.fleeUntil <= now) updateIdleAttention(mob, level, resolved, brain, now);

        // plan may start a world bite; pin and stop locomotion in this same entity tick, rather
        // than letting waypoint steering move the body after the timed operation was registered.
        if (ownsActiveIngestion(mob, brain)) {
            holdActiveIngestion(mob, brain, driven, level);
            return;
        }

        if (brain.flightPhase != null && (brain.flightPhase.phase() == NpcFlightController.Phase.AIR_TRAVEL
                || brain.flightPhase.phase() == NpcFlightController.Phase.LAND_APPROACH)
                && brain.flightPhase.goal() != null && brain.idlePoint != null
                && NpcFlightController.nearLandingApproach(mob.position(), brain.idlePoint)) {
            Vec3 landing = brain.flightPhase.phase() == NpcFlightController.Phase.LAND_APPROACH
                    ? ROUTER.nearestSupportedPoint(mob, new Vec3(mob.getX(), mob.getY()-flight.npcCruiseHeight(), mob.getZ()), 8)
                    : brain.flightPhase.goal();
            if (landing != null) {
                brain.flightPhase = brain.flightPhase.transition(NpcFlightController.Phase.LANDING, now, 0, landing);
                brain.idlePoint = landing; brain.route = List.of();
            }
        }
        if (brain.idlePoint != null && brain.route.isEmpty()) {
            double dx = mob.getX() - brain.idlePoint.x, dz = mob.getZ() - brain.idlePoint.z;
            var phase = brain.flightPhase == null ? null : brain.flightPhase.phase();
            double arrivalY = flight != null && phase == NpcFlightController.Phase.TAKEOFF
                    ? brain.flightPhase.goal().y() : brain.idlePoint.y;
            if (dx * dx + dz * dz <= .36 && Math.abs(mob.getY() - arrivalY) <= .4
                    && (flight != null || mob.onGround() && ROUTER.hasSupportedSurface(mob, mob.position()))) {
                if (flight != null && phase == NpcFlightController.Phase.GROUND_WANDER) {
                    completeIdleLeg(brain, now);
                } else if (flight != null && phase == NpcFlightController.Phase.AIR_TRAVEL) {
                    brain.idlePoint = randomAirDestination(mob, flight, level.getMinBuildHeight());
                    brain.nextTargetRoute = now;
                } else if (flight == null) completeIdleLeg(brain, now);
            } else {
                // A failed/deferred idle route is not retried every entity tick. Reconsider it
                // on the next bounded decision cadence instead.
                if (brain.nextTargetRoute <= now) {
                    brain.nextTargetRoute = now + policy.planIntervalTicks();
                    if (flight != null && (phase == NpcFlightController.Phase.TAKEOFF
                            || phase == NpcFlightController.Phase.LANDING)) {
                        // These phases own direct vertical/contact targets, not navigable waypoints.
                    } else if (flight != null && (phase == NpcFlightController.Phase.AIR_TRAVEL
                            || phase == NpcFlightController.Phase.LAND_APPROACH)) {
                        AirNpcRoutePlanner.AirRouteResult airRoute = AIR_ROUTER.routeResult(mob, brain.idlePoint);
                        brain.lastRouteResult = airRoute.toString();
                        if (airRoute.status() == AirNpcRoutePlanner.RouteStatus.NO_PATH) {
                            if (phase == NpcFlightController.Phase.AIR_TRAVEL) {
                                brain.idlePoint = randomAirDestination(mob, flight, level.getMinBuildHeight());
                                brain.route = List.of();
                            }
                        } else if (airRoute.status() != AirNpcRoutePlanner.RouteStatus.DEFERRED)
                            brain.route = airRoute.waypoints();
                    } else {
                        GroundNpcRoutePlanner.RouteResult groundRoute = ROUTER.routeResult(mob, brain.idlePoint);
                        brain.lastRouteResult = groundRoute.toString();
                        if (groundRoute.status() == GroundNpcRoutePlanner.RouteStatus.NO_PATH) {
                            brain.idlePoint = null;
                            brain.activeTask = null;
                            brain.route = List.of();
                        } else if (groundRoute.status() != GroundNpcRoutePlanner.RouteStatus.DEFERRED)
                            brain.route = groundRoute.waypoints();
                    }
                }
            }
        }

        Vec3 desired = desiredPoint(mob, level, brain, policy, now);
        int wishX = 0, wishZ = 0;
        boolean jump = false;
        float motorYaw = mob.getYRot();
        double verticalIntent = Double.NaN;
        boolean phaseLanding = brain.flightPhase != null
                && NpcFlightController.phaseLanding(brain.flightPhase.phase());
        boolean naturalAirborne = flight != null
                && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.isFlying(mob)
                && (brain.flightPhase == null || brain.flightPhase.phase() == NpcFlightController.Phase.AIR_TRAVEL
                || brain.flightPhase.phase() == NpcFlightController.Phase.LAND_APPROACH);
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
                if (flight != null ? desired.distanceTo(mob.position()) < .55
                        : dx * dx + dz * dz < .16 && Math.abs(desired.y - mob.getY()) <= 1.05) {
                    brain.route = List.copyOf(brain.route.subList(1, brain.route.size()));
                    waypoint = brain.route.isEmpty() ? null : brain.route.get(0);
                    if (waypoint != null) {
                        desired = waypoint.position();
                        dx = desired.x - mob.getX();
                        dz = desired.z - mob.getZ();
                    }
                }
            }
            if (flight != null) {
                int q = com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor.INPUT_QUANTIZATION;
                var flightPolicy = com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.fromCharacterData(resolved);
                Vec3 horizontalWish = flightHorizontalWish(dx, dz,
                        flightPolicy.sprintSpeedPerSecond(), q, naturalAirborne && brain.route.size() > 1);
                FlightCommand command = flightCommand(horizontalWish, q);
                if (command.forward() != 0) {
                    motorYaw = command.yaw();
                    float facingYaw = flightPresentationYaw(mob.getDeltaMovement().x, mob.getDeltaMovement().z,
                            dx, dz, mob.getYRot());
                    mob.setYRot(facingYaw);
                    mob.setYBodyRot(facingYaw);
                    mob.setYHeadRot(facingYaw);
                    wishZ = command.forward();
                }
            } else if (dx * dx + dz * dz > .16) {
                float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
                motorYaw = yaw;
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                int q = com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor.INPUT_QUANTIZATION;
                // The shared motor accepts local strafe/forward wishes and rotates them by yaw.
                // Since the NPC faces its waypoint, move forward; do not rotate a world-space
                // direction into local input a second time.
                double remaining = Math.hypot(dx, dz);
                wishZ = (int) Math.round(q * Math.min(1d, remaining / 1.25));
                Vec3 proposed = new Vec3(dx, 0, dz).normalize().scale(Math.min(.2, remaining));
                if (waypoint != null && waypoint.y() > mob.getY() + .15 && flight == null) jump = true;
                boolean plannedJump = jump;
                MoveCheck move = safeNext(mob, proposed, flight != null);
                if (move == MoveCheck.BLOCKED) {
                    jump = plannedJump || canJumpForward(mob, proposed);
                    if (!jump) { wishX = 0; wishZ = 0; }
                } else if (move == MoveCheck.PIT && !jump && mob.onGround() && waypoint == null) {
                    // An explicit route waypoint has already passed loaded support and edge-clear
                    // checks; do not veto its validated one-block descent with this local probe.
                    // The pit guard still brakes unplanned direct steering.
                    wishX = 0; wishZ = 0;
                }
            } else if ((flight == null || brain.flightPhase == null
                    || brain.flightPhase.phase() == NpcFlightController.Phase.GROUND_WANDER)
                    && brain.idlePoint != null && mob.onGround()
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

        if (flight != null && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.isFlying(mob)) {
            double targetY = brain.flightPhase != null && brain.flightPhase.phase() == NpcFlightController.Phase.LANDING
                    && brain.flightPhase.goal() != null ? brain.flightPhase.goal().y() : brain.target != null
                    ? Math.min(mob.getY(), desired == null ? mob.getY() : desired.y)
                    : brain.idlePoint != null ? (desired == null ? brain.idlePoint.y : desired.y)
                    : desired == null ? mob.getY() : desired.y + flight.npcCruiseHeight();
            double desiredVertical = NpcFlightController.verticalWish(mob.getY(), targetY, flight.npcVerticalSpeedMultiplier());
            brain.verticalWish = NpcFlightController.easeVerticalWish(brain.verticalWish, desiredVertical, .12);
            verticalIntent = brain.verticalWish;
            if (phaseLanding) com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.requestLanding(mob);
        }

        // Set only for this call boundary. stepNpc revalidates canonical authority and performs
        // exactly one grounded physics step; no vanilla navigation/move-control is executed.
        driven.moonstation14$setNpcMovementDriven(true);
        brain.lastMotorStep = "position=" + mob.position() + " waypoint="
                + (brain.route.isEmpty() ? null : brain.route.get(0)) + " wish=(" + wishX + "," + wishZ
                + ") jump=" + jump + " vertical=" + verticalIntent + " onGround=" + mob.onGround() + " velocity=" + mob.getDeltaMovement();
        MovementSpeedModifierComponent movement = resolved.component(MovementSpeedModifierComponent.class)
                .orElseThrow();
        // SS14 NPC steering supplies sprint input for ordinary HTN travel too. Preserve that
        // gait with the resolved absolute prototype sprint rate through the shared motor.
        double desiredSpeed = flight != null && naturalAirborne
                ? com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.fromCharacterData(resolved)
                        .sprintSpeedPerSecond()
                : autonomousSpeedPerSecond(movement);
        java.util.function.BooleanSupplier authority = () -> eligible(mob, level,
                CharacterIdentitySystem.resolveForActor(mob).orElse(null));
        if (Double.isFinite(verticalIntent) && flight != null)
            MOTOR.stepNpcAutonomousFlight(mob, wishX, wishZ, jump, true, motorYaw, desiredSpeed,
                    verticalIntent,
                    phaseLanding ? com.juicyslew.moonstation14.ms14.animals.flight.FlightState.LANDING
                            : com.juicyslew.moonstation14.ms14.animals.flight.FlightState.FLYING, authority);
        else if (Double.isFinite(verticalIntent))
            MOTOR.stepNpc(mob, wishX, wishZ, jump, true, motorYaw, desiredSpeed, verticalIntent, authority);
        else
            MOTOR.stepNpc(mob, wishX, wishZ, jump, true, motorYaw, desiredSpeed, authority);
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

    /** Finishes a roaming leg without discarding the altitude owned by the flight task. */
    static void completeIdleLeg(Brain brain, long now) {
        brain.idlePoint = null;
        brain.activeTask = null;
        brain.idleUntil = now + brain.idleWait;
    }

    static double autonomousSpeedPerSecond(MovementSpeedModifierComponent movement) {
        return movement.sprintSpeed();
    }

    /** World-space desired velocity input. The shared motor owns acceleration and friction. */
    static Vec3 flightHorizontalWish(double dx, double dz, double desiredSpeed, int quantization) {
        return flightHorizontalWish(dx, dz, desiredSpeed, quantization, false);
    }

    static Vec3 flightHorizontalWish(double dx, double dz, double desiredSpeed, int quantization,
                                     boolean intermediateAirRouteNode) {
        double distance = Math.hypot(dx, dz);
        if (!(distance > 1e-9) || !Double.isFinite(distance) || !(desiredSpeed > 0d)
                || !Double.isFinite(desiredSpeed) || quantization <= 0)
            return Vec3.ZERO;
        double magnitude = intermediateAirRouteNode ? 1d : Math.min(desiredSpeed, distance * 2d) / desiredSpeed;
        return new Vec3(Math.round(dx / distance * magnitude * quantization) / (double) quantization, 0,
                Math.round(dz / distance * magnitude * quantization) / (double) quantization);
    }

    record FlightCommand(float yaw, int forward) {}

    /** Converts world steering to the exact forward-only command consumed by the shared motor. */
    static FlightCommand flightCommand(Vec3 worldWish, int quantization) {
        if (worldWish == null || quantization <= 0 || !Double.isFinite(worldWish.x)
                || !Double.isFinite(worldWish.z)) return new FlightCommand(0f, 0);
        double magnitude = Math.hypot(worldWish.x, worldWish.z);
        if (!(magnitude > 1e-9)) return new FlightCommand(0f, 0);
        double bounded = Math.min(1d, magnitude);
        int forward = Math.min(quantization, Math.max(0, (int) Math.round(bounded * quantization)));
        float yaw = (float) Math.max(-180d, Math.min(180d,
                Math.toDegrees(Math.atan2(-worldWish.x, worldWish.z))));
        return new FlightCommand(yaw, forward);
    }

    static float flightPresentationYaw(double velocityX, double velocityZ,
                                      double targetX, double targetZ, float currentYaw) {
        if (velocityX * velocityX + velocityZ * velocityZ > 1.0e-6)
            return (float) Math.toDegrees(Math.atan2(-velocityX, velocityZ));
        if (targetX * targetX + targetZ * targetZ > 1.0e-6)
            return (float) Math.toDegrees(Math.atan2(-targetX, targetZ));
        return currentYaw;
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
        if (!(entity instanceof Mob mob)) return;
        com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.forget(mob);
        if (!(mob instanceof NpcMovementDriven driven)) return;
        if (!driven.moonstation14$isNpcMovementDriven() && !BRAINS.containsKey(mob.getUUID())) return;
        driven.moonstation14$setNpcMovementDriven(false);
        BRAINS.remove(mob.getUUID());
    }

    private static void plan(Mob mob, ServerLevel level, CharacterData character, NpcBehaviorComponent policy,
                             Brain brain, long now) {
        expireRejectedSources(brain.rejectedUntil, now);
        var cleaningState = mob.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ABSORBENT_CLEANING.get());
        if (policy.tasks().contains(NpcBehaviorComponent.Task.CLEAN)
                && com.juicyslew.moonstation14.ms14.animals.AbsorbentCleaningService.capable(character)
                && (cleaningState == null || now >= cleaningState.nextUseTick())) {
            BlockPos puddle = brain.cleanTarget;
            if (puddle == null || !level.hasChunkAt(puddle)
                    || !(level.getBlockEntity(puddle) instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity))
                puddle = com.juicyslew.moonstation14.ms14.animals.AbsorbentCleaningService.nearestVisiblePuddle(mob, policy.visionRange());
            if (puddle != null) {
                brain.cleanTarget = puddle.immutable();
                brain.target = null; brain.idlePoint = null; brain.activeTask = NpcBehaviorComponent.Task.CLEAN;
                Vec3 goal = new Vec3(puddle.getX()+.5, puddle.getY(), puddle.getZ()+.5);
                if (mob.position().distanceTo(goal) <= policy.mealRange()) {
                    com.juicyslew.moonstation14.ms14.animals.AbsorbentCleaningService.cleanAt(mob, puddle, now, true);
                    brain.cleanTarget = null;
                    brain.route = List.of(); brain.activeTask = null; return;
                }
                var route = ROUTER.routeResult(mob, goal);
                brain.lastRouteResult = route.toString();
                brain.route = route.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE ? route.waypoints() : List.of();
                return;
            }
            brain.cleanTarget = null;
        }
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
            var flightPolicy = character.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class)
                    .orElse(null);
            if (flightPolicy != null && brain.flightPhase != null
                    && brain.flightPhase.phase() == NpcFlightController.Phase.GROUND_WANDER) {
                var ground = ROUTER.randomReachableIdleRoute(mob, policy.idleRange(), mob.position(), 0);
                Vec3 point = ground.goalSupport() == null ? null : ground.goalSupport().position();
                if (ground.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE && point != null) {
                    brain.idlePoint = point;
                    brain.route = ground.waypoints();
                    brain.lastRouteResult = ground.toString();
                }
            } else if (flightPolicy != null) {
                double preference = cruisePreference(flightPolicy.npcCruiseHeight(), mob.getRandom().nextDouble());
                brain.cruiseY = safeCruiseY(mob, preference);
            }
            if (flightPolicy != null && brain.flightPhase.phase() != NpcFlightController.Phase.GROUND_WANDER) {
                brain.idlePoint = randomAirDestination(mob, flightPolicy, level.getMinBuildHeight());
                var airRoute = AIR_ROUTER.routeResult(mob, brain.idlePoint);
                brain.lastRouteResult = airRoute.toString();
                if (airRoute.status() == AirNpcRoutePlanner.RouteStatus.ROUTE) brain.route = airRoute.waypoints();
                else brain.idlePoint = null;
            } else {
            var route = ROUTER.randomReachableIdleRoute(mob, policy.idleRange(), mob.position(), 0);
            Vec3 point = route.goalSupport() == null ? null : route.goalSupport().position();
            if (route.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE && point != null) {
                brain.idlePoint = point;
                brain.lastRouteResult = route.toString();
                brain.route = route.waypoints();
                brain.nextTargetRoute = now + policy.planIntervalTicks();
            }
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

    private static Vec3 randomAirDestination(Mob mob,
            com.juicyslew.moonstation14.ms14.character.components.FlightComponent flight, int minHeight) {
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double radius = 3 + mob.getRandom().nextDouble() * 5;
        double reference = Double.isFinite(BRAINS.get(mob.getUUID()).cruiseY)
                ? BRAINS.get(mob.getUUID()).cruiseY : mob.getY() + flight.npcCruiseHeight();
        double y = NpcFlightController.anchoredAltitude(reference, mob.getY(), mob.getRandom().nextDouble());
        y = Math.max(minHeight + 1, y);
        return new Vec3(mob.getX() + Math.cos(angle) * radius, y, mob.getZ() + Math.sin(angle) * radius);
    }

    static Vec3 idleRouteAnchor(Mob mob,
            com.juicyslew.moonstation14.ms14.character.components.FlightComponent flight, double cruiseY,
            int minimumBuildHeight) {
        if (flight == null || !Double.isFinite(cruiseY)) return mob.position();
        // The planner performs a bounded support search below this exact horizontal position;
        // don't guess a clearance fraction, since resolved cruise heights vary by prototype.
        return new Vec3(mob.getX(), Math.max(minimumBuildHeight, mob.getY()), mob.getZ());
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
            if (brain.activeTask == NpcBehaviorComponent.Task.RETALIATE && target instanceof net.minecraft.world.entity.LivingEntity living
                    && living.isAlive() && !com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.withinReach(
                            mob, living, CharacterIdentitySystem.resolveForActor(mob)
                                    .flatMap(c -> c.component(com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent.class))
                                    .map(com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent::range).orElse(1.6f)))
                return brain.route.isEmpty() ? living.position() : brain.route.get(0).position();
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
        return autonomousNpcEligible(mob, level, character)
                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(mob).isEmpty();
    }

    /** Canonical autonomy admission shared by routine NPC behavior, defensive retaliation and escape. */
    public static boolean autonomousNpcEligible(Mob mob, ServerLevel level, CharacterData character) {
        if (character == null || mob.isRemoved() || !mob.isAlive() || mob.isDeadOrDying() || !mob.isNoAi()
                || !mob.isAddedToLevel() || mob.level() != level || level.getEntity(mob.getUUID()) != mob
                || mob.isPassenger() || mob instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()
                || !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(mob)
                ) return false;
        if (character.component(NpcBehaviorComponent.class).isEmpty()) return false;
        var binding = PlayerCharacterHarnessBinder.binding(mob);
        return !LifecycleStartupRuntime.isExactOfflineOwnerBody(mob, binding);
    }

    /** Escape's extra custody exclusion is separate from general autonomous NPC authority. */
    private static boolean autonomousEscapeEligible(Mob mob, ServerLevel level, CharacterData character) {
        return autonomousNpcEligible(mob, level, character);
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

    private static MoveCheck safeNext(Mob mob, Vec3 delta) { return safeNext(mob, delta, false); }
    private static MoveCheck safeNext(Mob mob, Vec3 delta, boolean flying) {
        if (!(mob.level() instanceof ServerLevel level)) return MoveCheck.BLOCKED;
        Vec3 next = mob.position().add(delta);
        BlockPos feet = BlockPos.containing(next.x, next.y, next.z);
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) return MoveCheck.BLOCKED;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) return MoveCheck.BLOCKED;
        if (!flying && !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && !ROUTER.hasSupportedSurface(mob, next)) return MoveCheck.PIT;
        AABB box = mob.getBoundingBox().move(delta);
        if (!level.noCollision(mob, box)) return MoveCheck.BLOCKED;
        return MoveCheck.CLEAR;
    }

    /** Finds bounded clearance above the local loaded support without touching unloaded chunks. */
    static double safeCruiseY(Mob mob, double requestedHeight) {
        if (!(mob.level() instanceof ServerLevel level)) return mob.getY();
        if (!(requestedHeight > 0) || !Double.isFinite(requestedHeight)) return mob.getY();
        double maximumCruiseHeight = com.juicyslew.moonstation14.ms14.character.components.FlightComponent.MAX_NPC_CRUISE_HEIGHT;
        double maximumDrop = cruiseProjectionDrop(maximumCruiseHeight);
        GroundNpcRoutePlanner.Waypoint support = ROUTER.projectGroundBelow(mob, maximumDrop);
        if (support == null) return mob.getY();
        double target = support.y() + Math.min(requestedHeight, maximumCruiseHeight * 1.25);
        double clipped = ROUTER.clipVerticalTarget(mob, target);
        if (!Double.isFinite(clipped)) return mob.getY();
        return target > clipped ? Math.max(support.y(), clipped - .05) : clipped;
    }

    static double cruisePreference(double resolvedHeight, double randomUnit) {
        double roll = Double.isFinite(randomUnit) ? Math.max(0, Math.min(1, randomUnit)) : .5;
        return resolvedHeight * (.75 + roll * .5);
    }

    static double cruiseProjectionDrop(double resolvedHeight) {
        return Math.max(0, resolvedHeight) * 1.25 + .25;
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
