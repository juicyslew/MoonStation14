package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Server-owned, transient pull leases for loose world items. */
public final class BodyPullService {
    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }
    private static final double PICKUP_REACH = 5.0;
    private static final double BOUNDED_LAG_MARGIN = 1.5;
    private static final Map<LivingEntity, Pull> PULLS = new WeakHashMap<>();
    private BodyPullService() { }

    public static void register(IEventBus bus) { bus.addListener(BodyPullService::onPostServerTick); }

    public static Result toggle(ServerPlayer actor, UUID targetId, String hand, long expectedRevision) {
        var admitted = HandActorAuthority.resolve(actor);
        if (admitted.isEmpty() || targetId == null || hand == null) return Result.DENIED;
        var authority = admitted.orElseThrow();
        LivingEntity body = authority.body();
        return toggle(body, targetId, hand, expectedRevision,
                () -> HandActorAuthority.revalidate(actor, authority), actor, authority);
    }

    /** Trusted fixture route for live world tests; deliberately not part of network/API admission. */
    static Result toggleFixture(LivingEntity body, UUID targetId, String hand, long expectedRevision,
                                BooleanSupplier authorityValid) {
        return toggle(body, targetId, hand, expectedRevision, authorityValid, null, null);
    }

    private static Result toggle(LivingEntity body, UUID targetId, String hand, long expectedRevision,
                                 BooleanSupplier authorityValid, ServerPlayer actor,
                                 HandActorAuthority.Snapshot authority) {
        if (body == null || targetId == null || hand == null || authorityValid == null) return Result.DENIED;
        Pull existing = PULLS.get(body);
        if (existing != null) {
            LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (!existing.targetId.equals(targetId) || !existing.hand.equals(hand)
                    || current == null || !current.compatible(body) || current.revision() != expectedRevision
                    || !current.reservation(hand).equals(java.util.Optional.of(targetId))) return Result.DENIED;
            return stopPull(existing) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
        }
        if (!CharacterControlSystem.canAct(body) || !(body.level() instanceof ServerLevel level)
                || BodyItemTargetProbe.probeSelected(level, body, targetId, PICKUP_REACH) == null) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body)) return Result.RECOVERY_REQUIRED;
        LiveHands next = before.reserveHand(expectedRevision, hand, targetId).orElse(null);
        if (next == null || PULLS.values().stream().anyMatch(p -> p.targetId.equals(targetId))) return Result.DENIED;
        if (!authorityValid.getAsBoolean() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before)
            return Result.DENIED;
        body.setData(ModDataAttachments.LIVE_HANDS.get(), next);
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != next || !next.compatible(body)
                || !next.reservation(hand).equals(java.util.Optional.of(targetId))) return Result.RECOVERY_REQUIRED;
        ItemEntity item = level.getEntity(targetId) instanceof LooseItemEntity entity ? entity : null;
        if (item == null || !authorityValid.getAsBoolean()
                || !CharacterControlSystem.canAct(body) || body.level() != level
                || BodyItemTargetProbe.probeSelected(level, body, targetId, PICKUP_REACH) == null
                || level.getEntity(targetId) != item || item.isRemoved() || item.getTarget() != null
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != next
                || !next.reservation(hand).equals(java.util.Optional.of(targetId))) {
            release(body, hand, targetId); return Result.DENIED;
        }
        PULLS.put(body, new Pull(actor, authority, authorityValid, body, level, targetId, item, hand,
                horizontalDistance(body.position(), item.position()) + .15, next.revision()));
        return Result.SUCCESS;
    }

    public static Result stop(ServerPlayer actor) {
        var snapshot = HandActorAuthority.resolve(actor);
        if (snapshot.isEmpty()) return Result.DENIED;
        Pull pull = PULLS.get(snapshot.orElseThrow().body());
        if (pull == null || pull.actor != actor) return Result.DENIED;
        return stopPull(pull) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    /** Trusted fixture stop; authenticated callers must use {@link #stop(ServerPlayer)}. */
    static Result stopFixture(LivingEntity body) {
        Pull pull = PULLS.get(body);
        return pull == null ? Result.DENIED : stopPull(pull) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    public static boolean isPulling(LivingEntity body) { return PULLS.containsKey(body); }
    public static double movementMultiplier(LivingEntity body) {
        return isPulling(body) ? com.juicyslew.moonstation14.ms14.movement.PullMovementPolicy.MOVEMENT_FACTOR : 1.0;
    }

    public static void onPostServerTick(ServerTickEvent.Post event) {
        Iterator<Pull> iterator = PULLS.values().iterator();
        while (iterator.hasNext()) {
            Pull pull = iterator.next();
            if (!pull.authorityValid.getAsBoolean()
                    || pull.body.level() != pull.level || pull.body.isRemoved() || !pull.body.isAlive()
                    || pull.level.getChunkSource().getChunkNow(pull.body.blockPosition().getX() >> 4,
                    pull.body.blockPosition().getZ() >> 4) == null
                    || pull.level.getChunkSource().getChunkNow(pull.item.blockPosition().getX() >> 4,
                    pull.item.blockPosition().getZ() >> 4) == null
                    || !CharacterControlSystem.canAct(pull.body)
                    || pull.item.isRemoved() || pull.item.level() != pull.level
                    || pull.level.getEntity(pull.targetId) != pull.item || pull.item.getTarget() != null) {
                if (release(pull.body, pull.hand, pull.targetId)
                        || pull.body.isRemoved() || !pull.body.isAlive()) iterator.remove();
                continue;
            }
            LiveHands state = pull.body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (state == null || !state.reservation(pull.hand).equals(java.util.Optional.of(pull.targetId))) {
                iterator.remove(); continue;
            }
            if (!state.compatible(pull.body)) {
                // Compatibility can change independently (for example after a prototype change),
                // but this exact reservation still belongs to this pull and must be cleared.
                if (pull.body.isRemoved() || !pull.body.isAlive()) {
                    release(pull.body, pull.hand, pull.targetId);
                    iterator.remove();
                } else if (release(pull.body, pull.hand, pull.targetId)) {
                    iterator.remove();
                }
                continue;
            }
            double separation = horizontalDistance(pull.body.position(), pull.item.position());
            if (separation > pull.maxDistance + BOUNDED_LAG_MARGIN
                    || separation > PICKUP_REACH + BOUNDED_LAG_MARGIN) {
                if (release(pull.body, pull.hand, pull.targetId)
                        || pull.body.isRemoved() || !pull.body.isAlive()) iterator.remove();
                continue;
            }
            // A floor object is pulled from the body's feet plane, not upward toward its chest.
            Vec3 target = new Vec3(pull.body.getX(), pull.item.getY(), pull.body.getZ());
            Vec3 current = pull.item.position();
            Vec3 horizontalVelocity = new Vec3(pull.item.getDeltaMovement().x, 0, pull.item.getDeltaMovement().z);
            Vec3 impulse = PullConstraint.impulse(target, current,
                    horizontalVelocity, pull.maxDistance);
            if (impulse.lengthSqr() > 0) {
                if (pull.item instanceof LooseItemEntity loose) loose.applyImpulse(impulse);
            }
        }
    }

    private static boolean stopPull(Pull pull) {
        PULLS.remove(pull.body, pull);
        return release(pull.body, pull.hand, pull.targetId);
    }

    private static boolean release(LivingEntity body, String hand, UUID target) {
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null) return true;
        if (!before.reservation(hand).equals(java.util.Optional.of(target))) return true;
        LiveHands next = before.releaseHand(hand, target).orElse(null);
        if (next == null) return false;
        body.setData(ModDataAttachments.LIVE_HANDS.get(), next);
        return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next
                && next.reservation(hand).isEmpty();
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    private record Pull(ServerPlayer actor, HandActorAuthority.Snapshot authority, BooleanSupplier authorityValid, LivingEntity body,
                        ServerLevel level, UUID targetId, ItemEntity item, String hand,
                        double maxDistance, long reservedRevision) { }
}
