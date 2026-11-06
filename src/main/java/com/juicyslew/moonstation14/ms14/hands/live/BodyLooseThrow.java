package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import com.juicyslew.moonstation14.MoonStation14;

/** Transfers a complete live hand stack to an independently simulated loose world object. */
public final class BodyLooseThrow {
    private BodyLooseThrow() { }
    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }
    private static final Map<LivingEntity, Long> COOLDOWN = new WeakHashMap<>();

    public static Result throwHeld(ServerPlayer actor, String hand, String expectedToken, long expectedRevision,
                                   com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target,
                                   java.util.function.Consumer<UUID> committedWorldEntity) {
        var auth = HandActorAuthority.resolve(actor);
        if (auth.isEmpty() || expectedToken == null) { receipt(actor, "AUTHORITY", hand, null, null, null, null); return Result.DENIED; }
        var resolved = auth.orElseThrow();
        return throwOwned(resolved.body(), hand, expectedToken, expectedRevision, target,
                () -> HandActorAuthority.revalidate(actor, resolved), committedWorldEntity);
    }

    static Result throwOwned(LivingEntity body, String hand, String expectedToken, long revision,
                             com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target,
                             BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committed) {
        return LiveHandsCommitGateway.batch(() -> throwOwnedCommitted(body, hand, expectedToken, revision, target, authorityValid, committed));
    }

    private static Result throwOwnedCommitted(LivingEntity body, String hand, String expectedToken, long revision,
                              com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target,
                              BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committed) {
        if (body == null || hand == null || expectedToken == null || target == null || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !live(body, level) || !CharacterControlSystem.canAct(body) || !level.hasChunkAt(body.blockPosition()))
            return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision) return Result.DENIED;
        ItemToken token;
        try { token = new ItemToken(expectedToken); } catch (IllegalArgumentException e) { return Result.DENIED; }
        if (!before.token(hand).filter(expectedToken::equals).isPresent()) return Result.DENIED;
        long now = level.getGameTime();
        synchronized (COOLDOWN) {
            if (now < COOLDOWN.getOrDefault(body, Long.MIN_VALUE)) return Result.DENIED;
        }
        LiveHands.WholeResult transition = before.takeWhole(revision, hand, token);
        if (!transition.succeeded()) return Result.DENIED;
        ItemStack stack = transition.takenStack().orElseThrow();
        if (stack.isEmpty() || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                || !live(body, level) || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before)
            return Result.DENIED;
        Vec3 eye = body.getEyePosition(), look = body.getLookAngle(), bodyPosition = body.position();
        var releaseFramePoint = LoadedFrameRayQuery.currentActorPoint(level, body);
        Vec3 capturedWorldPoint = LoadedFrameRayQuery.resolveCapturedTarget(level, target);
        if (releaseFramePoint == null || capturedWorldPoint == null) { receipt(body, "ACTOR_FRAME_UNKNOWN", hand, stack, eye, look, null); return Result.DENIED; }
        if (!finite(look) || !finite(eye) || !finite(bodyPosition) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01) return Result.DENIED;
        LooseItemEntity entity;
        Vec3 handOrigin;
        try {
            // Aim remains eye-authoritative, but place the physical object at a conservative hand height.
            entity = LooseItemEntity.createForActor(body, bodyPosition, stack);
            LooseObjectProfile profile = entity.profile();
            double range = Math.min(8.0, profile.throwRange());
            double minimum = Math.min(range, profile.minimumThrowRange());
            Vec3 aim = BodyItemTargetProbe.loadedAimPoint(level, body, range, capturedWorldPoint);
            if (aim == null) { receipt(body, "NO_CANDIDATE_UNCLASSIFIED", hand, stack, eye, look, releaseFramePoint); return Result.DENIED; }
            double distance = eye.distanceTo(aim);
            if (!finite(aim) || distance + 1.0e-4 < minimum) return Result.DENIED;
            double handY = body.getY() + Mth.clamp(1.125, 0.0,
                    Math.max(0.0, body.getBbHeight() - profile.physicalHeight() - .075));
            handOrigin = new Vec3(body.getX(), handY, body.getZ());
            Vec3 toAim = aim.subtract(handOrigin);
            if (distance <= .1 || !finite(toAim) || toAim.lengthSqr() < 1.0e-8) return Result.DENIED;
            Vec3 launch = handOrigin.add(toAim.normalize().scale(.1));
            entity.setPos(launch.x, launch.y, launch.z);
            if (!BodyItemTargetProbe.loadedItemBoxClear(level, entity)) {
                receipt(body, "LAUNCH_BOX_REJECTED", hand, stack, eye, look, releaseFramePoint); return Result.DENIED;
            }
            Vec3 direction = aim.subtract(handOrigin);
            if (!finite(direction) || direction.lengthSqr() < 1.0e-8) return Result.DENIED;
            // Body velocity is frame-relative. Capture the SDK carrier velocity at the actual
            // launch point; the new entity's first native-frame transition applies destination correction.
            var velocityPoint = LoadedFrameRayQuery.pointAtWorldPosition(level, releaseFramePoint.frameId(), launch);
            if (velocityPoint == null) { receipt(body, "VELOCITY_POINT_UNKNOWN", hand, stack, eye, look, releaseFramePoint); return Result.DENIED; }
            Vec3 carrierVelocity = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity
                    .pointVelocityAt(level, velocityPoint);
            Vec3 launchVelocity = HandReleaseKinematics.compose(body.getDeltaMovement(), carrierVelocity,
                    direction.normalize().scale(profile.throwSpeed()));
            if (launchVelocity == null) return Result.DENIED;
            entity.launch(launchVelocity, profile.throwSpin());
            if (!eye.equals(body.getEyePosition()) || !bodyPosition.equals(body.position())
                    || !capturedWorldPoint.equals(LoadedFrameRayQuery.resolveCapturedTarget(level, target))
                    || !handOrigin.equals(currentHandOrigin(body, profile.physicalHeight()))
                    || !releaseFramePoint.equals(LoadedFrameRayQuery.currentActorPoint(level, body))) return Result.DENIED;
        } catch (RuntimeException failure) { return Result.DENIED; }
        if (!same(stack, entity.getItem())) return Result.DENIED;
        // Revalidate every authority and hand invariant after entity construction/probing, just before clear.
        if (!authorityValid.getAsBoolean() || !live(body, level) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || before.revision() != revision
                || !before.token(hand).filter(expectedToken::equals).isPresent()
                || !before.stackCopy(hand).filter(s -> same(stack, s)).isPresent()
                 || !eye.equals(body.getEyePosition()) || !bodyPosition.equals(body.position())
                 || !capturedWorldPoint.equals(LoadedFrameRayQuery.resolveCapturedTarget(level, target))
                || !handOrigin.equals(currentHandOrigin(body, entity.profile().physicalHeight()))
                || !releaseFramePoint.equals(LoadedFrameRayQuery.currentActorPoint(level, body))) return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(body, transition.state()); }
        catch (RuntimeException failure) { return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                ? Result.DENIED : Result.RECOVERY_REQUIRED; }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != transition.state()
                || !transition.state().compatible(body)) return Result.RECOVERY_REQUIRED;
        try {
            if (!level.addFreshEntity(entity)) return compensate(body, level, entity, before, transition.state(), hand, token, stack);
        } catch (RuntimeException failure) {
            return compensate(body, level, entity, before, transition.state(), hand, token, stack);
        }
        if (!live(body, level) || level.getEntity(entity.getUUID()) != entity || entity.isRemoved()
                || !entity.isAddedToLevel() || !same(stack, entity.getItem())
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != transition.state()) return Result.RECOVERY_REQUIRED;
        synchronized (COOLDOWN) {
            if (entity.profile().throwCooldown() > 0)
                COOLDOWN.put(body, now + entity.profile().throwCooldown());
        }
        committed.accept(entity.getUUID());
        return Result.SUCCESS;
    }

    private static void receipt(LivingEntity actor, String stage, String hand, ItemStack stack, Vec3 eye, Vec3 look,
                                LoadedFrameRayQuery.ActorPoint actorPoint) {
        if (!MoonStation14.LOGGER.isDebugEnabled() || !com.juicyslew.moonstation14.ms14.player_body_control.CharacterCadenceDiagnostics.allowReceipt(System.nanoTime())) return;
        var level = actor == null ? null : actor.level();
        MoonStation14.LOGGER.debug("[body-loose-throw] stage={} actor={} world={} levelGameTime={} serverTick={} eye={} look={} yaw={} pitch={} bodyPosition={} actorPoint={} item={} itemUuid={} hand={}",
                stage, actor == null ? "unknown" : actor.getUUID(), actor == null ? "unknown" : actor.level().dimension(),
                level == null ? "unknown" : level.getGameTime(), level instanceof ServerLevel server ? server.getServer().getTickCount() : "unknown",
                eye, look, actor == null ? "unknown" : actor.getYRot(), actor == null ? "unknown" : actor.getXRot(),
                actor == null ? "unknown" : actor.position(), actorPoint == null ? "UNKNOWN" : actorPoint,
                stack == null ? "unknown" : stack.getItem(), "UNKNOWN", hand);
    }

    private static Result compensate(LivingEntity body, ServerLevel level, LooseItemEntity entity, LiveHands before,
                                     LiveHands cleared, String hand, ItemToken token, ItemStack stack) {
        if (level.getEntity(entity.getUUID()) != null || entity.isAddedToLevel() || entity.isRemoved()
                || !live(body, level) || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != cleared
                || !cleared.compatible(body)) return Result.RECOVERY_REQUIRED;
        LiveHands.WholeResult restore = cleared.putWhole(cleared.revision(), hand, token, stack);
        if (!restore.succeeded() || restore.state().revision() <= before.revision()) return Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, restore.state()); }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        return live(body, level) && level.getEntity(entity.getUUID()) == null && !entity.isAddedToLevel()
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == restore.state()
                && restore.state().compatible(body) && restore.state().token(hand).filter(token.value()::equals).isPresent()
                && restore.state().stackCopy(hand).filter(s -> same(stack, s)).isPresent()
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }
    private static boolean live(LivingEntity b, ServerLevel l) { return b.level() == l && !b.isRemoved() && b.isAlive()
            && b.isAddedToLevel() && l.getEntity(b.getUUID()) == b; }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Vec3 currentHandOrigin(LivingEntity body, double physicalHeight) {
        return new Vec3(body.getX(), body.getY() + Mth.clamp(1.125, 0.0,
                Math.max(0.0, body.getBbHeight() - physicalHeight - .075)), body.getZ());
    }
    private static boolean same(ItemStack a, ItemStack b) { return b != null && a.getCount() == b.getCount()
            && ItemStack.isSameItemSameComponents(a, b); }
}
