package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Atomic transfers for a bag or belt held in one body hand. */
public final class BodyHeldGridTransfer {
    private BodyHeldGridTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result store(ServerPlayer actor, String hostHand, String hostToken, String sourceHand,
                               String sourceToken, long revision, GridGeometry.Cell anchor,
                               GridGeometry.Rotation rotation) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), hostHand, hostToken, sourceHand, sourceToken,
                revision, anchor, rotation, null, null, true,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result take(ServerPlayer actor, String hostHand, String hostToken, String destinationHand,
                              String childToken, long revision, GridGeometry.Cell anchor) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), hostHand, hostToken, destinationHand, childToken,
                revision, anchor, null, null, null, false,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result move(ServerPlayer actor, String hostHand, String hostToken, String childToken,
                              long revision, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
                              GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), hostHand, hostToken, null, childToken,
                revision, oldAnchor, oldRotation, newAnchor, newRotation, false,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Fixture seam accepts only a spawned, prototype-compatible body; it does not claim a connected player.
    static Result transferOwned(LivingEntity body, String hostHand, String hostToken, String otherHand,
                                String otherToken, long revision, GridGeometry.Cell anchor,
                                GridGeometry.Rotation rotation, GridGeometry.Cell newAnchor,
                                GridGeometry.Rotation newRotation, boolean storing, BooleanSupplier authorityValid) {
        if (body == null || hostHand == null || hostToken == null || otherToken == null || anchor == null
                || storing && (otherHand == null || rotation == null)
                || storing && otherHand.equals(hostHand)
                || !storing && (newAnchor != null ? otherHand != null : otherHand == null || otherHand.equals(hostHand))
                || authorityValid == null || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(body, level) || !CharacterControlSystem.canAct(body))
            return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || !before.compatible(body)) return Result.DENIED;
        var host = before.stackCopy(hostHand);
        if (host.isEmpty() || !before.token(hostHand).filter(hostToken::equals).isPresent()
                || !(host.orElseThrow().is(ModItems.BAG) || host.orElseThrow().is(ModItems.BELT))
                || newAnchor != null && !host.orElseThrow().has(ModDataComponents.GRID_CONTENTS.get())
                || GridContents.read(host.orElseThrow()).isEmpty()) return Result.DENIED;
        Optional<LiveHands> next;
        if (storing) next = before.insertHeldGrid(revision, hostHand, hostToken, otherHand, otherToken, anchor, rotation);
        else if (newAnchor != null) next = before.moveHeldGrid(revision, hostHand, hostToken, otherToken,
                anchor, rotation, newAnchor, newRotation);
        else next = before.extractHeldGrid(revision, hostHand, hostToken, otherHand, otherToken, anchor);
        if (next.isEmpty() || !authorityValid.getAsBoolean() || !live(body, level)
                || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || !hostStillMatches(before, hostHand, hostToken, newAnchor != null)
                || !com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.representable(next.orElseThrow(), body)) return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(body, next.orElseThrow()); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    private static boolean hostStillMatches(LiveHands state, String hand, String token, boolean moving) {
        var stack = state.stackCopy(hand);
        return state.token(hand).filter(token::equals).isPresent() && stack.isPresent()
                && (stack.orElseThrow().is(ModItems.BAG) || stack.orElseThrow().is(ModItems.BELT))
                && (!moving || stack.orElseThrow().has(ModDataComponents.GRID_CONTENTS.get()))
                && GridContents.read(stack.orElseThrow()).isPresent();
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}
