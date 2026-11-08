package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.ms14.combat.MeleeSelectionCooldown;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import com.juicyslew.moonstation14.MoonStation14;

/** Server-side world/attachment ownership transfer. Never touches the carrier inventory. */
public final class BodyHandItemTransfer {
    private BodyHandItemTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    /** Shared world destination/entity path for trusted minted grants; caller retains input on failure. */
    public static LooseItemEntity spawnTrustedGrant(LivingEntity body, ItemStack stack) {
        return spawnTrustedGrant(body, stack, null, ignored -> { });
    }

    /** Trusted grant tied to the exact currently-authorized player carrier. */
    public static LooseItemEntity spawnAuthorizedGrant(ServerPlayer carrier, HandActorAuthority.Snapshot exactAuthority,
                                                        ItemStack stack, java.util.function.Consumer<String> failedStage) {
        if (carrier == null || exactAuthority == null || exactAuthority.actor() != carrier
                || !HandActorAuthority.revalidate(carrier, exactAuthority)) {
            if (failedStage != null) failedStage.accept("authority admission");
            return null;
        }
        return spawnTrustedGrant(exactAuthority.body(), stack, carrier, failedStage,
                () -> HandActorAuthority.revalidate(carrier, exactAuthority));
    }

    private static LooseItemEntity spawnTrustedGrant(LivingEntity body, ItemStack stack, Entity excludedCarrier,
                                                       java.util.function.Consumer<String> failedStage) {
        return spawnTrustedGrant(body, stack, excludedCarrier, failedStage, () -> true);
    }

    private static LooseItemEntity spawnTrustedGrant(LivingEntity body, ItemStack stack, Entity excludedCarrier,
                                                       java.util.function.Consumer<String> failedStage,
                                                       BooleanSupplier authorityValid) {
        if (body == null || stack == null || stack.isEmpty() || !(body.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !finite(body.position())
                || !level.hasChunkAt(body.blockPosition()) || !authorityValid.getAsBoolean()) {
            failStage(failedStage, "body/admission validation"); return null;
        }
        String[] stage = {"candidate resolution"};
        try {
            LooseItemEntity entity = createTrustedDropEntity(body, stack, excludedCarrier, value -> stage[0] = value);
            if (entity == null) { failStage(failedStage, stage[0]); return null; }
            stage[0] = "stack validation";
            if (!same(stack, entity.getItem()) || !authorityValid.getAsBoolean()) {
                failStage(failedStage, stage[0]); return null;
            }
            stage[0] = "add/spawn";
            if (!level.addFreshEntity(entity)) {
                if (entity.isAddedToLevel() && level.getEntity(entity.getUUID()) == entity) entity.discard();
                failStage(failedStage, stage[0]);
                return null;
            }
            if (level.getEntity(entity.getUUID()) == entity && entity.isAddedToLevel()
                    && !entity.isRemoved() && same(stack, entity.getItem()) && authorityValid.getAsBoolean()) return entity;
            if (entity.isAddedToLevel() && level.getEntity(entity.getUUID()) == entity) entity.discard();
            failStage(failedStage, "post-spawn publication validation");
            return null;
        } catch (RuntimeException failure) { failStage(failedStage, stage[0] + " exception " + failure.getClass().getSimpleName()); return null; }
    }

    private static void failStage(java.util.function.Consumer<String> callback, String stage) {
        if (callback != null) callback.accept(stage);
    }

    /** Shared package-local detached drop stage for other exact-source ownership transfers. */
    static LooseItemEntity createStagedDrop(Entity body, ItemStack stack) {
        return createStagedDrop(body, stack, null);
    }

    private static LooseItemEntity createStagedDrop(Entity body, ItemStack stack, Entity excludedCarrier) {
        return createTrustedDropEntity(body, stack, excludedCarrier, ignored -> { }, null);
    }

    private static LooseItemEntity createStagedDrop(Entity body, ItemStack stack, Entity excludedCarrier,
            @javax.annotation.Nullable com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        return createTrustedDropEntity(body, stack, excludedCarrier, ignored -> { }, target);
    }

    /** Detached harvest output at a checked horizontal offset; caller publishes via FreshWorldOutputBatch. */
    static LooseItemEntity createStagedHarvestDrop(LivingEntity body, ItemStack stack, double offsetX, double offsetZ) {
        if (body == null || stack == null || stack.isEmpty() || PortableBodyCarrying.containsPortableReferences(stack)
                || !Double.isFinite(offsetX) || !Double.isFinite(offsetZ)
                || Math.abs(offsetX) > .25 || Math.abs(offsetZ) > .25
                || !(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body) return null;
        try {
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(stack, false);
            Vec3 at = body.position().add(offsetX, Math.max(.01, profile.physicalHeight() * .5), offsetZ);
            if (!finite(at) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(at))) return null;
            LooseItemEntity entity = LooseItemEntity.createForActor(body, at, stack.copy());
            if (!entity.prepareInitialFrameState(level) || !BodyItemTargetProbe.loadedItemBoxClear(level, entity)) return null;
            entity.setDeltaMovement(Vec3.ZERO);
            return same(stack, entity.getItem()) ? entity : null;
        } catch (RuntimeException unavailable) { return null; }
    }

    /** Detached non-body harvest output; publication remains owned by FreshWorldOutputBatch. */
    public static LooseItemEntity createStagedHarvestDrop(ServerLevel level, Vec3 origin, ItemStack stack,
                                                           double offsetX, double offsetZ) {
        if (level == null || origin == null || stack == null || stack.isEmpty()
                || PortableBodyCarrying.containsPortableReferences(stack)
                || !finite(origin) || !Double.isFinite(offsetX) || !Double.isFinite(offsetZ)
                || Math.abs(offsetX) > .25 || Math.abs(offsetZ) > .25 || !level.getServer().isSameThread()) return null;
        try {
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(stack, false);
            Vec3 at = origin.add(offsetX, 0, offsetZ);
            if (!finite(at) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(at))) return null;
            LooseItemEntity entity = LooseItemEntity.create(level, at, stack.copy());
            if (!entity.prepareInitialFrameState(level) || !BodyItemTargetProbe.loadedItemBoxClear(level, entity)) return null;
            entity.setDeltaMovement(Vec3.ZERO);
            return same(stack, entity.getItem()) ? entity : null;
        } catch (RuntimeException unavailable) { return null; }
    }

    /** Preserve preferred scatter, falling back only within the bounded 3x3 harvest footprint. */
    static LooseItemEntity createStagedHarvestDropWithFallback(LivingEntity body, ItemStack stack,
                                                                double preferredX, double preferredZ) {
        LooseItemEntity preferred = createStagedHarvestDrop(body, stack, preferredX, preferredZ);
        if (preferred != null) return preferred;
        double[] offsets = {0, -.25, .25};
        for (double x : offsets) for (double z : offsets) {
            if (x == preferredX && z == preferredZ) continue;
            LooseItemEntity candidate = createStagedHarvestDrop(body, stack, x, z);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static LooseItemEntity createTrustedDropEntity(Entity body, ItemStack stack, Entity excludedCarrier,
                                                           java.util.function.Consumer<String> stage) {
        return createTrustedDropEntity(body, stack, excludedCarrier, stage, null);
    }
    private static LooseItemEntity createTrustedDropEntity(Entity body, ItemStack stack, Entity excludedCarrier,
                                                           java.util.function.Consumer<String> stage,
                                                           @javax.annotation.Nullable com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        if (body == null || stack == null || stack.isEmpty()
                || body instanceof LivingEntity && PortableBodyCarrying.containsPortableReferences(stack)
                || !(body.level() instanceof ServerLevel level)) return null;
        try {
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(stack, false);
            var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
            LivingEntity livingBody = body instanceof LivingEntity living ? living : null;
            var owner = livingBody == null ? null : containment.ownerOf(livingBody).orElse(null);
            BodyItemTargetProbe.DropPlacement placement;
            if (owner != null) {
                if (target != null) return null;
                Vec3 root = owner.positionFor(livingBody);
                if (!owner.isValid() || root == null || !finite(root)
                        || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(root))) return null;
                placement = new BodyItemTargetProbe.DropPlacement(root, Vec3.ZERO);
            } else {
                placement = target == null
                        ? BodyItemTargetProbe.resolveDrop(level, body, excludedCarrier, profile.physicalWidth(), profile.physicalHeight())
                        : BodyItemTargetProbe.resolveDrop(level, body, excludedCarrier, profile.physicalWidth(), profile.physicalHeight(), target);
            }
            if (placement == null) { stage.accept("candidate resolution"); return null; }
            stage.accept("anchor hydration/initial composition");
            LooseItemEntity entity = livingBody == null
                    ? LooseItemEntity.create(level, placement.position(), stack.copy())
                    : LooseItemEntity.createForActor(livingBody, placement.position(), stack.copy());
            if (placement.surfacePlacement() && placement.supportFrameId() != null) {
                var anchor = new com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchor(
                        placement.supportFrameId(), placement.supportStoragePoint());
                if (!com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchor.hydrate(level, entity, anchor)) return null;
            }
            if (owner == null && !entity.prepareInitialFrameState(level)) return null;
            // Profile dimensions choose candidates; verify the hydrated entity's actual physical
            // bounds at the final ownership boundary before it can be launched or committed.
            stage.accept("final actual bounds");
            if (!BodyItemTargetProbe.loadedItemBoxClear(level, entity)) return null;
            stage.accept("carrier/velocity point");
            var bodyPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
            if (bodyPoint == null) return null;
            var velocityPoint = LoadedFrameRayQuery.pointAtWorldPosition(level,
                    placement.surfacePlacement() ? placement.supportFrameId() : bodyPoint.frameId(), placement.position());
            if (velocityPoint == null) return null;
            Vec3 carrierVelocity = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity
                    .pointVelocityAt(level, velocityPoint);
            Vec3 launchVelocity = HandReleaseKinematics.composePlacement(placement,
                    body.getDeltaMovement(), carrierVelocity);
            if (launchVelocity == null) return null;
            entity.launch(launchVelocity, 0);
            return same(stack, entity.getItem()) ? entity : null;
        } catch (RuntimeException failure) { stage.accept("creation exception " + failure.getClass().getSimpleName()); return null; }
    }

    private static Result pickupPortableLiving(LivingEntity carrier, LivingEntity target, UUID nominated,
            String hand, LiveHands before, long expectedRevision, BooleanSupplier authorityValid,
            java.util.function.Consumer<UUID> committedSource, boolean selectedPolicy, ServerLevel level,
            Vec3 sourceEye, Vec3 sourceLook) {
        if (!PortableBodyCarrying.eligibleLivingTarget(carrier, target) || !reachable(carrier, target.position()))
            return Result.DENIED;
        Vec3 targetPosition = target.position();
        UUID tokenId = UUID.randomUUID();
        ItemToken token = new ItemToken(tokenId.toString());
        ItemStack key = PortableBodyCarrying.createKey(carrier, target, tokenId);
        if (!PortableBodyKeys.isKey(key)) return Result.DENIED;
        LiveHands.WholeResult transition = before.putWhole(expectedRevision, hand, token, key);
        if (!transition.succeeded() || !BodyHandStateService.representable(transition.state(), carrier)) return Result.DENIED;
        BodyItemTargetProbe.Target confirmed = selectedPolicy
                ? BodyItemTargetProbe.probeSelected(level, carrier, nominated, BodyItemTargetProbe.MAX_REACH)
                : BodyItemTargetProbe.nearestVisible(level, carrier, BodyItemTargetProbe.MAX_REACH);
        if (confirmed == null || !nominated.equals(confirmed.itemId())
                || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(carrier)
                || carrier.level() != level || carrier.isRemoved() || !carrier.isAddedToLevel()
                || level.getEntity(carrier.getUUID()) != carrier || carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || target.level() != level || target.isRemoved() || !target.isAddedToLevel()
                || level.getEntity(target.getUUID()) != target
                || !target.position().equals(targetPosition)
                || !reachable(carrier, target.position()) || !sourceEye.equals(carrier.getEyePosition())
                || !sourceLook.equals(carrier.getLookAngle()) || !PortableBodyCarrying.eligibleLivingTarget(carrier, target))
            return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(carrier, before, transition.state()); }
        catch (RuntimeException failure) {
            return carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        if (carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != transition.state()
                || !transition.state().compatible(carrier)) return Result.RECOVERY_REQUIRED;
        var owner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                .ownerOf(target).orElse(null);
        if (!(owner instanceof PortableBodyCarrierOwner portable) || portable.carrier() != carrier
                || !portable.reference().body().equals(target.getUUID()) || !portable.handToken().equals(token.value()))
            return Result.RECOVERY_REQUIRED;
        committedSource.accept(target.getUUID());
        return Result.SUCCESS;
    }

    public static Result pickup(ServerPlayer actor, UUID itemEntityUuid, String hand, long expectedHandRevision) {
        return pickup(actor, itemEntityUuid, hand, expectedHandRevision, ignored -> { });
    }

    public static Result pickup(ServerPlayer actor, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                 java.util.function.Consumer<UUID> committedSource) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || itemEntityUuid == null) { transferReceipt(actor, "AUTHORITY", itemEntityUuid, hand); return Result.DENIED; }
        LivingEntity body = authority.orElseThrow().body();
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()), committedSource, true);
    }

    /** Pickup the specifically nominated item; UUID selects the target, but grants no reach/ownership authority. */
    public static Result pickupSelected(ServerPlayer actor, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                        java.util.function.Consumer<UUID> committedSource) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || itemEntityUuid == null) return Result.DENIED;
        LivingEntity body = authority.orElseThrow().body();
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()), committedSource);
    }

    /** Trusted server-created output pickup. Exact entity and stack are proofs, not reach/authority grants. */
    public static Result pickupTrustedCreated(HandActorAuthority.Snapshot authority, LooseItemEntity exactEntity,
                                               ItemStack expectedStack, BooleanSupplier callerValid) {
        if (authority == null || exactEntity == null || expectedStack == null || expectedStack.isEmpty()
                || callerValid == null || !(authority.body().level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return Result.DENIED;
        LivingEntity body = authority.body();
        for (String hand : authority.handIds()) {
            LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (current == null || !current.compatible(body) || !current.handIds().equals(authority.handIds()))
                return Result.DENIED;
            if (current.stackCopy(hand).isPresent()) continue;
            Result result = pickupOwnedTrusted(body, exactEntity, expectedStack, hand, current.revision(),
                    () -> HandActorAuthority.revalidate(authority.actor(), authority) && callerValid.getAsBoolean());
            if (result == Result.SUCCESS || result == Result.RECOVERY_REQUIRED) return result;
        }
        return Result.DENIED;
    }

    private static Result pickupOwnedTrusted(LivingEntity body, LooseItemEntity entity, ItemStack stack,
                                               String hand, long revision, BooleanSupplier valid) {
        return LiveHandsCommitGateway.batch(() -> pickupTransaction(body, entity.getUUID(), hand, revision,
                valid, ignored -> { }, entity, stack));
    }

    // Trusted fixture seam only: tests use a real spawned body and world item, never a fake actor authority.
    static Result pickupOwned(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                              BooleanSupplier authorityValid) {
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision, authorityValid, ignored -> { });
    }

    static Result pickupSelectedOwned(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                      BooleanSupplier authorityValid) {
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision, authorityValid, ignored -> { }, true);
    }

    private static Result pickupOwned(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                          BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedSource) {
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision, authorityValid, committedSource, false);
    }

    private static Result pickupOwned(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                          BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedSource,
                                          boolean selectedPolicy) {
        return LiveHandsCommitGateway.batch(() -> pickupTransaction(body, itemEntityUuid, hand, expectedHandRevision,
                authorityValid, committedSource, null, null, selectedPolicy));
    }

    private static Result pickupTransaction(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                          BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedSource) {
        return pickupTransaction(body, itemEntityUuid, hand, expectedHandRevision, authorityValid, committedSource, null, null, false);
    }

    private static Result pickupTransaction(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                           BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedSource,
                                           LooseItemEntity trustedEntity, ItemStack expectedStack) {
        return pickupTransaction(body, itemEntityUuid, hand, expectedHandRevision, authorityValid, committedSource,
                trustedEntity, expectedStack, false);
    }

    private static Result pickupTransaction(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                           BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedSource,
                                           LooseItemEntity trustedEntity, ItemStack expectedStack, boolean selectedPolicy) {
        if (body == null || itemEntityUuid == null || authorityValid == null) return Result.DENIED;
        if (!(body.level() instanceof ServerLevel level) || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        if (!level.getServer().isSameThread() || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != expectedHandRevision)
            return Result.DENIED;
        // The nominated UUID selects the item, but current reach, line of sight and ownership remain server-validated.
        Vec3 sourceEye = body.getEyePosition();
        boolean trusted = trustedEntity != null;
        BodyItemTargetProbe.Target selected = trusted ? null : selectedPolicy
                ? BodyItemTargetProbe.probeSelected(level, body, itemEntityUuid, BodyItemTargetProbe.MAX_REACH)
                : BodyItemTargetProbe.probeNominatedPickup(level, body, itemEntityUuid, BodyItemTargetProbe.MAX_REACH);
        if (!trusted && selected == null) { transferReceipt(body, "NO_CANDIDATE_UNCLASSIFIED", itemEntityUuid, hand); return Result.DENIED; }
        UUID sourceId = trusted ? trustedEntity.getUUID() : selected.itemId();
        Entity entity = trusted ? trustedEntity : level.getEntity(sourceId);
        if (!trusted && selectedPolicy && entity instanceof LivingEntity living) {
            return pickupPortableLiving(body, living, sourceId, hand, before, expectedHandRevision, authorityValid,
                    committedSource, true, level, sourceEye, body.getLookAngle());
        }
        if (!(entity instanceof ItemEntity item) || item.isRemoved() || !item.isAddedToLevel()
                || item.level() != level || item.getTarget() != null || item.hasPickUpDelay()
                || !BodyItemTargetProbe.reachableStorageHost(level, body, item, BodyItemTargetProbe.MAX_REACH)) return Result.DENIED;
        if (trusted && (level.getEntity(sourceId) != trustedEntity || !trustedEntity.admitsBodyHandPickup())) return Result.DENIED;
        ItemStack source = item.getItem().copy();
        if (!valid(source) || trusted && !same(source, expectedStack)) return Result.DENIED;
        Vec3 sourcePosition = item.position();
        ItemToken token = new ItemToken(UUID.randomUUID().toString());
        LiveHands.WholeResult transition = before.putWhole(expectedHandRevision, hand, token, source);
        if (transition.succeeded()) transition = new LiveHands.WholeResult(
                MeleeSelectionCooldown.stageSelectedInsertion(body, before, transition.state()),
                transition.takenStack(), transition.rejection());
        BodyItemTargetProbe.Target confirmed = !trusted && transition.succeeded()
                ? probePickup(level, body, sourceId, selectedPolicy) : null;
        if (!transition.succeeded() || !BodyHandStateService.representable(transition.state(), body)
                || !authorityValid.getAsBoolean()
                || !CharacterControlSystem.canAct(body) || body.level() != level
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || !sourcePosition.equals(item.position())
                 || level.getEntity(sourceId) != item || item.level() != level
                || item.isRemoved() || !item.isAddedToLevel()
                || item.getTarget() != null || item.hasPickUpDelay() || !same(source, item.getItem())
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                  || !sourceEye.equals(body.getEyePosition())
                 || (!trusted && (confirmed == null || !sourceId.equals(confirmed.itemId())))
                // Shape callbacks are not assumed pure: the checked source and attachment must still
                // be the exact live owners immediately after the probe, before clearing the source.
                || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || item.level() != level || item.isRemoved()
                 || !item.isAddedToLevel() || level.getEntity(sourceId) != item
                  || !sourcePosition.equals(item.position())
                 || item.getTarget() != null || item.hasPickUpDelay() || !same(source, item.getItem())
                 || !BodyHandStateService.representable(transition.state(), body)
                  || !sourceEye.equals(body.getEyePosition())
                   || (!trusted && probePickup(level, body, sourceId, selectedPolicy) == null)
                  || !sourceEye.equals(body.getEyePosition())
                 || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                 || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                 || level.getEntity(body.getUUID()) != body || item.level() != level
                 || item.isRemoved() || !item.isAddedToLevel() || level.getEntity(sourceId) != item
                  || !sourcePosition.equals(item.position())
                 || item.getTarget() != null || item.hasPickUpDelay()
                 || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                  || !same(source, item.getItem()) || (trusted && (!same(expectedStack, item.getItem())
                  || level.getEntity(sourceId) != trustedEntity || !BodyItemTargetProbe.reachableStorageHost(level, body, item,
                  BodyItemTargetProbe.MAX_REACH))))
             { transferReceipt(body, "REVALIDATION_CHANGED", sourceId, hand); return Result.DENIED; }

        // The entity must stop owning the stack before the attachment starts owning it.
        try {
            item.setItem(ItemStack.EMPTY);
        } catch (RuntimeException failure) {
            return item.level() == level && !item.isRemoved() && item.isAddedToLevel()
                     && level.getEntity(sourceId) == item && sourcePosition.equals(item.position())
                    && body.level() == level && !body.isRemoved() && body.isAddedToLevel()
                    && level.getEntity(body.getUUID()) == body
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    && same(source, item.getItem()) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        if (!item.getItem().isEmpty() || item.level() != level || !sourcePosition.equals(item.position())
                || item.getTarget() != null || item.hasPickUpDelay()
                 || level.getEntity(sourceId) != item || item.isRemoved() || !item.isAddedToLevel()
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before)
            return Result.RECOVERY_REQUIRED;
        try {
            LiveHandsCommitGateway.commitOwned(body, transition.state());
        } catch (RuntimeException failure) {
            return rollbackPickup(body, before, item, level, source, sourcePosition);
        }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != transition.state()
                || !transition.state().compatible(body)) return Result.RECOVERY_REQUIRED;
        try {
            item.discard();
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
        if (item.isRemoved() && level.getEntity(sourceId) != item) { committedSource.accept(sourceId); return Result.SUCCESS; }
        return Result.RECOVERY_REQUIRED;
    }

    public static Result drop(ServerPlayer actor, String hand, String expectedToken, long expectedHandRevision) {
        return drop(actor, hand, expectedToken, expectedHandRevision, ignored -> { });
    }

    public static Result drop(ServerPlayer actor, String hand, String expectedToken, long expectedHandRevision,
                              java.util.function.Consumer<UUID> committedWorldEntity) {
        return drop(actor, hand, expectedToken, expectedHandRevision, null, committedWorldEntity);
    }

    public static Result drop(ServerPlayer actor, String hand, String expectedToken, long expectedHandRevision,
                              @javax.annotation.Nullable com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target,
                              java.util.function.Consumer<UUID> committedWorldEntity) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || expectedToken == null) return Result.DENIED;
        LivingEntity body = authority.orElseThrow().body();
        return dropOwned(body, actor, hand, expectedToken, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()), committedWorldEntity, target);
    }

    /**
     * Consume one unit from a body hand after exact authority admission. This only commits the
     * body attachment; it is not atomic with any world/pipe admission performed by a caller.
     */
    public static Result consumeOneForAuthorizedBody(ServerPlayer carrier, HandActorAuthority.Snapshot exactAuthority,
                                                      String handId, String token, long revision,
                                                      ItemStack expectedItem) {
        if (carrier == null || exactAuthority == null || exactAuthority.actor() != carrier
                || handId == null || token == null || expectedItem == null || expectedItem.isEmpty())
            return Result.DENIED;
        LivingEntity body = exactAuthority.body();
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !CharacterControlSystem.canAct(body) || !HandActorAuthority.revalidate(carrier, exactAuthority)
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body)
            return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || !before.handIds().equals(exactAuthority.handIds())) return Result.DENIED;
        LiveHands next = before.consumeOne(revision, handId, token, expectedItem).orElse(null);
        if (next == null || !BodyHandStateService.representable(next, body)
                || !HandActorAuthority.revalidate(carrier, exactAuthority)
                || !CharacterControlSystem.canAct(body) || body.level() != level || body.isRemoved()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body)) return Result.DENIED;
        try {
            LiveHandsCommitGateway.commitOwned(body, next);
        } catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return body.level() == level && !body.isRemoved() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next
                && next.compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    // Fixture seam: the public route alone resolves the actor. Tests exercise the same world/attachment transfer
    // on a real spawned body, without synthesizing a controller or changing the production authorization route.
    static Result dropOwned(LivingEntity body, String hand, String expectedToken, long expectedHandRevision,
                            BooleanSupplier authorityValid) {
        return dropOwned(body, null, hand, expectedToken, expectedHandRevision, authorityValid, ignored -> { }, false);
    }

    /** System-owned drop on a medical transition; bypasses voluntary-action incapacitation only. */
    public static Result forceDropOwned(LivingEntity body, String hand, String token, long revision) {
        return dropOwned(body, null, hand, token, revision, () -> true, ignored -> { }, true);
    }

    /** Fixture overload accepts only an explicitly designated excluded carrier; it is never inferred by type. */
    static Result dropOwned(LivingEntity body, Entity excludedCarrier, String hand, String expectedToken,
                            long expectedHandRevision, BooleanSupplier authorityValid) {
        return dropOwned(body, excludedCarrier, hand, expectedToken, expectedHandRevision, authorityValid, ignored -> { }, false);
    }

    private static Result dropOwned(LivingEntity body, Entity excludedCarrier, String hand, String expectedToken,
                              long expectedHandRevision, BooleanSupplier authorityValid,
                              java.util.function.Consumer<UUID> committedWorldEntity) {
        return dropOwned(body, excludedCarrier, hand, expectedToken, expectedHandRevision, authorityValid, committedWorldEntity, false, null);
    }

    private static Result dropOwned(LivingEntity body, Entity excludedCarrier, String hand, String expectedToken,
                              long expectedHandRevision, BooleanSupplier authorityValid,
                              java.util.function.Consumer<UUID> committedWorldEntity,
                              @javax.annotation.Nullable com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        return dropOwned(body, excludedCarrier, hand, expectedToken, expectedHandRevision, authorityValid, committedWorldEntity, false, target);
    }

    private static Result dropOwned(LivingEntity body, Entity excludedCarrier, String hand, String expectedToken,
                              long expectedHandRevision, BooleanSupplier authorityValid,
                              java.util.function.Consumer<UUID> committedWorldEntity, boolean forced) {
        return dropOwned(body, excludedCarrier, hand, expectedToken, expectedHandRevision, authorityValid, committedWorldEntity, forced, null);
    }
    private static Result dropOwned(LivingEntity body, Entity excludedCarrier, String hand, String expectedToken,
                              long expectedHandRevision, BooleanSupplier authorityValid,
                              java.util.function.Consumer<UUID> committedWorldEntity, boolean forced,
                              @javax.annotation.Nullable com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        return LiveHandsCommitGateway.batch(() -> dropTransaction(body, excludedCarrier, hand, expectedToken,
                expectedHandRevision, authorityValid, committedWorldEntity, forced, target));
    }

    private static Result dropTransaction(LivingEntity body, Entity excludedCarrier, String hand, String expectedToken,
                              long expectedHandRevision, BooleanSupplier authorityValid,
                              java.util.function.Consumer<UUID> committedWorldEntity, boolean forced,
                              @javax.annotation.Nullable com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        if (expectedToken == null || body == null || body.isRemoved() || !body.isAddedToLevel()) return Result.DENIED;
        if (!(body.level() instanceof ServerLevel level) || (!forced && !CharacterControlSystem.canAct(body))
                || level.getEntity(body.getUUID()) != body
                || !finite(body.position()) || !level.hasChunkAt(body.blockPosition())) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != expectedHandRevision)
            return Result.DENIED;
        ItemToken token;
        try { token = new ItemToken(expectedToken); }
        catch (IllegalArgumentException failure) { return Result.DENIED; }
        LiveHands.WholeResult transition = before.takeWhole(expectedHandRevision, hand, token);
        if (!transition.succeeded()) return Result.DENIED;
        ItemStack stack = transition.takenStack().orElseThrow();
        if (PortableBodyKeys.isKeyItem(stack))
            return dropPortableBody(body, excludedCarrier, level, before, transition, hand, token, stack,
                    authorityValid, committedWorldEntity, forced, target);
        if (PortableBodyCarrying.containsPortableReferences(stack))
            return dropPortableContainer(body, excludedCarrier, level, before, transition, hand, token, stack,
                    authorityValid, committedWorldEntity, forced, target);
        if (!valid(stack) || !authorityValid.getAsBoolean()
                || (!forced && !CharacterControlSystem.canAct(body)) || !finite(body.position())
                || !level.hasChunkAt(body.blockPosition())
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body))
            return Result.DENIED;
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var containedOwner = containment.ownerOf(body).orElse(null);
        if (containedOwner != null) {
            if (target != null) return Result.DENIED;
            UUID ownerIdentity = containedOwner.identity();
            LiveHands cleared = transition.state();
            if (!authorityValid.getAsBoolean() || containment.ownerOf(body).orElse(null) != containedOwner)
                return Result.DENIED;
            try { LiveHandsCommitGateway.commitOwned(body, cleared); }
            catch (RuntimeException failure) {
                return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                        ? Result.DENIED : Result.RECOVERY_REQUIRED;
            }
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != cleared || !cleared.compatible(body))
                return Result.RECOVERY_REQUIRED;
            boolean ownerStillExact = body.level() == level && !body.isRemoved() && body.isAlive()
                    && level.getEntity(body.getUUID()) == body && authorityValid.getAsBoolean()
                    && containment.ownerOf(body).filter(owner -> owner == containedOwner
                    && owner.identity().equals(ownerIdentity) && owner.isValid()).isPresent()
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == cleared;
            boolean accepted = ownerStillExact && containedOwner.acceptDroppedItem(token,
                    new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.BodyHand(body.getUUID(), hand), stack.copy());
            if (accepted) return body.level() == level && !body.isRemoved() && body.isAlive()
                    && level.getEntity(body.getUUID()) == body
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == cleared
                    && containment.ownerOf(body).filter(owner -> owner == containedOwner
                    && owner.identity().equals(ownerIdentity) && owner.isValid()).isPresent()
                    ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != cleared
                    || !cleared.compatible(body)) return Result.RECOVERY_REQUIRED;
            if (!ownerStillExact) return restoreContainedHand(body, before, cleared, hand, token, stack);
            Vec3 ownerPosition = containedOwner.position();
            if (!finite(ownerPosition) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(ownerPosition)))
                return restoreContainedHand(body, before, cleared, hand, token, stack);
            return dropAtContainedOwner(body, level, containedOwner, ownerIdentity, authorityValid,
                    before, cleared, hand, token, stack, ownerPosition, committedWorldEntity);
        }
        Vec3 sourceEye = body.getEyePosition(), sourceLook = body.getLookAngle();
        var releaseFramePoint = LoadedFrameRayQuery.currentActorPoint(level, body);
        if (releaseFramePoint == null) return Result.DENIED;
        LooseItemEntity dropped = createStagedDrop(body, stack, excludedCarrier, target);
        if (dropped == null || !sourceEye.equals(body.getEyePosition())
                || !sourceLook.equals(body.getLookAngle()) || !authorityValid.getAsBoolean()
                || !releaseFramePoint.equals(LoadedFrameRayQuery.currentActorPoint(level, body))
                || (!forced && !CharacterControlSystem.canAct(body)) || body.level() != level || body.isRemoved()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body)) return Result.DENIED;
        try {
            LiveHandsCommitGateway.commitOwned(body, transition.state());
        } catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != transition.state()
                || !transition.state().compatible(body)) return Result.RECOVERY_REQUIRED;
        // A callback can alter the world even if addFreshEntity returns false or throws. Compensate only
        // when this exact entity never joined and the exact cleared attachment still belongs to this body.
        try {
            boolean added = level.addFreshEntity(dropped);
            if (!added) return compensateUnspawnedDrop(body, level, dropped, before, transition.state(),
                    hand, token, stack);
        } catch (RuntimeException failure) {
            return compensateUnspawnedDrop(body, level, dropped, before, transition.state(),
                    hand, token, stack);
        }
        if (level.getEntity(dropped.getUUID()) == dropped && dropped.isAddedToLevel()
                && !dropped.isRemoved() && same(stack, dropped.getItem())
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == transition.state()) {
            committedWorldEntity.accept(dropped.getUUID());
            return Result.SUCCESS;
        }
        return Result.RECOVERY_REQUIRED;
    }

    private static Result dropPortableBody(LivingEntity carrier, Entity excludedCarrier, ServerLevel level,
            LiveHands before, LiveHands.WholeResult transition, String hand, ItemToken token, ItemStack key,
            BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedWorldEntity, boolean forced,
            @javax.annotation.Nullable com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        if (!PortableBodyKeys.isKey(key) || !PortableBodyCarrying.validateProspective(carrier, before)
                || !authorityValid.getAsBoolean() || (!forced && !CharacterControlSystem.canAct(carrier))) return Result.DENIED;
        var reference = PortableBodyKeys.reference(key);
        if (reference == null || !reference.token().toString().equals(token.value())) return Result.DENIED;
        LivingEntity mouse = level.getEntity(reference.body()) instanceof LivingEntity living ? living : null;
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var currentOwner = mouse == null ? null : containment.ownerOf(mouse).orElse(null);
        if (!(currentOwner instanceof PortableBodyCarrierOwner owner) || owner.carrier() != carrier
                || !owner.reference().equals(reference) || !owner.handToken().equals(token.value())) return Result.DENIED;
        double width = mouse.getBbWidth(), height = mouse.getBbHeight();
        Vec3 carrierPosition = carrier.position(), carrierEye = carrier.getEyePosition(), carrierLook = carrier.getLookAngle();
        Vec3 mousePosition = mouse.position();
        BodyItemTargetProbe.DropPlacement placement = target == null
                ? BodyItemTargetProbe.resolveDrop(level, carrier, carrier, width, height, mouse)
                : BodyItemTargetProbe.resolveDrop(level, carrier, carrier, width, height, target, mouse);
        if (placement == null || !portableBodyDropClear(level, carrier, mouse, placement.position())) return Result.DENIED;
        LiveHands cleared = transition.state();
        if (!BodyHandStateService.representable(cleared, carrier) || !authorityValid.getAsBoolean()
                || (!forced && !CharacterControlSystem.canAct(carrier))
                || carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || level.getEntity(mouse.getUUID()) != mouse || !mouse.position().equals(mousePosition)
                || !carrier.position().equals(carrierPosition) || !carrier.getEyePosition().equals(carrierEye)
                || !carrier.getLookAngle().equals(carrierLook) || !owner.isValid()
                || containment.ownerOf(mouse).orElse(null) != owner
                || !portableBodyDropClear(level, carrier, mouse, placement.position())) return Result.DENIED;
        var release = PortableBodyReleaseTransaction.capture(mouse);
        if (release == null || release.rootOwner() != owner || release.rootRevision() != before.revision()) return Result.DENIED;
        var releaseResult = PortableBodyReleaseTransaction.release(release,
                new PortableBodyReleaseTransaction.Placement(placement.position(), placement.velocity(),
                        () -> authorityValid.getAsBoolean() && (forced || CharacterControlSystem.canAct(carrier))
                                && carrier.level() == level && level.getEntity(carrier.getUUID()) == carrier
                                && carrier.position().equals(carrierPosition) && carrier.getEyePosition().equals(carrierEye)
                                && carrier.getLookAngle().equals(carrierLook)
                                && portableBodyDropClear(level, carrier, mouse, placement.position())));
        if (releaseResult == PortableBodyReleaseTransaction.Result.DENIED) return Result.DENIED;
        if (releaseResult == PortableBodyReleaseTransaction.Result.RECOVERY_REQUIRED) return Result.RECOVERY_REQUIRED;
        PortableBodyEscapeService.onDropped(mouse);
        committedWorldEntity.accept(mouse.getUUID());
        return Result.SUCCESS;
    }

    private static Result dropPortableContainer(LivingEntity carrier, Entity excludedCarrier, ServerLevel level,
            LiveHands before, LiveHands.WholeResult transition, String hand, ItemToken token, ItemStack rootStack,
            BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedWorldEntity, boolean forced,
            @javax.annotation.Nullable com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        var entry = PortableBodyCarrying.directPortableKey(rootStack);
        if (entry == null || !PortableBodyKeys.isKey(entry.stack()) || !authorityValid.getAsBoolean()
                || (!forced && !CharacterControlSystem.canAct(carrier))) return Result.DENIED;
        var reference = PortableBodyKeys.reference(entry.stack());
        LivingEntity portableBody = reference == null || !reference.dimension().equals(level.dimension().location())
                ? null : level.getEntity(reference.body()) instanceof LivingEntity living ? living : null;
        if (portableBody == null || !com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.isValidBody(portableBody, level))
            return Result.DENIED;
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var oldOwner = containment.registeredOwnerOf(portableBody).orElse(null);
        if (!(oldOwner instanceof PortableBodyCarrierOwner owner) || owner.carrier() != carrier
                || !owner.reference().equals(reference) || !owner.handToken().equals(token.value())
                || !owner.isValid()) return Result.DENIED;
        LooseObjectProfile profile;
        try { profile = LooseObjectProfile.resolveForStack(rootStack, false); }
        catch (RuntimeException invalidProfile) { return Result.DENIED; }
        Vec3 origin = carrier.position(), eye = carrier.getEyePosition(), look = carrier.getLookAngle();
        var placement = target == null
                ? BodyItemTargetProbe.resolveDrop(level, carrier, excludedCarrier, profile.physicalWidth(), profile.physicalHeight())
                : BodyItemTargetProbe.resolveDrop(level, carrier, excludedCarrier, profile.physicalWidth(), profile.physicalHeight(), target);
        if (placement == null || !portableBodyDropClear(level, carrier, portableBody, placement.position())) return Result.DENIED;
        LooseItemEntity root;
        try {
            root = LooseItemEntity.createForActor(carrier, placement.position(), rootStack.copy());
            if (placement.surfacePlacement() && placement.supportFrameId() != null) {
                var anchor = new com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchor(
                        placement.supportFrameId(), placement.supportStoragePoint());
                if (!com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchor.hydrate(level, root, anchor)) return Result.DENIED;
            }
            if (!root.prepareInitialFrameState(level) || !BodyItemTargetProbe.loadedItemBoxClear(level, root)
                    || root.getBoundingBox().intersects(carrier.getBoundingBox()) || !same(rootStack, root.getItem()))
                return Result.DENIED;
            var actorPoint = LoadedFrameRayQuery.currentActorPoint(level, carrier);
            if (actorPoint == null) return Result.DENIED;
            var velocityPoint = LoadedFrameRayQuery.pointAtWorldPosition(level,
                    placement.surfacePlacement() ? placement.supportFrameId() : actorPoint.frameId(), placement.position());
            if (velocityPoint == null) return Result.DENIED;
            Vec3 carrierVelocity = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity
                    .pointVelocityAt(level, velocityPoint);
            Vec3 launchVelocity = HandReleaseKinematics.composePlacement(placement, carrier.getDeltaMovement(), carrierVelocity);
            if (launchVelocity == null) return Result.DENIED;
            root.launch(launchVelocity, 0);
        } catch (RuntimeException failure) { return Result.DENIED; }
        if (!same(rootStack, root.getItem()) || !authorityValid.getAsBoolean()
                || (!forced && !CharacterControlSystem.canAct(carrier))
                || carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !origin.equals(carrier.position()) || !eye.equals(carrier.getEyePosition())
                || !look.equals(carrier.getLookAngle()) || !owner.isValid()
                || containment.ownerOf(portableBody).orElse(null) != owner
                || !portableBodyDropClear(level, carrier, portableBody, placement.position())) return Result.DENIED;
        try {
            if (!level.addFreshEntity(root) || root.isRemoved() || !root.isAddedToLevel()
                    || level.getEntity(root.getUUID()) != root || !same(rootStack, root.getItem())) {
                if (root.isAddedToLevel() && level.getEntity(root.getUUID()) == root) root.discard();
                return Result.DENIED;
            }
        } catch (RuntimeException failure) {
            if (root.isAddedToLevel() && level.getEntity(root.getUUID()) == root) root.discard();
            return Result.DENIED;
        }
        var worldOwner = new PortableWorldStorageOwner(level, root.getUUID(), entry.token(), reference);
        if (!containment.transfer(portableBody, owner, worldOwner)) { root.discard(); return Result.DENIED; }
        try { LiveHandsCommitGateway.commitOwned(carrier, before, transition.state()); }
        catch (RuntimeException failure) {
            if (carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    && containment.registeredOwnerOf(portableBody).orElse(null) == worldOwner
                    && containment.transfer(portableBody, worldOwner, owner)) {
                root.discard();
                return Result.DENIED;
            }
            return Result.RECOVERY_REQUIRED;
        }
        if (carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != transition.state()
                || containment.registeredOwnerOf(portableBody).orElse(null) != worldOwner
                || !same(rootStack, root.getItem())) return Result.RECOVERY_REQUIRED;
        committedWorldEntity.accept(root.getUUID());
        return Result.SUCCESS;
    }

    static boolean portableBodyDropClear(ServerLevel level, LivingEntity carrier, LivingEntity body, Vec3 position) {
        var bounds = body.getBoundingBox().move(position.subtract(body.position()));
        if (!BodyItemTargetProbe.loadedItemBoxClear(level, body, bounds)
                || bounds.intersects(carrier.getBoundingBox())) return false;
        return level.getEntities(body, bounds, entity -> entity != carrier).isEmpty();
    }

    private static Result dropAtContainedOwner(LivingEntity body, ServerLevel level,
            com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner owner, UUID ownerIdentity,
            BooleanSupplier authorityValid, LiveHands before, LiveHands cleared, String hand,
            ItemToken token, ItemStack stack, Vec3 position,
            java.util.function.Consumer<UUID> committedWorldEntity) {
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        if (!authorityValid.getAsBoolean() || !owner.isValid() || owner.identity() == null
                || !owner.identity().equals(ownerIdentity) || containment.ownerOf(body).orElse(null) != owner
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != cleared) return Result.RECOVERY_REQUIRED;
        ItemEntity dropped;
        try {
            dropped = new ItemEntity(level, position.x, position.y, position.z, stack.copy());
            dropped.setDeltaMovement(Vec3.ZERO);
            dropped.setUnlimitedLifetime();
        } catch (RuntimeException failure) { return restoreContainedHand(body, before, cleared, hand, token, stack); }
        if (!same(stack, dropped.getItem())) return restoreContainedHand(body, before, cleared, hand, token, stack);
        try {
            if (!level.addFreshEntity(dropped)) return compensateContainedWorldDrop(body, level, dropped, before, cleared, hand, token, stack);
        } catch (RuntimeException failure) {
            return compensateContainedWorldDrop(body, level, dropped, before, cleared, hand, token, stack);
        }
        if (level.getEntity(dropped.getUUID()) == dropped && dropped.isAddedToLevel() && !dropped.isRemoved()
                && same(stack, dropped.getItem())
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == cleared) {
            committedWorldEntity.accept(dropped.getUUID());
            return Result.SUCCESS;
        }
        return Result.RECOVERY_REQUIRED;
    }

    private static Result compensateContainedWorldDrop(LivingEntity body, ServerLevel level, ItemEntity dropped,
            LiveHands before, LiveHands cleared, String hand, ItemToken token, ItemStack stack) {
        if (level.getEntity(dropped.getUUID()) != null || dropped.isAddedToLevel() || dropped.isRemoved()
                || body.level() != level || body.isRemoved() || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != cleared)
            return Result.RECOVERY_REQUIRED;
        return restoreContainedHand(body, before, cleared, hand, token, stack);
    }

    private static Result restoreContainedHand(LivingEntity body, LiveHands before, LiveHands cleared,
                                                String hand, ItemToken token, ItemStack stack) {
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != cleared) return Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, before); }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                && before.token(hand).filter(token.value()::equals).isPresent()
                && before.stackCopy(hand).filter(owned -> same(stack, owned)).isPresent()
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static Result compensateUnspawnedDrop(LivingEntity body, ServerLevel level, ItemEntity dropped,
                                                   LiveHands before, LiveHands cleared, String hand,
                                                   ItemToken token, ItemStack stack) {
        if (level.getEntity(dropped.getUUID()) != null || dropped.isAddedToLevel() || dropped.isRemoved()
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != cleared
                || !cleared.compatible(body)) return Result.RECOVERY_REQUIRED;
        // Do not roll back the revision: the original request must remain stale after denial.
        LiveHands.WholeResult restored = cleared.putWhole(cleared.revision(), hand, token, stack);
        if (!restored.succeeded() || restored.state().revision() <= before.revision())
            return Result.RECOVERY_REQUIRED;
        try {
            LiveHandsCommitGateway.commitOwned(body, restored.state());
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
        return body.level() == level && !body.isRemoved() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body
                && level.getEntity(dropped.getUUID()) == null && !dropped.isAddedToLevel() && !dropped.isRemoved()
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == restored.state()
                && restored.state().compatible(body)
                && restored.state().token(hand).filter(token.value()::equals).isPresent()
                && restored.state().stackCopy(hand).filter(owned -> same(stack, owned)).isPresent()
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static Result rollbackPickup(LivingEntity body, LiveHands before, ItemEntity item,
                                         ServerLevel level, ItemStack source, Vec3 sourcePosition) {
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !before.compatible(body)
                || item.level() != level || !sourcePosition.equals(item.position())
                || item.getTarget() != null || item.hasPickUpDelay()
                || level.getEntity(item.getUUID()) != item || item.isRemoved() || !item.isAddedToLevel()
                || !item.getItem().isEmpty())
            return Result.RECOVERY_REQUIRED;
        try {
            item.setItem(source.copy());
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
        return same(source, item.getItem()) ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static boolean valid(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0
                && stack.getCount() <= stack.getMaxStackSize();
    }

    private static BodyItemTargetProbe.Target probePickup(ServerLevel level, LivingEntity body, UUID nominated,
                                                           boolean selectedPolicy) {
        return selectedPolicy
                ? BodyItemTargetProbe.probeSelected(level, body, nominated, BodyItemTargetProbe.MAX_REACH)
                : BodyItemTargetProbe.probeNominatedPickup(level, body, nominated, BodyItemTargetProbe.MAX_REACH);
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static boolean reachable(LivingEntity body, Vec3 point) {
        if (body == null || point == null || !finite(body.position()) || !finite(point)) return false;
        double reach = BodyItemTargetProbe.MAX_REACH;
        return body.position().distanceToSqr(point) <= reach * reach;
    }

    private static void transferReceipt(LivingEntity actor, String stage, UUID nominated, String hand) {
        if (!MoonStation14.LOGGER.isDebugEnabled() || !com.juicyslew.moonstation14.ms14.player_body_control.CharacterCadenceDiagnostics.allowReceipt(System.nanoTime())) return;
        var level = actor == null ? null : actor.level();
        MoonStation14.LOGGER.debug("[body-hand-transfer] stage={} actor={} world={} levelGameTime={} serverTick={} eye={} look={} yaw={} pitch={} bodyPosition={} actorPoint={} nominated={} hand={}",
                stage, actor == null ? "unknown" : actor.getUUID(), actor == null ? "unknown" : actor.level().dimension(),
                level == null ? "unknown" : level.getGameTime(), level instanceof ServerLevel server ? server.getServer().getTickCount() : "unknown",
                actor == null ? "unknown" : actor.getEyePosition(), actor == null ? "unknown" : actor.getLookAngle(),
                actor == null ? "unknown" : actor.getYRot(), actor == null ? "unknown" : actor.getXRot(),
                actor == null ? "unknown" : actor.position(), "UNKNOWN", nominated, hand);
    }
}
