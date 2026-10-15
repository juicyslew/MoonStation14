package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared timed owner for an already-admitted escape from portable custody. */
public final class PortableBodyEscapeService {
    public static final String OPERATION_KIND = "portable_body_escape";
    public static final double MOVEMENT_THRESHOLD = .3d;
    public static final double DISTANCE_THRESHOLD = 1.5d;
    public record Diagnostic(java.util.UUID bodyId, java.util.UUID rootId, String stage, String detail) { }
    private static final java.util.concurrent.CopyOnWriteArrayList<java.util.function.Consumer<Diagnostic>> OBSERVERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    static AutoCloseable observeDiagnostics(java.util.function.Consumer<Diagnostic> observer) {
        OBSERVERS.add(Objects.requireNonNull(observer));
        return () -> OBSERVERS.remove(observer);
    }
    static void diagnostic(LivingEntity body, java.util.UUID rootId, String stage, String detail) {
        if (OBSERVERS.isEmpty()) return;
        var event = new Diagnostic(body.getUUID(), rootId, stage, detail);
        for (var observer : OBSERVERS) try { observer.accept(event); } catch (RuntimeException ignored) { }
    }
    private PortableBodyEscapeService() { }

    /** SS14 DroppedEvent equivalent: invalidate an in-flight escape at a committed real drop. */
    public static void onDropped(LivingEntity body) {
        if (body == null) return;
        String source = body.getUUID().toString();
        TimedInteractionSystem.cancelMatching(body, op -> op.kind().equals(OPERATION_KIND)
                && op.sourceKey().equals(source));
    }

    /** Custody owner captures an exact token and performs the one canonical release transaction. */
    public interface Custody {
        default java.util.UUID rootId() { return null; }
        boolean isCurrent();
        boolean canRemoveReference();
        /** Canonical custody owner applies interaction accessibility without self-collider LOS rejection. */
        boolean isAccessible();
        /** Position of the containing body's/root, not the moving bag/item presentation. */
        Vec3 rootPosition();
        /** Frame-aware movement coordinate; fixture/custom custody keeps legacy root-relative sampling. */
        default Vec3 movementPosition(LivingEntity body) {
            Vec3 root = rootPosition();
            return root == null ? null : body.position().subtract(root);
        }
        boolean releaseOriginalBody();
    }

    /** Live entry: capability and default duration always come from resolved character composition. */
    public static TimedInteractionEngine.StartResult request(LivingEntity body, ServerPlayer presentationOwner,
            long originSequence, Custody custody) {
        var capability = ActiveCharacterPolicy.resolveActor(body)
                .flatMap(character -> character.component(CanEscapeInventoryComponent.class)).orElse(null);
        if (capability == null) return TimedInteractionEngine.StartResult.DENIED;
        return requestForResolvedCapability(body, presentationOwner, originSequence, capability, custody);
    }

    static TimedInteractionEngine.StartResult requestForResolvedCapability(LivingEntity body,
            ServerPlayer presentationOwner, long originSequence, CanEscapeInventoryComponent capability, Custody custody) {
        if (capability == null) return TimedInteractionEngine.StartResult.DENIED;
        final int duration;
        try {
            duration = com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem
                    .secondsToTicks(capability.baseResistTimeSeconds());
        } catch (IllegalArgumentException invalidDuration) {
            return TimedInteractionEngine.StartResult.DENIED;
        }
        return requestForCapability(body, presentationOwner, originSequence, duration, custody);
    }

    /** Package-private scheduler seam for fixtures; live requests must use {@link #request}. */
    static TimedInteractionEngine.StartResult requestForCapability(LivingEntity body, ServerPlayer presentationOwner,
            long originSequence, int durationTicks, Custody custody) {
        Objects.requireNonNull(body); Objects.requireNonNull(custody);
        if (durationTicks < 1 || !admitted(body, custody)) return TimedInteractionEngine.StartResult.DENIED;
        String source = body.getUUID().toString();
        var state = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        if (state != null && state.operations().stream().anyMatch(op -> op.phase() == TimedOperation.Phase.ACTIVE
                && op.kind().equals(OPERATION_KIND) && op.sourceKey().equals(source)))
            return TimedInteractionEngine.StartResult.STARTED; // repeated directional input is not a toggle
        // Portable bodies are pinned in the carrier's coordinate frame. Compare the
        // body-to-root vector, not either entity's world position: SS14 DoAfter
        // interruption is parent-relative (DoAfter.cs:44-57; SharedDoAfterSystem.cs:229-238).
        java.util.UUID rootId = custody.rootId();
        return TimedInteractionSystem.startWithMovementPosition(body, presentationOwner, originSequence, OPERATION_KIND, source,
                durationTicks, true, true, 1d, MOVEMENT_THRESHOLD, () -> durationTicks,
                () -> {
                    boolean admittedNow = admitted(body, custody);
                    Vec3 rootNow = custody.rootPosition();
                    return admittedNow && rootNow != null;
                },
                () -> {
                    boolean current = custody.isCurrent();
                    boolean removable = current && custody.canRemoveReference();
                    boolean released = removable && custody.releaseOriginalBody();
                    var outcome = released ? TimedInteractionSystem.Completion.STOP : TimedInteractionSystem.Completion.DENIED;
                    diagnostic(body, rootId, "completion-outcome", "op=" + operationId(body) + ",current=" + current
                            + ",removable=" + removable + ",released=" + released + ",outcome=" + outcome);
                    return outcome;
                },
                 Optional.empty(), () -> { }, () -> {
                      return custody.movementPosition(body);
                 });
    }

    private static boolean admitted(LivingEntity body, Custody custody) {
        if (body.isRemoved() || !body.isAlive()
                || !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body)
                || !custody.isCurrent() || !custody.canRemoveReference()
                || !custody.isAccessible()) return false;
        Vec3 root = custody.rootPosition();
        return root != null && body.position().distanceTo(root) <= DISTANCE_THRESHOLD;
    }
    private static long operationId(LivingEntity body) {
        var state = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
        return state == null ? -1 : state.operations().stream().filter(op -> op.kind().equals(OPERATION_KIND)
                && op.phase() == TimedOperation.Phase.COMPLETED).mapToLong(TimedOperation::id).max().orElse(-1);
    }
}
