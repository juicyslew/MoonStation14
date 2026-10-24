package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
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

/** Server-side world/attachment ownership transfer. Never touches the carrier inventory. */
public final class BodyHandItemTransfer {
    private static final double MAX_REACH_SQUARED = 25.0;

    private BodyHandItemTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    /** Shared world destination/entity path for trusted minted grants; caller retains input on failure. */
    public static LooseItemEntity spawnTrustedGrant(LivingEntity body, ItemStack stack) {
        if (body == null || stack == null || stack.isEmpty() || !(body.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !finite(body.position())
                || !level.hasChunkAt(body.blockPosition())) return null;
        try {
            LooseItemEntity entity = createTrustedDropEntity(body, stack, null);
            if (entity == null) return null;
            if (!same(stack, entity.getItem()) || !level.addFreshEntity(entity)) {
                if (entity.isAddedToLevel() && level.getEntity(entity.getUUID()) == entity) entity.discard();
                return null;
            }
            if (level.getEntity(entity.getUUID()) == entity && entity.isAddedToLevel()
                    && !entity.isRemoved() && same(stack, entity.getItem())) return entity;
            if (entity.isAddedToLevel() && level.getEntity(entity.getUUID()) == entity) entity.discard();
            return null;
        } catch (RuntimeException failure) { return null; }
    }

    /** Shared package-local detached drop stage for other exact-source ownership transfers. */
    static LooseItemEntity createStagedDrop(LivingEntity body, ItemStack stack) {
        return createStagedDrop(body, stack, null);
    }

    /** Detached, stationary harvest output at a checked horizontal offset from the ordinary body drop point. */
    static LooseItemEntity createStagedHarvestDrop(LivingEntity body, ItemStack stack, double offsetX, double offsetZ) {
        if (body == null || stack == null || stack.isEmpty() || !Double.isFinite(offsetX) || !Double.isFinite(offsetZ)
                || Math.abs(offsetX) > .25 || Math.abs(offsetZ) > .25) return null;
        try {
            if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                    || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body) return null;
            return createStagedHarvestDrop(level, body.position(), stack, offsetX, offsetZ);
        } catch (RuntimeException unavailable) { return null; }
    }

    /** Position-only form shared by body and system harvest; origin is the item's feet plane. */
    static LooseItemEntity createStagedHarvestDrop(ServerLevel level, Vec3 origin, ItemStack stack,
                                                   double offsetX, double offsetZ) {
        if (level == null || origin == null || stack == null || stack.isEmpty()
                || !Double.isFinite(offsetX) || !Double.isFinite(offsetZ)
                || Math.abs(offsetX) > .25 || Math.abs(offsetZ) > .25) return null;
        try {
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(stack, false);
            Vec3 placed = new Vec3(origin.x + offsetX,
                    origin.y + Math.max(.01, profile.physicalHeight() * .5), origin.z + offsetZ);
            if (!finite(placed) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(placed))
                    || !loadedItemBoxClear(level, placed, profile.physicalWidth(), profile.physicalHeight())) return null;
            LooseItemEntity entity = LooseItemEntity.create(level, placed, stack.copy());
            entity.setDeltaMovement(Vec3.ZERO);
            return same(stack, entity.getItem()) ? entity : null;
        } catch (RuntimeException unavailable) { return null; }
    }

    private static boolean loadedItemBoxClear(ServerLevel level, Vec3 placed, double width, double height) {
        return BodyItemTargetProbe.loadedItemBoxClear(level, placed, width, height);
    }

    /** Keep the authored scatter when it fits; otherwise try a bounded set within the same harvest footprint. */
    static LooseItemEntity createStagedHarvestDropWithFallback(LivingEntity body, ItemStack stack,
                                                                double preferredX, double preferredZ) {
        LooseItemEntity preferred = createStagedHarvestDrop(body, stack, preferredX, preferredZ);
        if (preferred != null) return preferred;
        // Center first, then the remaining points of a 3x3 grid. All candidates stay in SS14's
        // horizontal +/- .25 manual-harvest footprint and retain the exact same feet height.
        double[] offsets = {0, -.25, .25};
        for (double x : offsets) for (double z : offsets) {
            if (x == preferredX && z == preferredZ) continue;
            LooseItemEntity candidate = createStagedHarvestDrop(body, stack, x, z);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static LooseItemEntity createStagedDrop(LivingEntity body, ItemStack stack, Entity excludedCarrier) {
        return createTrustedDropEntity(body, stack, excludedCarrier);
    }

    private static LooseItemEntity createTrustedDropEntity(LivingEntity body, ItemStack stack, Entity excludedCarrier) {
        if (body == null || stack == null || stack.isEmpty() || !(body.level() instanceof ServerLevel level)) return null;
        try {
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(stack, false);
            var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
            var owner = containment.ownerOf(body).orElse(null);
            BodyItemTargetProbe.DropPlacement placement;
            if (owner != null) {
                Vec3 root = owner.positionFor(body);
                if (!owner.isValid() || root == null || !finite(root)
                        || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(root))) return null;
                placement = new BodyItemTargetProbe.DropPlacement(root, Vec3.ZERO);
            } else {
                placement = BodyItemTargetProbe.resolveDrop(level, body, excludedCarrier,
                        profile.physicalWidth(), profile.physicalHeight());
            }
            if (placement == null) return null;
            LooseItemEntity entity = LooseItemEntity.create(level, placement.position(), stack.copy());
            entity.launch(placement.velocity(), 0);
            return same(stack, entity.getItem()) ? entity : null;
        } catch (RuntimeException failure) { return null; }
    }

    public static Result pickup(ServerPlayer actor, UUID itemEntityUuid, String hand, long expectedHandRevision) {
        return pickup(actor, itemEntityUuid, hand, expectedHandRevision, ignored -> { });
    }

    public static Result pickup(ServerPlayer actor, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                 java.util.function.Consumer<UUID> committedSource) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || itemEntityUuid == null) return Result.DENIED;
        LivingEntity body = authority.orElseThrow().body();
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()), committedSource, false);
    }

    /** Pickup a specifically selected visible pile member; unlike legacy pickup, UUID is selection, not hint. */
    public static Result pickupSelected(ServerPlayer actor, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                        java.util.function.Consumer<UUID> committedSource) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || itemEntityUuid == null) return Result.DENIED;
        LivingEntity body = authority.orElseThrow().body();
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()), committedSource, true);
    }

    /** Trusted server-created output pickup. The supplied entity and stack are exact proofs; no ray is used. */
    public static Result pickupTrustedCreated(HandActorAuthority.Snapshot authority, LooseItemEntity exactEntity,
                                               ItemStack expectedStack,
                                               java.util.function.BooleanSupplier callerValid) {
        if (authority == null || exactEntity == null || expectedStack == null || expectedStack.isEmpty()
                || callerValid == null || !(authority.body().level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return Result.DENIED;
        LivingEntity body = authority.body();
        for (String hand : authority.handIds()) {
            LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (current == null || !current.compatible(body) || !current.handIds().equals(authority.handIds())) return Result.DENIED;
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
                valid, ignored -> { }, false, entity, stack));
    }

    // Trusted fixture seam only: tests use a real spawned body and world item, never a fake actor authority.
    static Result pickupOwned(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                              BooleanSupplier authorityValid) {
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision, authorityValid, ignored -> { }, false);
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
                authorityValid, committedSource, selectedPolicy));
    }

    private static Result pickupTransaction(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                         BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedSource,
                                         boolean selectedPolicy) {
        return pickupTransaction(body, itemEntityUuid, hand, expectedHandRevision, authorityValid, committedSource,
                selectedPolicy, null, null);
    }

    private static Result pickupTransaction(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                                         BooleanSupplier authorityValid, java.util.function.Consumer<UUID> committedSource,
                                         boolean selectedPolicy, ItemEntity exactEntity, ItemStack expectedStack) {
        if (body == null || itemEntityUuid == null || authorityValid == null) return Result.DENIED;
        if (!(body.level() instanceof ServerLevel level) || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        if (!level.getServer().isSameThread() || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != expectedHandRevision)
            return Result.DENIED;
        // The packet UUID is only a hint. The body-eye ray selects the actual source on this tick.
        // A reserved/delayed first item blocks a farther pickup rather than being skipped.
        Vec3 sourceEye = body.getEyePosition(), sourceLook = body.getLookAngle();
        boolean trusted = exactEntity != null;
        BodyItemTargetProbe.Target selected = trusted ? null : selectedPolicy
                ? BodyItemTargetProbe.probeSelected(level, body, itemEntityUuid, BodyItemTargetProbe.MAX_REACH)
                : BodyItemTargetProbe.nearestVisible(level, body, BodyItemTargetProbe.MAX_REACH);
        if (!trusted && selected == null) return Result.DENIED;
        UUID sourceId = trusted ? exactEntity.getUUID() : selected.itemId();
        Entity entity = trusted ? exactEntity : level.getEntity(sourceId);
        if (!(entity instanceof ItemEntity item) || item.isRemoved() || !item.isAddedToLevel()
                || item.level() != level || item.getTarget() != null || item.hasPickUpDelay()
                 || !reachable(body, item.position())) return Result.DENIED;
        ItemStack source = item.getItem().copy();
        if (!valid(source) || trusted && !same(source, expectedStack)) return Result.DENIED;
        Vec3 sourcePosition = item.position();
        ItemToken token = new ItemToken(UUID.randomUUID().toString());
        LiveHands.WholeResult transition = before.putWhole(expectedHandRevision, hand, token, source);
        if (transition.succeeded()) transition = new LiveHands.WholeResult(
                MeleeSelectionCooldown.stageSelectedInsertion(body, before, transition.state()),
                transition.takenStack(), transition.rejection());
        BodyItemTargetProbe.Target confirmed = trusted ? null : transition.succeeded()
                ? (selectedPolicy ? BodyItemTargetProbe.probeSelected(level, body, sourceId, BodyItemTargetProbe.MAX_REACH)
                : BodyItemTargetProbe.nearestVisible(level, body, BodyItemTargetProbe.MAX_REACH)) : null;
        if (!transition.succeeded() || !BodyHandStateService.representable(transition.state(), body)
                || !authorityValid.getAsBoolean()
                || !CharacterControlSystem.canAct(body) || body.level() != level
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || !sourcePosition.equals(item.position()) || !reachable(body, item.position())
                 || level.getEntity(sourceId) != item || item.level() != level
                || item.isRemoved() || !item.isAddedToLevel()
                || item.getTarget() != null || item.hasPickUpDelay() || !same(source, item.getItem())
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                 || !sourceEye.equals(body.getEyePosition()) || !sourceLook.equals(body.getLookAngle())
                  || !trusted && (confirmed == null || !sourceId.equals(confirmed.itemId()))
                // Shape callbacks are not assumed pure: the checked source and attachment must still
                // be the exact live owners immediately after the probe, before clearing the source.
                || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || item.level() != level || item.isRemoved()
                 || !item.isAddedToLevel() || level.getEntity(sourceId) != item
                || !sourcePosition.equals(item.position()) || !reachable(body, item.position())
                 || item.getTarget() != null || item.hasPickUpDelay() || !same(source, item.getItem())
                 || !BodyHandStateService.representable(transition.state(), body)
                 || !sourceEye.equals(body.getEyePosition()) || !sourceLook.equals(body.getLookAngle())
                  || (!trusted && (selectedPolicy
                   ? BodyItemTargetProbe.probeSelected(level, body, sourceId, BodyItemTargetProbe.MAX_REACH) == null
                   : BodyItemTargetProbe.probe(level, body, item, BodyItemTargetProbe.MAX_REACH) == null))
                 || !sourceEye.equals(body.getEyePosition()) || !sourceLook.equals(body.getLookAngle())
                 || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                 || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                 || level.getEntity(body.getUUID()) != body || item.level() != level
                 || item.isRemoved() || !item.isAddedToLevel() || level.getEntity(sourceId) != item
                 || !sourcePosition.equals(item.position()) || !reachable(body, item.position())
                 || item.getTarget() != null || item.hasPickUpDelay()
                 || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                 || !same(source, item.getItem()))
             return Result.DENIED;

         if (!trusted && item instanceof LooseItemEntity loose) {
             var contact = loose.profile().damageOnInteract();
             var contactDecision = com.juicyslew.moonstation14.ms14.loose_objects.ItemContactDamageSystem.onContact(body, loose, contact);
             if (!authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                     || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                     || body.level() != level || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                     || item.level() != level || item.isRemoved() || !item.isAddedToLevel() || level.getEntity(sourceId) != item
                     || item.getTarget() != null || item.hasPickUpDelay() || !sourcePosition.equals(item.position())
                     || !same(source, item.getItem()) || !reachable(body, item.position())) return Result.DENIED;
             if (contactDecision == com.juicyslew.moonstation14.ms14.loose_objects.ItemContactDamageSystem.Decision.HANDLED)
                 return Result.DENIED;
         }

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
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || expectedToken == null) return Result.DENIED;
        LivingEntity body = authority.orElseThrow().body();
        return dropOwned(body, actor, hand, expectedToken, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()), committedWorldEntity);
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
        return dropOwned(body, excludedCarrier, hand, expectedToken, expectedHandRevision, authorityValid, committedWorldEntity, false);
    }

    private static Result dropOwned(LivingEntity body, Entity excludedCarrier, String hand, String expectedToken,
                              long expectedHandRevision, BooleanSupplier authorityValid,
                              java.util.function.Consumer<UUID> committedWorldEntity, boolean forced) {
        return LiveHandsCommitGateway.batch(() -> dropTransaction(body, excludedCarrier, hand, expectedToken,
                expectedHandRevision, authorityValid, committedWorldEntity, forced));
    }

    private static Result dropTransaction(LivingEntity body, Entity excludedCarrier, String hand, String expectedToken,
                              long expectedHandRevision, BooleanSupplier authorityValid,
                              java.util.function.Consumer<UUID> committedWorldEntity, boolean forced) {
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
        LooseItemEntity dropped = createStagedDrop(body, stack, excludedCarrier);
        if (dropped == null || !sourceEye.equals(body.getEyePosition())
                || !sourceLook.equals(body.getLookAngle()) || !authorityValid.getAsBoolean()
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

    private static boolean same(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    private static boolean reachable(LivingEntity body, Vec3 point) {
        return finite(body.position()) && finite(point)
                && body.position().distanceToSqr(point) <= MAX_REACH_SQUARED;
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
