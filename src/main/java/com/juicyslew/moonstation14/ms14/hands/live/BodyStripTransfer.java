package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.stripping.StripCapability;
import com.juicyslew.moonstation14.ms14.stripping.StripSlotRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Timed-completion consumer for whole-item transfers between distinct character bodies. */
public final class BodyStripTransfer {
    private BodyStripTransfer() { }
    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }
    @FunctionalInterface interface Committer { boolean commit(LivingEntity body, LiveHands expected, LiveHands value); }

    /**
     * Commits a previously timed action. authorityValid must revalidate authority/environment only;
     * it must not require the pre-transfer source token to remain in its old owner after commit.
     */
    public static Result transferOwned(LivingEntity actorBody, LivingEntity targetBody, StripSlotRef slot,
                                       boolean insertion, String sourceToken, BooleanSupplier authorityValid) {
        Result[] completed = {null};
        try {
            return LiveHandsCommitGateway.batch(() -> completed[0] = transfer(actorBody, targetBody, slot, insertion,
                    sourceToken, authorityValid, LiveHandsCommitGateway::commitTrusted));
        } catch (RuntimeException publicationFailure) {
            // The inner publisher proves both candidate identities before it returns SUCCESS;
            // batch's finally publication may throw only after that outcome has been recorded.
            if (completed[0] == Result.SUCCESS) return Result.SUCCESS;
            return completed[0] == Result.DENIED ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
    }

    /** Side-effect-free eligibility check for timed-action start/tick validation. */
    public static boolean canTransferOwned(LivingEntity actor, LivingEntity target, StripSlotRef ref,
                                           boolean insertion, String sourceToken, BooleanSupplier authorityValid) {
        if (!baseValid(actor, target, ref, sourceToken, authorityValid)) return false;
        LiveHands a = existing(actor), t = existing(target);
        if (a == null || t == null || !a.compatible(actor) || !t.compatible(target)) return false;
        if (ref.kind() == StripSlotRef.Kind.HAND && !t.handIds().contains(ref.id())) return false;
        if (insertion) {
            String hand = sourceHandForToken(a, sourceToken);
            if (hand == null || !hand.equals(a.activeHand()) || a.reservation(hand).isPresent()) return false;
            ItemStack moving = a.stackCopy(hand).orElse(null);
            if (moving == null || moving.isEmpty()) return false;
            if (ref.kind() == StripSlotRef.Kind.HAND)
                return t.putWhole(t.revision(), ref.id(), new ItemToken(sourceToken), moving).succeeded();
            var snapshot = LiveHands.equipmentSnapshot(target).orElse(null);
            var template = BodyEquipmentTransfer.templateForTransfer(target);
            if (snapshot == null || template == null) return false;
            var occupied = snapshot.slots().stream().filter(s -> s.stack().isPresent())
                    .map(LiveHands.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
            return BodyEquipmentTransfer.fitsForTransfer((ServerLevel) actor.level(), template, snapshot, ref.id(), moving, occupied)
                    && t.insertEquipment(t.revision(), snapshot.slots().stream().map(LiveHands.EquipmentSlot::id).toList(),
                    ref.id(), sourceToken, moving).isPresent();
        }
        if (ref.kind() == StripSlotRef.Kind.HAND)
            return t.token(ref.id()).filter(sourceToken::equals).isPresent() && t.reservation(ref.id()).isEmpty();
        var snapshot = LiveHands.equipmentSnapshot(target).orElse(null);
        if (snapshot == null) return false;
        var slot = snapshot.slots().stream().filter(s -> s.id().equals(ref.id())).findFirst().orElse(null);
        return slot != null && slot.token().filter(sourceToken::equals).isPresent()
                && BodyEquipmentTransfer.templateForTransfer(target) != null
                && t.removeEquipmentToExternal(t.revision(), snapshot.slots().stream().map(LiveHands.EquipmentSlot::id).toList(),
                BodyEquipmentTransfer.templateForTransfer(target), ref.id(), sourceToken).isPresent();
    }

    private static boolean baseValid(LivingEntity actor, LivingEntity target, StripSlotRef ref,
                                      String token, BooleanSupplier valid) {
        if (actor == null || target == null || actor == target || ref == null || token == null || valid == null
                || !(actor.level() instanceof ServerLevel level) || target.level() != level || level.getServer() == null
                || !level.getServer().isSameThread() || !live(actor, level) || !live(target, level)
                || !valid.getAsBoolean() || !CharacterControlSystem.canAct(actor)) return false;
        var policy = StripCapability.resolve(actor, target);
        return policy.actorCanOpen() && (ref.kind() == StripSlotRef.Kind.HAND
                ? policy.targetHandsCanBeStripped() : policy.targetInventoryCanBeStripped());
    }

    static Result transferOwned(LivingEntity actorBody, LivingEntity targetBody, StripSlotRef slot,
                                boolean insertion, String sourceToken, BooleanSupplier authorityValid, Committer committer) {
        return LiveHandsCommitGateway.batch(() -> transfer(actorBody, targetBody, slot, insertion,
                sourceToken, authorityValid, committer));
    }

    private static Result transfer(LivingEntity actor, LivingEntity target, StripSlotRef ref,
                                   boolean insertion, String sourceToken, BooleanSupplier valid, Committer committer) {
        if (!baseValid(actor, target, ref, sourceToken, valid)) return Result.DENIED;
        ServerLevel level = (ServerLevel) actor.level();
        LiveHands a = existing(actor), t = existing(target);
        if (a == null || t == null || !a.compatible(actor) || !t.compatible(target)) return Result.DENIED;
        if (ref.kind() == StripSlotRef.Kind.HAND && !t.handIds().contains(ref.id())) return Result.DENIED;
        LiveHands na = a, nt = t;
        ItemStack moving;
        String token;
        var snapshot = LiveHands.equipmentSnapshot(target).orElse(null);
        if (insertion) {
            String sourceHand = sourceHandForToken(a, sourceToken);
            if (sourceHand == null || !a.activeHand().equals(sourceHand)
                    || a.reservation(sourceHand).isPresent()) return Result.DENIED;
            moving = a.stackCopy(sourceHand).orElse(null);
            token = sourceToken;
            if (moving == null || moving.isEmpty()) return Result.DENIED;
            if (ref.kind() == StripSlotRef.Kind.HAND) {
                var result = t.putWhole(t.revision(), ref.id(), new ItemToken(token), moving);
                if (!result.succeeded()) return Result.DENIED;
                nt = result.state();
                na = a.takeWhole(a.revision(), sourceHand, new ItemToken(token)).state();
            } else {
                var template = BodyEquipmentTransfer.templateForTransfer(target);
                if (snapshot == null || template == null) return Result.DENIED;
                var occupied = snapshot.slots().stream().filter(s -> s.stack().isPresent())
                        .map(LiveHands.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
                if (!BodyEquipmentTransfer.fitsForTransfer(level, template, snapshot, ref.id(), moving, occupied)) return Result.DENIED;
                nt = t.insertEquipment(t.revision(), snapshot.slots().stream().map(LiveHands.EquipmentSlot::id).toList(), ref.id(), token, moving).orElse(null);
                if (nt == null) return Result.DENIED;
                na = a.takeWhole(a.revision(), sourceHand, new ItemToken(token)).state();
            }
        } else {
            if (ref.kind() == StripSlotRef.Kind.HAND) {
                token = t.token(ref.id()).orElse(null); moving = t.stackCopy(ref.id()).orElse(null);
                if (token == null || !token.equals(sourceToken) || moving == null || t.reservation(ref.id()).isPresent()) return Result.DENIED;
                nt = t.takeWhole(t.revision(), ref.id(), new ItemToken(token)).state();
            } else {
                if (snapshot == null) return Result.DENIED;
                var slot = snapshot.slots().stream().filter(s -> s.id().equals(ref.id())).findFirst().orElse(null);
                if (slot == null || slot.token().isEmpty() || !slot.token().orElseThrow().equals(sourceToken)) return Result.DENIED;
                token = slot.token().orElseThrow(); moving = slot.stack().orElseThrow();
                var removed = t.removeEquipmentToExternal(t.revision(), snapshot.slots().stream().map(LiveHands.EquipmentSlot::id).toList(),
                        BodyEquipmentTransfer.templateForTransfer(target), ref.id(), token).orElse(null);
                if (removed == null) return Result.DENIED;
                nt = removed.state();
                var receiver = firstFreeHand(a);
                if (receiver != null) na = a.putWhole(a.revision(), receiver, new ItemToken(token), moving).state();
                var staged = BodyEquipmentTransfer.stageDependentDrops(target, removed.drops());
                if (staged.result() != BodyEquipmentTransfer.Result.SUCCESS) return map(staged.result());
                com.juicyslew.moonstation14.entities.LooseItemEntity mainDrop = null;
                if (receiver == null) {
                    mainDrop = BodyHandItemTransfer.spawnTrustedGrant(actor, moving);
                    if (mainDrop == null) return staged.cleanup() ? Result.DENIED : Result.RECOVERY_REQUIRED;
                }
                Result result = commit(actor, target, a, na, t, nt, moving, token, valid, staged, committer);
                if (result == Result.DENIED && mainDrop != null && mainDrop.isAddedToLevel()) mainDrop.discard();
                return result;
            }
            String hand = firstFreeHand(a);
            if (hand != null) na = a.putWhole(a.revision(), hand, new ItemToken(token), moving).state();
            else return worldDrop(actor, target, a, na, t, nt, moving, token, valid, committer);
        }
        return commit(actor, target, a, na, t, nt, moving, token, valid, null, committer);
    }

    private static Result commit(LivingEntity actor, LivingEntity target, LiveHands a, LiveHands na,
                                 LiveHands t, LiveHands nt, ItemStack moving, String token,
                                 BooleanSupplier valid, BodyEquipmentTransfer.DropStage staged, Committer committer) {
        if (!valid.getAsBoolean() || !CharacterControlSystem.canAct(actor) || !live(actor, (ServerLevel) actor.level())
                || !live(target, (ServerLevel) actor.level()) || actor.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != a
                || target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != t || !a.compatible(actor) || !t.compatible(target))
            return cleanup(staged) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        try {
            // Full-hand removals may intentionally leave the actor snapshot unchanged and stage
            // the root item as a world drop. Do not treat that no-op owner as a failed first write.
            if (na == a) {
                if (existing(actor) != a) return Result.RECOVERY_REQUIRED;
            } else {
                committer.commit(actor, a, na);
            }
            LiveHands afterFirstA = existing(actor), afterFirstT = existing(target);
            if (na != a && afterFirstA == a && afterFirstT == t)
                return cleanup(staged) ? Result.DENIED : Result.RECOVERY_REQUIRED;
            if (afterFirstA != na || afterFirstT != t) return Result.RECOVERY_REQUIRED;
            if (!valid.getAsBoolean() || !live(target, (ServerLevel) actor.level()))
                return resolveOutcome(actor, target, a, na, t, nt, staged, committer);
            committer.commit(target, t, nt);
            return resolveOutcome(actor, target, a, na, t, nt, staged, committer);
        } catch (RuntimeException failure) {
            return resolveOutcome(actor, target, a, na, t, nt, staged, committer);
        }
    }

    private static Result resolveOutcome(LivingEntity actor, LivingEntity target, LiveHands a, LiveHands na,
                                         LiveHands t, LiveHands nt, BodyEquipmentTransfer.DropStage staged,
                                         Committer committer) {
        LiveHands currentA = existing(actor), currentT = existing(target);
        if (currentA == na && currentT == nt) return Result.SUCCESS;
        if (currentA == a && currentT == t) return cleanup(staged) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        if (currentA == na && currentT == t) {
            LiveHands restored = na.restoreContents(na.revision(), a).orElse(null);
            if (restored != null) {
                try {
                    committer.commit(actor, na, restored);
                } catch (RuntimeException ignored) { }
                if (existing(actor) == restored && existing(target) == t)
                    return cleanup(staged) ? Result.DENIED : Result.RECOVERY_REQUIRED;
            }
        }
        return Result.RECOVERY_REQUIRED;
    }

    private static Result worldDrop(LivingEntity actor, LivingEntity target, LiveHands a, LiveHands na,
                                    LiveHands t, LiveHands nt, ItemStack stack, String token, BooleanSupplier valid,
                                    Committer committer) {
        var entity = BodyHandItemTransfer.spawnTrustedGrant(actor, stack);
        if (entity == null) return Result.DENIED;
        Result result = commit(actor, target, a, na, t, nt, stack, token, valid, null, committer);
        if (result == Result.DENIED && entity.isAddedToLevel()) entity.discard();
        return result;
    }
    private static boolean cleanup(BodyEquipmentTransfer.DropStage stage) { return stage == null || stage.cleanup(); }
    private static Result map(BodyEquipmentTransfer.Result result) { return result == BodyEquipmentTransfer.Result.SUCCESS ? Result.SUCCESS : result == BodyEquipmentTransfer.Result.DENIED ? Result.DENIED : Result.RECOVERY_REQUIRED; }
    private static String sourceHandForToken(LiveHands state, String token) { for (String id : state.handIds()) if (state.token(id).filter(token::equals).isPresent()) return id; return null; }
    private static String firstFreeHand(LiveHands state) {
        if (state.stackCopy(state.activeHand()).isEmpty() && state.reservation(state.activeHand()).isEmpty()) return state.activeHand();
        for (String id : state.handIds()) if (state.stackCopy(id).isEmpty() && state.reservation(id).isEmpty()) return id;
        return null;
    }
    private static LiveHands existing(LivingEntity body) { return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static boolean live(LivingEntity body, ServerLevel level) { return body.level() == level && body.isAlive() && !body.isRemoved() && body.isAddedToLevel() && level.getEntity(body.getUUID()) == body; }
}

