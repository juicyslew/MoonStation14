package com.juicyslew.moonstation14.ms14.player_body_control.character;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/** Temporary server-side movement ownership lease for an explicitly configured grounded mob. */
public final class GroundedHarnessLease implements AutoCloseable {
    public static final String CONFIGURED_MARKER = "moonstation14:configured_mind_harness";

    private final Mob body;
    private final MindControlledMob owner;
    private final boolean priorNoAi;
    private boolean closed;

    private GroundedHarnessLease(Mob body, MindControlledMob owner, boolean priorNoAi) {
        this.body = body;
        this.owner = owner;
        this.priorNoAi = priorNoAi;
    }

    /** Attempts to acquire this body on the server. No Mind transfer or other session state is created. */
    public static Optional<GroundedHarnessLease> tryAcquire(Mob body) {
        if (!isEligible(body)) return Optional.empty();

        MindControlledMob owner = (MindControlledMob) body;
        boolean priorNoAi = body.isNoAi();
        try {
            body.getNavigation().stop();
            body.setNoAi(true);
            owner.moonstation14$setMovementOwned(true);
            return Optional.of(new GroundedHarnessLease(body, owner, priorNoAi));
        } catch (RuntimeException | Error failure) {
            try {
                restore(body, owner, priorNoAi);
            } catch (IllegalStateException cleanupFailure) {
                IllegalStateException result = new IllegalStateException(
                        "Failed to acquire grounded harness lease and could not verify rollback", failure);
                result.addSuppressed(cleanupFailure);
                throw result;
            }
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        restore(body, owner, priorNoAi);
        closed = true;
    }

    static boolean isEligible(Mob body) {
        return isEligible(body, false);
    }

    /** Read-only revalidation for a body whose movement ownership lease is already held. */
    public static boolean isOwnedBodyEligible(Mob body) {
        return isEligible(body, true);
    }

    private static boolean isEligible(Mob body, boolean allowOwned) {
        if (body == null || body.isRemoved() || !body.isAlive() || body.level().isClientSide
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || level.getEntity(body.getUUID()) != body || body.isPassenger()
                || body.isInWaterOrBubble() || body.isInLava()
                || !body.getPersistentData().getBoolean(CONFIGURED_MARKER)
                || !(body instanceof MindControlledMob owner)
                || (!allowOwned && owner.moonstation14$isMovementOwned())) {
            return false;
        }

        CharacterIdentityAttachment identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound()
                || ModCharacters.characterForHost(level, BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()))
                .filter(identity.characterId()::equals).isEmpty()) return false;
        return CharacterIdentitySystem.resolve(body)
                .filter(data -> data.movement().filter(movement -> "grounded".equals(movement.mode())).isPresent())
                .isPresent();
    }

    private static void restore(Mob body, MindControlledMob owner, boolean priorNoAi) {
        IllegalStateException failure = null;
        boolean markerCleared = false;
        boolean aiRestored = false;
        try {
            owner.moonstation14$setMovementOwned(false);
            markerCleared = true;
        } catch (RuntimeException | Error exception) {
            failure = cleanupFailure(failure, "setting movement ownership marker to false", exception);
        }
        try {
            body.setNoAi(priorNoAi);
            aiRestored = true;
        } catch (RuntimeException | Error exception) {
            failure = cleanupFailure(failure, "restoring prior noAI=" + priorNoAi, exception);
            try {
                owner.moonstation14$setMovementOwned(true);
            } catch (RuntimeException | Error rollbackFailure) {
                failure = cleanupFailure(failure,
                        "reinstating movement ownership marker after AI restoration failed; body protection is not guaranteed",
                        rollbackFailure);
            }
        }

        try {
            if (owner.moonstation14$isMovementOwned()) {
                failure = cleanupFailure(failure, "verifying movement ownership marker is false",
                        new IllegalStateException("marker remained true"));
            }
        } catch (RuntimeException | Error exception) {
            failure = cleanupFailure(failure, "reading movement ownership marker during restoration", exception);
        }
        try {
            if (body.isNoAi() != priorNoAi) {
                failure = cleanupFailure(failure, "verifying prior noAI=" + priorNoAi,
                        new IllegalStateException("observed noAI=" + body.isNoAi()));
            }
        } catch (RuntimeException | Error exception) {
            failure = cleanupFailure(failure, "reading noAI during restoration", exception);
        }
        if (!markerCleared && failure == null) {
            failure = new IllegalStateException("Movement ownership marker setter did not complete");
        }
        if (!aiRestored && failure == null) {
            failure = new IllegalStateException("noAI restoration setter did not complete");
        }
        if (failure != null) throw failure;
    }

    private static IllegalStateException cleanupFailure(IllegalStateException failure, String context,
                                                         Throwable cause) {
        IllegalStateException contextual = new IllegalStateException("Failed " + context, cause);
        if (failure == null) return contextual;
        failure.addSuppressed(contextual);
        return failure;
    }
}
