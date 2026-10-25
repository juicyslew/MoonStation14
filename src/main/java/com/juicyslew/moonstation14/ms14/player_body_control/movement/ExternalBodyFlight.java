package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Ephemeral, server-world-clocked native-derived throw flight; distinct from movement eligibility. */
public final class ExternalBodyFlight {
    private static final Map<Mob, State> FLIGHTS = new WeakHashMap<>();

    private ExternalBodyFlight() { }

    public static boolean canLaunch(Mob body) {
        return body != null && body.level() instanceof ServerLevel && hasPhysicsConsumer(body);
    }

    /** Preflight used while portable custody still suppresses the otherwise eligible NPC flight target. */
    public static boolean canLaunchAfterPortableRelease(Mob body) {
        if (body == null || !(body.level() instanceof ServerLevel)) return false;
        return body instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()
                || com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(body)
                || com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics
                        .hasCorpseCharacterProfile(body)
                || GroundedHarnessWorldStep.hasNpcFlightCharacterProfile(body);
    }

    public static void launch(Mob body, Vec3 velocity, int durationTicks) {
        launch(body, velocity, durationTicks, false);
    }

    /** Called only after canonical portable-body release commits. */
    public static void launchPortableBodyThrow(Mob body, Vec3 velocity, int durationTicks) {
        launch(body, velocity, durationTicks, true);
    }

    private static void launch(Mob body, Vec3 velocity, int durationTicks, boolean portableThrow) {
        if (body == null || !(body.level() instanceof ServerLevel level) || durationTicks <= 0
                || velocity == null || !finite(velocity) || !canLaunch(body)) return;
        long now = level.getGameTime();
        body.setDeltaMovement(velocity);
        FLIGHTS.put(body, new State(level, now, now + durationTicks, Long.MIN_VALUE, portableThrow, false,
                boundary(level.dimension(), body.getX(), body.getZ())));
    }

    /** Authoritative remaining flight duration for snapshots; querying never advances simulation. */
    public static int remainingTicks(Mob body) {
        State state = FLIGHTS.get(body);
        if (state == null || body.level() != state.level) return 0;
        long remaining = state.endTick - state.level.getGameTime();
        if (remaining <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    public static boolean active(Mob body) { return remainingTicks(body) > 0; }

    public static boolean alreadyAdvancedThisTick(Mob body) {
        State state = FLIGHTS.get(body);
        return state != null && state.level == body.level() && state.lastAdvancedTick == state.level.getGameTime();
    }

    public static void advanced(Mob body) {
        State state = FLIGHTS.get(body);
        if (state != null && state.level == body.level())
            FLIGHTS.put(body, new State(state.level, state.launchTick, state.endTick, state.level.getGameTime(), state.portableThrow, state.released, state.lastBoundary));
    }

    public static boolean tracked(Mob body) {
        State state = FLIGHTS.get(body);
        return state != null && state.level == body.level();
    }

    /** Completed only after ordinary collision/gravity/friction has actually settled the body. */
    public static boolean settled(Mob body) {
        Vec3 velocity = body.getDeltaMovement();
        return body.onGround() && velocity.lengthSqr() == 0d;
    }

    public static void cancel(Mob body) { FLIGHTS.remove(body); }

    /** Called at LevelTick.Post after transport/containment updates; advances idle throws without packets. */
    public static void tick(ServerLevel level) {
        GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
        for (Mob body : new ArrayList<>(FLIGHTS.keySet())) {
            if (body == null) continue;
            State state = FLIGHTS.get(body);
            if (state == null) continue;
            if (body.level() != state.level) {
                if (body.level() instanceof ServerLevel currentLevel && !body.isRemoved()
                        && body.isAddedToLevel() && currentLevel.getEntity(body.getUUID()) == body)
                    recordOfflineRelocationIfBoundary(body);
                cancel(body);
                continue;
            }
            if (state.level != level) continue;
            if (state.released) {
                // endTick is fixed at release; ordinary movement may keep refreshing
                // lastAdvancedTick and must not extend this one-tick dedup lease.
                if (level.getGameTime() > state.endTick) cancel(body);
                continue;
            }
            boolean retainedCorpsePhysics = com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics
                    .eligible(body);
            if (body.level() != level || body.isRemoved() || !body.isAlive() && !retainedCorpsePhysics
                    || !hasPhysicsConsumer(body)) {
                cancel(body);
                continue;
            }
            if (com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                    .ownerOf(body).isPresent()) {
                cancel(body);
                continue;
            }
            if (alreadyAdvancedThisTick(body)) {
                recordOfflineRelocationIfBoundary(body);
                if (remainingTicks(body) == 0 && settled(body)) cancel(body);
            } else {
                boolean advanced;
                if (com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(body))
                    advanced = com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.step(body);
                else if (com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep.isNpcFlightBodyEligible(body))
                    advanced = stepper.stepNpcFlight(body).isPresent();
                else advanced = stepper.step(body, 0, 0, false, false, body.getYRot()).isPresent();
                if (advanced) {
                    recordOfflineRelocationIfBoundary(body);
                    if (remainingTicks(body) == 0 && settled(body)) {
                        // The ordinary post-throw motor has had its landing/ground-friction step.
                        cancel(body);
                    }
                }
            }
            State current = FLIGHTS.get(body);
            if (current != null && current.portableThrow
                    && level.getGameTime() - current.launchTick >= recoveryTicks(body)
                    && !body.onGround()
                    && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.recoverPortableThrow(body)) {
                // Retain the lease through this tick so every motor observes the advancement guard.
                FLIGHTS.put(body, new State(current.level, current.launchTick, level.getGameTime(),
                        level.getGameTime(), true, true, current.lastBoundary));
            }
        }
    }

    private static boolean hasPhysicsConsumer(Mob body) {
        return body instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()
                || com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(body)
                || GroundedHarnessWorldStep.isNpcFlightBodyEligible(body);
    }

    private static boolean finite(Vec3 velocity) {
        return Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z);
    }

    private static void recordOfflineRelocationIfBoundary(Mob body) {
        State state = FLIGHTS.get(body);
        if (state == null || !(body.level() instanceof ServerLevel bodyLevel)) return;
        Boundary current = boundary(bodyLevel.dimension(), body.getX(), body.getZ());
        if (state.lastBoundary.equals(current)) return;
        // Update before persistence: even an ACTIVE/anonymous/stale body is attempted only once
        // per actual chunk/dimension transition, never once per physics frame.
        State captured = new State(state.level, state.launchTick, state.endTick, state.lastAdvancedTick, state.portableThrow, state.released, current);
        FLIGHTS.put(body, captured);
        PlayerCharacterBinding binding = PlayerCharacterHarnessBinder.binding(body);
        if (binding == null) return;
        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleStartupRuntime
                .recordOfflineBodyRelocation(body, candidate -> candidate == body
                        && ownerProofMatches(candidate, body, FLIGHTS.get(body), captured,
                        PlayerCharacterHarnessBinder.binding(candidate), binding)
                        && body.level() == bodyLevel && !body.isRemoved()
                        && body.isAddedToLevel() && bodyLevel.getEntity(body.getUUID()) == body);
    }

    static Boundary boundary(ResourceKey<Level> dimension, double x, double z) {
        return new Boundary(dimension, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    static boolean ownerProofMatches(Object currentBody, Object expectedBody, Object currentState,
                                     Object expectedState, PlayerCharacterBinding currentBinding,
                                     PlayerCharacterBinding expectedBinding) {
        return currentBody == expectedBody && currentState == expectedState
                && expectedBinding != null && expectedBinding.equals(currentBinding);
    }

    static boolean boundaryChanged(Boundary previous, Boundary current) {
        return previous == null || !previous.equals(current);
    }

    record Boundary(ResourceKey<Level> dimension, int chunkX, int chunkZ) { }
    private static int recoveryTicks(Mob body) {
        return com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.capability(body)
                .map(com.juicyslew.moonstation14.ms14.character.components.FlightComponent::throwRecoveryTicks)
                .orElse(Integer.MAX_VALUE);
    }
    private record State(ServerLevel level, long launchTick, long endTick, long lastAdvancedTick,
                         boolean portableThrow, boolean released, Boundary lastBoundary) { }
}
