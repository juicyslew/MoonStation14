package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.character.components.PullableComponent;
import com.juicyslew.moonstation14.ms14.character.components.PullerComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
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
    private static final Map<LivingEntity, Pull> PULLS = new WeakHashMap<>();
    private static final Map<Entity, Boolean> TETHER_ENDPOINTS = new java.util.IdentityHashMap<>();
    private BodyPullService() { }

    public static void register(IEventBus bus) {
        bus.addListener(BodyPullService::onPreServerTick);
        bus.addListener(BodyPullService::onPostServerTick);
    }

    public static Result toggle(ServerPlayer actor, UUID targetId, String hand, long expectedRevision) {
        var admitted = HandActorAuthority.resolve(actor);
        if (admitted.isEmpty() || targetId == null || hand == null) return Result.DENIED;
        var authority = admitted.orElseThrow();
        LivingEntity body = authority.body();
        return toggle(body, targetId, hand, expectedRevision,
                () -> HandActorAuthority.revalidate(actor, authority), actor, authority);
    }

    /** Narrow native-player route: client supplies only a target; hand/revision are server selected. */
    public static Result toggleNative(ServerPlayer owner, UUID targetId) {
        var controlled = HandActorAuthority.resolve(owner);
        if (controlled.isPresent()) {
            var authority = controlled.orElseThrow();
            LivingEntity body = authority.body();
            Pull active = PULLS.get(body);
            if (targetId == null) return active != null && active.actor == owner
                    ? (stopPull(active) ? Result.SUCCESS : Result.RECOVERY_REQUIRED) : Result.DENIED;
            if (active != null) return Result.DENIED;
            LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (hands == null || !hands.compatible(body)) return Result.RECOVERY_REQUIRED;
            String free = hands.handIds().stream().filter(id -> hands.stackCopy(id).isEmpty()
                    && hands.reservation(id).isEmpty()).findFirst().orElse(null);
            return free == null ? Result.DENIED : toggle(body, targetId, free, hands.revision(),
                    () -> HandActorAuthority.revalidate(owner, authority), owner, authority);
        }
        if (!nativeOwner(owner)) return Result.DENIED;
        Pull existing = PULLS.get(owner);
        if (targetId == null) return existing != null && existing.body == owner
                ? (stopPull(existing) ? Result.SUCCESS : Result.RECOVERY_REQUIRED) : Result.DENIED;
        if (existing != null) return Result.DENIED;
        LiveHands hands = LiveHands.initialize(owner).orElseGet(() -> owner.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()));
        if (hands == null || !hands.compatible(owner) || !hands.handIds().contains("left")
                || !hands.handIds().contains("right")) return Result.DENIED;
        String free = hands.handIds().stream().filter(id -> hands.stackCopy(id).isEmpty()
                && hands.reservation(id).isEmpty()
                && !com.juicyslew.moonstation14.ms14.hands.live.NativeHeldItemTransfer
                .nativeOccupiedHand(owner, id)).findFirst().orElse(null);
        if (free == null) return Result.DENIED;
        LiveHands current = hands;
        return toggle(owner, targetId, free, current.revision(), () -> nativeOwner(owner), null, null);
    }

    private static boolean nativeOwner(ServerPlayer owner) {
        return owner != null && !(owner instanceof FakePlayer) && owner.isAlive() && !owner.isRemoved()
                && !owner.isSpectator() && owner.getServer() != null
                && owner.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner
                && owner.getType().builtInRegistryHolder().key().location().toString().equals("minecraft:player")
                && !ActiveCharacterPolicy.isCarrier(owner)
                && ActiveCharacterPolicy.resolveActor(owner).flatMap(data -> data.component(PullerComponent.class)).isPresent()
                && CharacterControlSystem.canAct(owner);
    }

    /** Trusted fixture route for live world tests; deliberately not part of network/API admission. */
    static Result toggleFixture(LivingEntity body, UUID targetId, String hand, long expectedRevision,
                                BooleanSupplier authorityValid) {
        return toggle(body, targetId, hand, expectedRevision, authorityValid, null, null);
    }

    private static Result toggle(LivingEntity body, UUID targetId, String hand, long expectedRevision,
                                 BooleanSupplier authorityValid, ServerPlayer actor,
                                 HandActorAuthority.Snapshot authority) {
        return LiveHandsCommitGateway.batch(() -> toggleCommitted(body, targetId, hand, expectedRevision,
                authorityValid, actor, authority));
    }

    private static Result toggleCommitted(LivingEntity body, UUID targetId, String hand, long expectedRevision,
                                 BooleanSupplier authorityValid, ServerPlayer actor,
                                 HandActorAuthority.Snapshot authority) {
        if (body == null || targetId == null || hand == null || authorityValid == null
                || ActiveCharacterPolicy.resolveActor(body).filter(data -> data.component(PullerComponent.class).isPresent()).isEmpty()) return Result.DENIED;
        Pull existing = PULLS.get(body);
        if (existing != null) {
            LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (!existing.targetId.equals(targetId) || !existing.hand.equals(hand)
                    || current == null || !current.compatible(body) || current.revision() != expectedRevision
                    || !current.reservation(hand).equals(java.util.Optional.of(targetId))) return Result.DENIED;
            return stopPull(existing) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
        }
        if (!CharacterControlSystem.canAct(body) || !(body.level() instanceof ServerLevel level)
                || probeSelected(level, body, targetId) == null) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body)) return Result.RECOVERY_REQUIRED;
        LiveHands next = before.reserveHand(expectedRevision, hand, targetId).orElse(null);
        if (next == null || PULLS.values().stream().anyMatch(p -> p.targetId.equals(targetId))) return Result.DENIED;
        if (!authorityValid.getAsBoolean() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before)
            return Result.DENIED;
        LiveHandsCommitGateway.commitOwned(body, next);
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != next || !next.compatible(body)
                || !next.reservation(hand).equals(java.util.Optional.of(targetId))) return Result.RECOVERY_REQUIRED;
        Entity target = level.getEntity(targetId);
        if (!eligibleTarget(target) || !authorityValid.getAsBoolean()
                || !CharacterControlSystem.canAct(body) || body.level() != level
                || probeSelected(level, body, targetId) == null
                || level.getEntity(targetId) != target || !eligibleTarget(target)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != next
                || !next.reservation(hand).equals(java.util.Optional.of(targetId))) {
            release(body, hand, targetId); return Result.DENIED;
        }
        var resolvedTether = ActiveCharacterPolicy.resolveActor(body).flatMap(data -> data.component(PullerComponent.class))
                .map(PullerComponent::tether).orElseThrow();
        PULLS.put(body, new Pull(actor, authority, authorityValid, body, level, targetId, target, hand,
                body.position().distanceTo(target.position()) + resolvedTether.slackAllowance(), resolvedTether, next.revision()));
        rebuildAttachments();
        PostureSystem.refreshMovementAvailability(body, true);
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
    /** A conscious pulled body can resist its single active lease with an actual directional wish. */
    public static void onMovementInput(LivingEntity target, boolean directionalWish) {
        if (!directionalWish || target == null || !CharacterControlSystem.canAct(target)) return;
        for (Pull pull : PULLS.values()) {
            if (pull.target != target) continue;
            stopPull(pull);
            return;
        }
    }
    /** Domain cleanup for authoritative medical transitions; never admits a user-requested action. */
    public static void stopForIncapacitation(LivingEntity body) {
        Pull pull = PULLS.get(body);
        if (pull != null) stopPull(pull);
    }
    public static double movementMultiplier(LivingEntity body) { return movementMultiplier(body, false); }
    public static double movementMultiplier(LivingEntity body, boolean sprint) {
        if (body == null || !(body.level().isClientSide ? posturePulling(body) : isPulling(body))) return 1d;
        var actor = body.level().isClientSide
                ? com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(body)
                : ActiveCharacterPolicy.resolveActor(body);
        return actor.flatMap(data -> data.component(PullerComponent.class))
                .map(component -> sprint ? component.sprintModifier() : component.walkModifier()).orElse(1d);
    }
    private static boolean posturePulling(LivingEntity body) {
        var posture = body.getExistingDataOrNull(ModDataAttachments.POSTURE.get());
        return posture != null && posture.data().pulling();
    }

    public static void onPreServerTick(ServerTickEvent.Pre event) { rebuildAttachments(); }

    private static void rebuildAttachments() {
        Map<Entity, java.util.List<RopeConstraintAttachment.Constraint>> endpoints = new java.util.IdentityHashMap<>();
        for (Pull pull : PULLS.values()) {
            if (pull.body.isRemoved() || pull.target.isRemoved() || pull.body.level() != pull.level
                    || pull.target.level() != pull.level || !pull.body.isAlive()) continue;
            var tuning = pull.tuning;
            if (tuning == null) continue;
            endpoints.computeIfAbsent(pull.body, ignored -> new java.util.ArrayList<>()).add(
                    new RopeConstraintAttachment.Constraint(pull.targetId,
                            new RopeConstraintAttachment.MotionSnapshot(pull.body.position(), pull.body.getDeltaMovement().scale(20)),
                            new RopeConstraintAttachment.MotionSnapshot(pull.target.position(), pull.target.getDeltaMovement().scale(20)),
                            pull.maxDistance, tuning, RopeConstraintAttachment.EndpointRole.PULLER));
            endpoints.computeIfAbsent(pull.target, ignored -> new java.util.ArrayList<>()).add(
                    new RopeConstraintAttachment.Constraint(pull.body.getUUID(),
                            new RopeConstraintAttachment.MotionSnapshot(pull.target.position(), pull.target.getDeltaMovement().scale(20)),
                            new RopeConstraintAttachment.MotionSnapshot(pull.body.position(), pull.body.getDeltaMovement().scale(20)),
                            pull.maxDistance, tuning, RopeConstraintAttachment.EndpointRole.PULLED));
        }
        java.util.Set<Entity> all = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        all.addAll(TETHER_ENDPOINTS.keySet()); all.addAll(endpoints.keySet());
        TETHER_ENDPOINTS.clear();
        for (Entity entity : all) {
            setEndpoint(entity, endpoints.get(entity));
            if (!entity.isRemoved() && endpoints.containsKey(entity)) TETHER_ENDPOINTS.put(entity, Boolean.TRUE);
        }
    }

    private static void setEndpoint(Entity entity, java.util.List<RopeConstraintAttachment.Constraint> constraints) {
        if (entity == null || entity.isRemoved()) return;
        if (constraints == null || constraints.isEmpty()) {
            if (entity.hasData(ModDataAttachments.ROPE_CONSTRAINT.get())) entity.removeData(ModDataAttachments.ROPE_CONSTRAINT.get());
        } else {
            constraints.sort(java.util.Comparator.comparing(c -> c.other().toString()));
            entity.setData(ModDataAttachments.ROPE_CONSTRAINT.get(), new RopeConstraintAttachment(constraints));
        }
    }

    public static void onPostServerTick(ServerTickEvent.Post event) {
        Iterator<Pull> iterator = PULLS.values().iterator();
        while (iterator.hasNext()) {
            Pull pull = iterator.next();
            if (!pull.authorityValid.getAsBoolean()
                    || pull.body.level() != pull.level || pull.body.isRemoved() || !pull.body.isAlive()
                    || pull.level.getChunkSource().getChunkNow(pull.body.blockPosition().getX() >> 4,
                    pull.body.blockPosition().getZ() >> 4) == null
                    || pull.level.getChunkSource().getChunkNow(pull.target.blockPosition().getX() >> 4,
                    pull.target.blockPosition().getZ() >> 4) == null
                    || !CharacterControlSystem.canAct(pull.body)
                    || pull.target.isRemoved() || pull.target.level() != pull.level
                    || pull.level.getEntity(pull.targetId) != pull.target || !eligibleTarget(pull.target)) {
                if (release(pull.body, pull.hand, pull.targetId)
                        || pull.body.isRemoved() || !pull.body.isAlive()) iterator.remove();
                continue;
            }
            LiveHands state = pull.body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (state == null || !state.reservation(pull.hand).equals(java.util.Optional.of(pull.targetId))) {
                release(pull.body, pull.hand, pull.targetId);
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
            if (pull.actor == null && pull.body instanceof ServerPlayer player
                    && com.juicyslew.moonstation14.ms14.hands.live.NativeHeldItemTransfer
                    .nativeOccupiedHand(player, pull.hand)) {
                if (release(pull.body, pull.hand, pull.targetId)) iterator.remove();
                continue;
            }
        }
        rebuildAttachments();
    }

    private static boolean stopPull(Pull pull) {
        PULLS.remove(pull.body, pull);
        boolean released = release(pull.body, pull.hand, pull.targetId);
        rebuildAttachments();
        return released;
    }

    private static boolean release(LivingEntity body, String hand, UUID target) {
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null) { PostureSystem.refreshMovementAvailability(body, false); return true; }
        if (!before.reservation(hand).equals(java.util.Optional.of(target))) {
            PostureSystem.refreshMovementAvailability(body, false);
            return true;
        }
        LiveHands next = before.releaseHand(hand, target).orElse(null);
        if (next == null) return false;
        LiveHandsCommitGateway.commitOwned(body, next);
        boolean released = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next
                && next.reservation(hand).isEmpty();
        if (released) PostureSystem.refreshMovementAvailability(body, false);
        return released;
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    private static boolean eligibleTarget(Entity target) {
        if (target instanceof LooseItemEntity loose) return !loose.isRemoved() && loose.getTarget() == null;
        return target instanceof LivingEntity living && !living.isRemoved()
                && ActiveCharacterPolicy.resolveActor(living)
                .filter(data -> data.component(PullableComponent.class).isPresent()).isPresent();
    }

    private static Entity probeSelected(ServerLevel level, LivingEntity body, UUID targetId) {
        Entity target = level.getEntity(targetId);
        if (!eligibleTarget(target) || target.level() != level || target.isRemoved() || target == body
                || target instanceof LooseItemEntity && horizontalDistance(body.position(), target.position()) > PICKUP_REACH
                || !(target instanceof LooseItemEntity
                && BodyItemTargetProbe.probeSelected(level, body, targetId, PICKUP_REACH) != null)
                && !(target instanceof LivingEntity && visibleLivingTarget(level, body, target))) return null;
        return target;
    }

    private static boolean visibleLivingTarget(ServerLevel level, LivingEntity body, Entity target) {
        Vec3 eye = body.getEyePosition();
        var box = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of((LivingEntity) target);
        Vec3 end = eye.add(body.getLookAngle().scale(PICKUP_REACH));
        Vec3 point = com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay
                .hit(box, eye, end, PICKUP_REACH, Double.POSITIVE_INFINITY).orElse(null);
        if (point == null) return false;
        int minX = Math.min(body.blockPosition().getX(), net.minecraft.core.BlockPos.containing(point).getX()) >> 4;
        int maxX = Math.max(body.blockPosition().getX(), net.minecraft.core.BlockPos.containing(point).getX()) >> 4;
        int minZ = Math.min(body.blockPosition().getZ(), net.minecraft.core.BlockPos.containing(point).getZ()) >> 4;
        int maxZ = Math.max(body.blockPosition().getZ(), net.minecraft.core.BlockPos.containing(point).getZ()) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, point,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, body));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                && eligibleTarget(target) && target.level() == level && !target.isRemoved();
    }

    private record Pull(ServerPlayer actor, HandActorAuthority.Snapshot authority, BooleanSupplier authorityValid, LivingEntity body,
                        ServerLevel level, UUID targetId, Entity target, String hand,
                        double maxDistance, com.juicyslew.moonstation14.ms14.character.components.TetherSettings tuning,
                        long reservedRevision) { }
}
