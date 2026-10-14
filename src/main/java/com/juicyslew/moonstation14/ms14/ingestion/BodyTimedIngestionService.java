package com.juicyslew.moonstation14.ms14.ingestion;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Starts authoritative, repeating timed ingestion operations for a controlled body. */
public final class BodyTimedIngestionService {
    private static final String KIND = "ingestion";

    private BodyTimedIngestionService() { }

    public static BodyHandActionResult.Reason startHeld(ServerPlayer actor, String hand, String token,
                                                         long revision, long sequence) {
        var snapshot = HandActorAuthority.resolve(actor).orElse(null);
        if (snapshot == null) return BodyHandActionResult.Reason.NO_AUTHORITY;
        BooleanSupplier authority = () -> HandActorAuthority.revalidate(actor, snapshot);
        var hands = snapshot.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        var stack = hands == null ? null : hands.stackCopy(hand).orElse(null);
        IngestionProfile policyProfile = drinkProfile(snapshot.body(), stack);
        return start(snapshot.body(), actor, sequence, authority,
                () -> TimedIngestionAccess.admitHeld(snapshot.body(), hand, token, revision, authority), "held:" + token,
                () -> intentValid(snapshot.body(), authority, hand, revision, token, true, false), policyProfile);
    }

    public static BodyHandActionResult.Reason startWorld(ServerPlayer actor, UUID target, String hand,
                                                          long revision, long sequence) {
        var snapshot = HandActorAuthority.resolve(actor).orElse(null);
        if (snapshot == null) return BodyHandActionResult.Reason.NO_AUTHORITY;
        BooleanSupplier authority = () -> HandActorAuthority.revalidate(actor, snapshot);
        return start(snapshot.body(), actor, sequence, authority,
                () -> TimedIngestionAccess.admitWorld(snapshot.body(), target, hand, revision, authority), "world:" + target,
                () -> intentValid(snapshot.body(), authority, hand, revision, null, false, target != null), null);
    }

    public static BodyHandActionResult.Reason startPuddle(ServerPlayer actor, BlockPos pos, String hand,
                                                            long revision, long sequence) {
        var snapshot = HandActorAuthority.resolve(actor).orElse(null);
        if (snapshot == null) return BodyHandActionResult.Reason.NO_AUTHORITY;
        BooleanSupplier authority = () -> HandActorAuthority.revalidate(actor, snapshot);
        String key = puddleKey(snapshot.body(), pos);
        return start(snapshot.body(), actor, sequence, authority,
                () -> TimedIngestionAccess.admitPuddle(snapshot.body(), pos, hand, revision, authority), key,
                () -> intentValid(snapshot.body(), authority, hand, revision, null, false, pos != null
                        && snapshot.body().level() instanceof net.minecraft.server.level.ServerLevel l && l.isInWorldBounds(pos)), null);
    }

    public static BodyHandActionResult.Reason startHeldOwned(LivingEntity body, String hand, String token,
                                                              long revision, long sequence, BooleanSupplier authority) {
        return start(body, null, sequence, authority,
                () -> TimedIngestionAccess.admitHeld(body, hand, token, revision, authority), "held:" + token,
                () -> intentValid(body, authority, hand, revision, token, true, false),
                drinkProfile(body, body == null || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == null
                        ? null : body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(hand).orElse(null)));
    }

    public static BodyHandActionResult.Reason startWorldOwned(LivingEntity body, UUID target, String hand,
                                                               long revision, long sequence, BooleanSupplier authority) {
        return start(body, null, sequence, authority,
                () -> TimedIngestionAccess.admitWorld(body, target, hand, revision, authority), "world:" + target,
                () -> intentValid(body, authority, hand, revision, null, false, target != null), null);
    }

    public static BodyHandActionResult.Reason startPuddleOwned(LivingEntity body, BlockPos pos, String hand,
                                                                long revision, long sequence, BooleanSupplier authority) {
        return start(body, null, sequence, authority,
                () -> TimedIngestionAccess.admitPuddle(body, pos, hand, revision, authority), puddleKey(body, pos),
                () -> intentValid(body, authority, hand, revision, null, false, pos != null
                        && body != null && body.level() instanceof net.minecraft.server.level.ServerLevel l && l.isInWorldBounds(pos)), null);
    }

    private static BodyHandActionResult.Reason start(LivingEntity body, ServerPlayer owner, long sequence,
            BooleanSupplier authority, java.util.function.Supplier<java.util.Optional<TimedIngestionAccess.Admission>> admissionSupplier,
            String sourceKey, BooleanSupplier intent, IngestionProfile overrideProfile) {
        if (body == null || authority == null || sourceKey == null || intent == null || !intent.getAsBoolean()) return BodyHandActionResult.Reason.DENIED;
        if (TimedInteractionSystem.cancelDuplicate(body, KIND, sourceKey)) {
            fresh(owner);
            return BodyHandActionResult.Reason.OK;
        }
        var admission = admissionSupplier.get();
        if (admission.isEmpty() || !ModIngestionProfiles.serverReady()) return BodyHandActionResult.Reason.DENIED;
        TimedIngestionAccess.Admission admitted = admission.orElseThrow();
        IngestionProfile profile = overrideProfile != null ? overrideProfile : ModIngestionProfiles.serverLookup(admitted.profileId());
        if (profile == null) return BodyHandActionResult.Reason.DENIED;
        var result = TimedInteractionSystem.start(body, owner, sequence, KIND, admitted.sourceKey(),
                profile.delayTicks(), true, false,
                () -> TimedIngestionAccess.valid(body, admitted, authority),
                 () -> switch (TimedIngestionAccess.complete(body, admitted, profile.doseCents(), profile.destroyOnEmpty(), authority,
                         cents -> IngestionReceiptObserver.publish(new IngestionReceiptObserver.Receipt(
                                 owner == null ? null : owner.getUUID(), body.getUUID(), admitted.sourceKey(), cents)))) {
                    case COMMITTED_REPEAT -> { committed(owner, snapshot(body, owner), body, authority); yield TimedInteractionSystem.Completion.REPEAT; }
                    case COMMITTED_STOP -> { committed(owner, snapshot(body, owner), body, authority); yield TimedInteractionSystem.Completion.STOP; }
                    case DENIED -> TimedInteractionSystem.Completion.DENIED;
                    case RECOVERY_REQUIRED -> TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
                });
        if (result == TimedInteractionEngine.StartResult.STARTED || result == TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED) {
            fresh(owner);
            return BodyHandActionResult.Reason.OK;
        }
        return BodyHandActionResult.Reason.DENIED;
    }

    private static boolean intentValid(LivingEntity body, BooleanSupplier authority, String hand, long revision,
                                       String token, boolean held, boolean sourceIdentityValid) {
        if (body == null || authority == null || hand == null || hand.isBlank() || revision < 0
                || held && token == null || !held && !sourceIdentityValid
                || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || body.isRemoved() || !body.isAlive()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body || !authority.getAsBoolean()) return false;
        if (held) try { new ItemToken(token); } catch (RuntimeException ex) { return false; }
        LiveHands hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || hands.revision() != revision || !hands.activeHand().equals(hand)) return false;
        if (held) return hands.token(hand).filter(token::equals).isPresent();
        return hands.stackCopy(hand).isEmpty() && hands.reservation(hand).isEmpty();
    }

    private static IngestionProfile drinkProfile(LivingEntity body, net.minecraft.world.item.ItemStack stack) {
        if (body == null || stack == null || !(stack.getItem() instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait trait)) return null;
        var policy = trait.drinkSolution(stack, body.level());
        if (policy == null || !policy.enabled() || !trait.solutionName(stack, body.level()).equals(policy.solutionName())) return null;
        try { return new IngestionProfile(new IngestionProfile.Edible(
                Math.toIntExact(com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(policy.consumeAmount())),
                policy.personalDelayTicks(), false)); }
        catch (RuntimeException invalid) { return null; }
    }

    private static void fresh(ServerPlayer actor) {
        if (actor != null) BodyHandStateService.requireFreshQuery(actor);
    }

    private static HandActorAuthority.Snapshot snapshot(LivingEntity body, ServerPlayer owner) {
        return owner == null ? null : HandActorAuthority.resolve(owner).filter(s -> s.body() == body).orElse(null);
    }

    private static void committed(ServerPlayer owner, HandActorAuthority.Snapshot current, LivingEntity body, BooleanSupplier authority) {
        if (owner == null || current == null || current.body() != body || !authority.getAsBoolean()) return;
        BodyHandStateService.ingestionCommitted(owner, current.body().getUUID(), current.epoch());
    }

    private static String puddleKey(LivingEntity body, BlockPos pos) {
        if (body == null || pos == null || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)) return null;
        return "puddle:" + level.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}

