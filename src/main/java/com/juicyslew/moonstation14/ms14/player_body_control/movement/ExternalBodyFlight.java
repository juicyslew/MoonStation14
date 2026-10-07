package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Ephemeral, server-world-clocked native-derived throw flight; distinct from movement eligibility. */
public final class ExternalBodyFlight {
    private static final Map<Mob, State> FLIGHTS = new WeakHashMap<>();

    private ExternalBodyFlight() { }

    public static void launch(Mob body, Vec3 velocity, int durationTicks) {
        if (!(body.level() instanceof ServerLevel level) || durationTicks <= 0
                || !(body instanceof MindControlledMob owner) || !owner.moonstation14$isMovementOwned()) return;
        long now = level.getGameTime();
        body.setDeltaMovement(velocity);
        FLIGHTS.put(body, new State(level, now + durationTicks, Long.MIN_VALUE));
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
            FLIGHTS.put(body, new State(state.level, state.endTick, state.level.getGameTime()));
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
            if (state == null || state.level != level) continue;
            if (body.level() != level || body.isRemoved() || !body.isAlive()
                    || !(body instanceof MindControlledMob owner) || !owner.moonstation14$isMovementOwned()) {
                cancel(body);
                continue;
            }
            if (com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                    .ownerOf(body).isPresent()) {
                cancel(body);
                continue;
            }
            if (alreadyAdvancedThisTick(body)) {
                if (remainingTicks(body) == 0 && settled(body)) cancel(body);
            } else {
                var result = stepper.step(body, 0, 0, false, false, body.getYRot());
                if (result.isPresent()) {
                    if (remainingTicks(body) == 0 && settled(body)) {
                        // The ordinary post-throw motor has had its landing/ground-friction step.
                        cancel(body);
                    }
                }
            }
        }
    }

    private record State(ServerLevel level, long endTick, long lastAdvancedTick) { }
}
