package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Bounded mutation of an already-owned active body-hand item, never of the carrier inventory. */
public final class BodyHeldItemMutation {
    private BodyHeldItemMutation() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    /** Calculates a detached, representable candidate without publishing it. */
    public static Optional<LiveHands> preview(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands admittedHands, String handId, String token, long expectedRevision,
            ItemStack expectedBefore, ItemStack after) {
        if (!valid(actor, authority, admittedHands, handId, token, expectedRevision, expectedBefore, after))
            return Optional.empty();
        var next = admittedHands.replaceActiveUseStack(expectedRevision, handId, token, expectedBefore, after);
        return next.filter(candidate -> BodyHandStateService.representable(candidate, authority.body()));
    }

    public static Result commit(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands admittedHands, String handId, String token, long expectedRevision,
            ItemStack expectedBefore, ItemStack after) {
        Optional<LiveHands> candidate = preview(actor, authority, admittedHands, handId, token,
                expectedRevision, expectedBefore, after);
        if (candidate.isEmpty()) return Result.DENIED;
        LivingEntity body = authority.body();
        ServerLevel level = (ServerLevel) body.level();
        if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != admittedHands
                || !matches(admittedHands, handId, token, expectedRevision, expectedBefore)) return Result.DENIED;
        try {
            if (!LiveHandsCommitGateway.commit(actor, authority, candidate.orElseThrow()))
                return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == admittedHands
                        ? Result.DENIED : Result.RECOVERY_REQUIRED;
        } catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == admittedHands
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        LiveHands published = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (!live(body, level) || published != candidate.orElseThrow()
                || !BodyHandStateService.representable(published, body)) return Result.RECOVERY_REQUIRED;
        return Result.SUCCESS;
    }

    private static boolean valid(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands hands,
            String hand, String token, long revision, ItemStack before, ItemStack after) {
        if (actor == null || authority == null || authority.actor() != actor || hands == null
                || hand == null || token == null || before == null || after == null
                || !(authority.body().level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(authority.body(), level)
                || !level.hasChunkAt(authority.body().blockPosition()) || !CharacterControlSystem.canAct(authority.body())
                || !HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !matches(hands, hand, token, revision, before)) return false;
        try { new ItemToken(token); }
        catch (IllegalArgumentException failure) { return false; }
        return hands.compatible(authority.body());
    }

    private static boolean matches(LiveHands hands, String hand, String token, long revision, ItemStack before) {
        return hands != null && hands.revision() == revision && hands.activeHand().equals(hand)
                && hands.token(hand).filter(token::equals).isPresent()
                && hands.stackCopy(hand).filter(current -> ItemStack.isSameItemSameComponents(current, before)
                && current.getCount() == before.getCount()).isPresent();
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}

