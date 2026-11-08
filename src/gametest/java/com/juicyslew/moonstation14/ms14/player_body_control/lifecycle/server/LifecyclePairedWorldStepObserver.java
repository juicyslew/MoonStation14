package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only observer reusing the joined owner's normal native world step and a paired actor step. */
public final class LifecyclePairedWorldStepObserver {
    private LifecyclePairedWorldStepObserver() { }

    public record PairSnapshot(long tick, Vec3 ownerBefore, Vec3 ownerVelocityBefore,
                               Vec3 pairedBefore, Vec3 pairedVelocityBefore,
                               Vec3 ownerAfter, Vec3 ownerVelocityAfter,
                               Vec3 pairedAfter, Vec3 pairedVelocityAfter,
                               AABB ownerBoundsBefore, AABB ownerBoundsAfter,
                               AABB pairedBoundsBefore, AABB pairedBoundsAfter,
                               boolean ownerGroundedBefore, boolean pairedGroundedBefore,
                               boolean ownerGrounded, boolean pairedGrounded,
                               int wishX, int wishZ, boolean jump, boolean sprint, float yaw,
                               SableOwnedBodyMotion.MoveResult ownerMove,
                               SableOwnedBodyMotion.MoveResult pairedMove) { }

    /** Bounded, ordered per-tick witness storage for one focused paired-body fixture. */
    public static final class CaptureWindow {
        private final int capacity;
        private final ArrayList<PairSnapshot> samples = new ArrayList<>();
        public CaptureWindow(int capacity) {
            if (capacity < 1) throw new IllegalArgumentException("capture capacity must be positive");
            this.capacity = capacity;
        }
        public void add(PairSnapshot sample) {
            if (samples.size() >= capacity)
                throw new net.minecraft.gametest.framework.GameTestAssertException(
                        "paired lifecycle witness exceeded bounded " + capacity + "-sample window");
            samples.add(sample);
        }
        public List<PairSnapshot> snapshot() { return List.copyOf(samples); }
        public int size() { return samples.size(); }
    }

    public static AutoCloseable install(ServerPlayer owner, PlayerCharacterHarnessEntity paired,
            AtomicReference<SableOwnedBodyMotion.MoveResult> ownerMove,
            AtomicReference<SableOwnedBodyMotion.MoveResult> pairedMove, AtomicLong tick,
            AtomicReference<PairSnapshot> snapshot, CaptureWindow window) {
        var step = GroundedHarnessWorldStep.observingNativeGeometry();
        return LifecycleCharacterSessionControl.withWorldStepForTesting(owner,
                 (body, wishX, wishZ, jump, sneak, sprint, yaw, originSequence, observer, reference) -> {
                    long now = owner.level().getServer().getTickCount();
                    if (tick.getAndSet(now) == now)
                        throw new net.minecraft.gametest.framework.GameTestAssertException(
                                "joined owner consumed multiple world steps in one tick");
                    Vec3 ownerBefore=body.position(), ownerVelocityBefore=body.getDeltaMovement();
                    Vec3 pairedBefore=paired.position(), pairedVelocityBefore=paired.getDeltaMovement();
                    AABB ownerBoundsBefore=body.getBoundingBox(), pairedBoundsBefore=paired.getBoundingBox();
                    boolean ownerGroundedBefore=body.onGround(), pairedGroundedBefore=paired.onGround();
                    ownerMove.set(null); pairedMove.set(null); snapshot.set(null);
                    Optional<com.juicyslew.moonstation14.ms14.movement.CharacterMovementState> moved =
                             step.step(body, wishX, wishZ, jump, sneak, sprint, yaw, originSequence, nativeMove -> {
                                  ownerMove.set(nativeMove);
                                  if (observer != null) observer.accept(nativeMove);
                             }, reference);
                     step.step(paired, wishX, wishZ, jump, sneak, sprint, yaw, originSequence, pairedMove::set, reference);
                    var captured=new PairSnapshot(now, ownerBefore, ownerVelocityBefore,
                            pairedBefore, pairedVelocityBefore, body.position(), body.getDeltaMovement(),
                            paired.position(), paired.getDeltaMovement(), ownerBoundsBefore,body.getBoundingBox(),
                            pairedBoundsBefore,paired.getBoundingBox(),ownerGroundedBefore,pairedGroundedBefore,
                            body.onGround(),paired.onGround(),
                            wishX,wishZ,jump,sprint,yaw,
                            ownerMove.get(),pairedMove.get());
                    snapshot.set(captured);
                    window.add(captured);
                    return moved;
                });
    }
}
