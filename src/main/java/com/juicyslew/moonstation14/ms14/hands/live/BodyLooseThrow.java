package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight;
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

/** Transfers a complete live hand stack to an independently simulated loose world object. */
public final class BodyLooseThrow {
    private BodyLooseThrow() { }
    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }
    private static final Map<LivingEntity, Long> COOLDOWN = new WeakHashMap<>();
    private static volatile String LAST_PORTABLE_THROW_GATE = "not-run";

    /** Read-only last portable-throw gate, intended for focused failure diagnostics. */
    public static String lastPortableThrowGateForDiagnostics() { return LAST_PORTABLE_THROW_GATE; }

    public static Result throwHeld(ServerPlayer actor, String hand, String expectedToken, long expectedRevision,
                                   java.util.function.Consumer<UUID> committedWorldEntity) {
        var auth = HandActorAuthority.resolve(actor);
        if (auth.isEmpty() || expectedToken == null) return Result.DENIED;
        var resolved = auth.orElseThrow();
        return throwOwned(resolved.body(), hand, expectedToken, expectedRevision,
                () -> HandActorAuthority.revalidate(actor, resolved), committedWorldEntity);
    }

    /** Shared-kernel forced disarm; supplied direction is bounded and horizontal, with no voluntary cooldown. */
    public static Result throwForDisarm(LivingEntity target, Vec3 horizontalOffset) {
        if (target == null || horizontalOffset == null || !finite(horizontalOffset)
                || horizontalOffset.y != 0 || horizontalOffset.horizontalDistanceSqr() < 1.0e-8)
            return Result.DENIED;
        double length = horizontalOffset.horizontalDistance();
        if (length < 1.0 || length > 1.5) return Result.DENIED;
        LiveHands hands = target.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null) return Result.DENIED;
        String hand = hands.activeHand();
        String token = hands.token(hand).orElse(null);
        if (token == null) return Result.DENIED;
        return LiveHandsCommitGateway.batch(() -> throwOwnedCommitted(target, hand, token, hands.revision(),
                () -> true, ignored -> { }, horizontalOffset, true));
    }

    static Result throwOwned(LivingEntity body, String hand, String expectedToken, long revision,
                             BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committed) {
        return LiveHandsCommitGateway.batch(() -> throwOwnedCommitted(body, hand, expectedToken, revision,
                authorityValid, committed, null, false));
    }

    private static Result throwOwnedCommitted(LivingEntity body, String hand, String expectedToken, long revision,
                              BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committed,
                              Vec3 forcedOffset, boolean forced) {
        LAST_PORTABLE_THROW_GATE = "entry";
        if (body == null || hand == null || expectedToken == null || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !live(body, level) || !level.hasChunkAt(body.blockPosition()))
            return denyPortableThrow("actor-authority-or-loaded-body");
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision)
            return denyPortableThrow("hand-revision-or-compatibility");
        ItemToken token;
        try { token = new ItemToken(expectedToken); } catch (IllegalArgumentException e) { return denyPortableThrow("hand-token-format"); }
        if (!before.token(hand).filter(expectedToken::equals).isPresent()) return denyPortableThrow("hand-token-mismatch");
        long now = level.getGameTime();
        boolean cooldownReady = cooldownReady(body, now);
        if (!intentPermits(forced, CharacterControlSystem.canAct(body), cooldownReady))
            return denyPortableThrow(cooldownReady ? "action-not-permitted" : "throw-cooldown");
        ItemStack held = before.stackCopy(hand).orElse(ItemStack.EMPTY);
        if (PortableBodyKeys.isKey(held))
            if (!forced) return throwPortableBody(body, level, hand, expectedToken, revision, authorityValid, committed,
                    before, token, held, now);
        LiveHands.WholeResult transition = before.takeWhole(revision, hand, token);
        if (!transition.succeeded()) return Result.DENIED;
        ItemStack stack = transition.takenStack().orElseThrow();
        // The key references a live body; throwing it would create a second, non-owning loose copy.
        if (PortableBodyCarrying.containsPortableReferences(stack)) return Result.DENIED;
        if (stack.isEmpty() || !authorityValid.getAsBoolean()
                || !live(body, level) || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before)
            return Result.DENIED;
        Vec3 eye = body.getEyePosition(), look = forced ? forcedOffset.normalize() : body.getLookAngle(), bodyPosition = body.position();
        if (!finite(look) || !finite(eye) || !finite(bodyPosition) || (!forced && (look.lengthSqr() < .99 || look.lengthSqr() > 1.01))) return Result.DENIED;
        LooseItemEntity entity;
        Vec3 handOrigin;
        try {
            // Aim remains eye-authoritative, but place the physical object at a conservative hand height.
            entity = LooseItemEntity.create(level, bodyPosition, stack);
            LooseObjectProfile profile = entity.profile();
            double range = aimRange(forced, Math.min(8.0, profile.throwRange()), forcedOffset);
            double minimum = Math.min(range, profile.minimumThrowRange());
            Vec3 end = eye.add(look.scale(range));
            if (!finite(end)) return Result.DENIED;
            Vec3 aim = forced ? BodyItemTargetProbe.loadedAimPoint(level, body, range, look)
                    : BodyItemTargetProbe.loadedAimPoint(level, body, range);
            if (aim == null) return Result.DENIED;
            double distance = eye.distanceTo(aim);
            if (!finite(aim) || distance + 1.0e-4 < minimum) return Result.DENIED;
            double handY = body.getY() + Mth.clamp(1.125, 0.0,
                    Math.max(0.0, body.getBbHeight() - profile.physicalHeight() - .075));
            handOrigin = new Vec3(body.getX(), handY, body.getZ());
            Vec3 toAim = aim.subtract(handOrigin);
            if (distance <= .1 || !finite(toAim) || toAim.lengthSqr() < 1.0e-8) return Result.DENIED;
            Vec3 launch = handOrigin.add(toAim.normalize().scale(.1));
            if (!BodyItemTargetProbe.loadedItemBoxClear(level, launch, profile.physicalWidth(), profile.physicalHeight()))
                return Result.DENIED;
            entity.setPos(launch.x, launch.y, launch.z);
            Vec3 direction = aim.subtract(handOrigin);
            if (!finite(direction) || direction.lengthSqr() < 1.0e-8) return Result.DENIED;
            entity.launch(direction.normalize().scale(profile.throwSpeed()), profile.throwSpin());
            if (!eye.equals(body.getEyePosition()) || (!forced && !look.equals(body.getLookAngle())) || !bodyPosition.equals(body.position())
                    || !handOrigin.equals(currentHandOrigin(body, profile.physicalHeight()))) return Result.DENIED;
        } catch (RuntimeException failure) { return Result.DENIED; }
        if (!same(stack, entity.getItem())) return Result.DENIED;
        // Revalidate every authority and hand invariant after entity construction/probing, just before clear.
        if (!authorityValid.getAsBoolean() || !live(body, level)
                || !intentPermits(forced, CharacterControlSystem.canAct(body), cooldownReady(body, now))
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || before.revision() != revision
                || !before.token(hand).filter(expectedToken::equals).isPresent()
                || !before.stackCopy(hand).filter(s -> same(stack, s)).isPresent()
                || !eye.equals(body.getEyePosition()) || (!forced && !look.equals(body.getLookAngle())) || !bodyPosition.equals(body.position())
                || !handOrigin.equals(currentHandOrigin(body, entity.profile().physicalHeight()))) return Result.DENIED;
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
        // Only an authenticated, voluntary throw arms the one-shot landing damage state.
        // Forced disarms share publication mechanics but are not voluntary item throws.
        if (!forced) entity.markExplicitThrowForLanding();
        synchronized (COOLDOWN) {
            if (usesVoluntaryCooldown(forced) && entity.profile().throwCooldown() > 0)
                COOLDOWN.put(body, now + entity.profile().throwCooldown());
        }
        committed.accept(entity.getUUID());
        return Result.SUCCESS;
    }

    private static Result throwPortableBody(LivingEntity carrier, ServerLevel level, String hand, String expectedToken,
                                            long revision, BooleanSupplier authorityValid,
                                            java.util.function.Consumer<UUID> committed, LiveHands before,
                                             ItemToken token, ItemStack key, long now) {
        LAST_PORTABLE_THROW_GATE = "start";
        var reference = PortableBodyKeys.reference(key);
        if (reference == null || !reference.dimension().equals(level.dimension().location())
                || !(level.getEntity(reference.body()) instanceof net.minecraft.world.entity.Mob target)
                || !PortableBodyKeys.isKey(key)) return denyPortableThrow("reference-or-loaded-target");
        var capability = HandCapability.resolveCharacter(target)
                .flatMap(character -> character.component(com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent.class))
                .orElse(null);
        if (capability == null || !ExternalBodyFlight.canLaunchAfterPortableRelease(target))
            return denyPortableThrow("resolved-capability-or-flight-preflight");
        LooseObjectProfile profile;
        try { profile = LooseObjectProfile.resolve(capability.profileId()); }
        catch (RuntimeException unavailableProfile) { return denyPortableThrow("profile-resolution"); }
        if (!Float.isFinite(profile.throwSpeed()) || profile.throwSpeed() <= 0) return denyPortableThrow("throw-speed");
        Vec3 eye = carrier.getEyePosition(), look = carrier.getLookAngle(), carrierPosition = carrier.position();
        if (!finite(eye) || !finite(look) || !finite(carrierPosition) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01)
            return denyPortableThrow("carrier-look-or-position");
        double range = Math.min(8.0, profile.throwRange());
        Vec3 end = eye.add(look.scale(range));
        if (!finite(end)) return denyPortableThrow("aim-end");
        Vec3 aim = BodyItemTargetProbe.loadedAimPoint(level, carrier, range);
        if (aim == null || !finite(aim) || eye.distanceTo(aim) + 1.0e-4 < Math.min(range, profile.minimumThrowRange()))
            return denyPortableThrow("loaded-aim-or-minimum-range");
        double handY = carrier.getY() + Mth.clamp(1.125, 0.0,
                Math.max(0.0, carrier.getBbHeight() - profile.physicalHeight() - .075));
        Vec3 origin = new Vec3(carrier.getX(), handY, carrier.getZ());
        Vec3 direction = aim.subtract(origin);
        if (!finite(direction) || direction.lengthSqr() < 1.0e-8) return denyPortableThrow("aim-direction");
        Vec3 launch = origin.add(direction.normalize().scale(.1));
        Vec3 velocity = direction.normalize().scale(profile.throwSpeed());
        if (!finite(launch) || !finite(velocity)
                || !BodyItemTargetProbe.loadedItemBoxClear(level, launch, profile.physicalWidth(), profile.physicalHeight()))
            return denyPortableThrow("launch-placement-collision");
        var snapshot = PortableBodyReleaseTransaction.capture(target);
        if (snapshot == null || !reference.equals(snapshot.reference())) return denyPortableThrow("release-snapshot");
        BooleanSupplier placementClear = () -> com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                .isValidBody(target, level) && level.hasChunkAt(target.blockPosition())
                && BodyItemTargetProbe.loadedItemBoxClear(level, launch, profile.physicalWidth(), profile.physicalHeight());
        if (!placementClear.getAsBoolean()) return denyPortableThrow("release-placement-collision");
        var placement = new PortableBodyReleaseTransaction.Placement(launch, velocity, placementClear);
        if (!authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(carrier)
                || carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(carrier) || before.revision() != revision
                || !before.token(hand).filter(expectedToken::equals).isPresent()
                || !before.stackCopy(hand).filter(stack -> same(key, stack)).isPresent()
                || !eye.equals(carrier.getEyePosition()) || !look.equals(carrier.getLookAngle())
                || !carrierPosition.equals(carrier.position()) || !PortableBodyReleaseTransaction.isCurrent(snapshot)
                || !ExternalBodyFlight.canLaunchAfterPortableRelease(target))
            return denyPortableThrow("authority-hand-placement-or-flight-revalidation");
        var released = PortableBodyReleaseTransaction.release(snapshot, placement);
        if (released == PortableBodyReleaseTransaction.Result.RECOVERY_REQUIRED) {
            LAST_PORTABLE_THROW_GATE = "release-recovery-required";
            return Result.RECOVERY_REQUIRED;
        }
        if (released != PortableBodyReleaseTransaction.Result.SUCCESS)
            return denyPortableThrow("release-transaction-" + released + ":"
                    + PortableBodyReleaseTransaction.lastDiagnostic());
        // canLaunch was checked immediately before the canonical release; launch is the same-body flight lease.
        ExternalBodyFlight.launchPortableBodyThrow(target, velocity, Math.max(1, profile.throwCooldown()));
        if (!ExternalBodyFlight.tracked(target) || !ExternalBodyFlight.active(target)
                || !com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.isValidBody(target, level)
                || target.position().distanceToSqr(launch) > 1.0e-10
                || !velocity.equals(target.getDeltaMovement())) {
            LAST_PORTABLE_THROW_GATE = "post-launch-recovery-required";
            return Result.RECOVERY_REQUIRED;
        }
        synchronized (COOLDOWN) {
            if (profile.throwCooldown() > 0) COOLDOWN.put(carrier, now + profile.throwCooldown());
        }
        committed.accept(target.getUUID());
        LAST_PORTABLE_THROW_GATE = "success";
        return Result.SUCCESS;
    }

    private static Result denyPortableThrow(String gate) {
        LAST_PORTABLE_THROW_GATE = gate;
        return Result.DENIED;
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
    static boolean intentPermits(boolean forced, boolean canAct, boolean cooldownReady) {
        return forced || canAct && cooldownReady;
    }
    static boolean usesVoluntaryCooldown(boolean forced) { return !forced; }
    static double aimRange(boolean forced, double profileRange, Vec3 offset) {
        return forced ? Math.min(profileRange, offset.horizontalDistance()) : profileRange;
    }
    private static boolean cooldownReady(LivingEntity body, long now) {
        synchronized (COOLDOWN) { return now >= COOLDOWN.getOrDefault(body, Long.MIN_VALUE); }
    }
    private static Vec3 currentHandOrigin(LivingEntity body, double physicalHeight) {
        return new Vec3(body.getX(), body.getY() + Mth.clamp(1.125, 0.0,
                Math.max(0.0, body.getBbHeight() - physicalHeight - .075)), body.getZ());
    }
    private static boolean same(ItemStack a, ItemStack b) { return b != null && a.getCount() == b.getCount()
            && ItemStack.isSameItemSameComponents(a, b); }
}
