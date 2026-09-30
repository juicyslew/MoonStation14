package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Atomic body-owned hand/pouch transfer; never uses the carrier inventory. */
public final class BodyPouchTransfer {
    private BodyPouchTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result insert(ServerPlayer actor, String pouchHand, String expectedPouchToken,
                                String sourceHand, String expectedSourceToken, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), pouchHand, expectedPouchToken,
                sourceHand, expectedSourceToken, expectedRevision, true,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result extract(ServerPlayer actor, String pouchHand, String expectedPouchToken,
                                 String destinationHand, String expectedChildToken, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), pouchHand, expectedPouchToken,
                destinationHand, expectedChildToken, expectedRevision, false,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Trusted fixture seam: real spawned body, with test-supplied authority validity only.
    static Result transferOwned(LivingEntity body, String pouchHand, String pouchToken, String otherHand,
                                String otherToken, long revision, boolean inserting, BooleanSupplier authorityValid) {
        if (body == null || authorityValid == null || pouchHand == null || otherHand == null
                || pouchToken == null || otherToken == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || !live(body, level)
                || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision) return Result.DENIED;
        Optional<LiveHands> next = inserting
                ? before.insertPouch(revision, pouchHand, pouchToken, otherHand, otherToken)
                : before.extractPouch(revision, pouchHand, pouchToken, otherHand, otherToken);
        if (next.isEmpty() || !authorityValid.getAsBoolean() || !live(body, level)
                || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body)) return Result.DENIED;
        try {
            body.setData(ModDataAttachments.LIVE_HANDS.get(), next.orElseThrow());
        } catch (RuntimeException failure) {
            return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    && before.compatible(body) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}
