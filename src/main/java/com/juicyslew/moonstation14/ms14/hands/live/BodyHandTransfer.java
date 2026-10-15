package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Authenticated, atomic whole-item transfer between body-owned hands. */
public final class BodyHandTransfer {
    private BodyHandTransfer() { }
    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result transfer(ServerPlayer actor, String source, String destination, String token, long revision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        var snapshot = authority.orElseThrow();
        return transferOwned(snapshot.body(), source, destination, token, revision,
                () -> HandActorAuthority.revalidate(actor, snapshot));
    }

    static Result transferOwned(LivingEntity body, String source, String destination, String token,
                                long revision, java.util.function.BooleanSupplier authorized) {
        if (body == null || source == null || destination == null || token == null || authorized == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || level.getEntity(body.getUUID()) != body
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || before.handIds().size() != 2 || !before.handIds().contains(source)
                || !before.handIds().contains(destination)) return Result.DENIED;
        LiveHands.WholeResult transition = before.transferWhole(revision, source, destination, new ItemToken(token));
        LiveHands next = transition.state();
        if (!transition.succeeded() || !authorized.getAsBoolean() || !before.compatible(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || before.revision() != revision || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var sourceStack = before.stackCopy(source).orElse(null);
        var sourceReference = PortableBodyKeys.reference(sourceStack);
        LivingEntity carriedBody = sourceReference == null
                || !sourceReference.dimension().equals(level.dimension().location()) ? null
                : level.getEntity(sourceReference.body()) instanceof LivingEntity living ? living : null;
        var sourceOwner = carriedBody == null ? null : containment.registeredOwnerOf(carriedBody).orElse(null);
        var refresh = sourceOwner instanceof PortableBodyCarrierOwner carrierOwner
                && carrierOwner.carrier() == body && carrierOwner.reference().equals(sourceReference)
                ? PortableBodyEscapeCustody.prepareTransfer(carrierOwner, carriedBody) : null;
        try { LiveHandsCommitGateway.commitOwned(body, next); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != next || !next.compatible(body))
            return Result.RECOVERY_REQUIRED;
        if (refresh != null) {
            var nextOwner = containment.registeredOwnerOf(carriedBody).orElse(null);
            if (nextOwner instanceof PortableBodyCarrierOwner portable) refresh.apply(portable);
        }
        return Result.SUCCESS;
    }
}

