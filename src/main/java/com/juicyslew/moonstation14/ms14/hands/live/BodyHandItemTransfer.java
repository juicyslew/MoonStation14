package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Server-side world/attachment ownership transfer. Never touches the carrier inventory. */
public final class BodyHandItemTransfer {
    private static final double MAX_REACH_SQUARED = 25.0;

    private BodyHandItemTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result pickup(ServerPlayer actor, UUID itemEntityUuid, String hand, long expectedHandRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || itemEntityUuid == null) return Result.DENIED;
        LivingEntity body = authority.orElseThrow().body();
        return pickupOwned(body, itemEntityUuid, hand, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Trusted fixture seam only: tests use a real spawned body and world item, never a fake actor authority.
    static Result pickupOwned(LivingEntity body, UUID itemEntityUuid, String hand, long expectedHandRevision,
                              BooleanSupplier authorityValid) {
        if (body == null || itemEntityUuid == null || authorityValid == null) return Result.DENIED;
        if (!(body.level() instanceof ServerLevel level) || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        if (!level.getServer().isSameThread() || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body) return Result.DENIED;
        Entity entity = level.getEntity(itemEntityUuid);
        if (!(entity instanceof ItemEntity item) || item.isRemoved() || !item.isAddedToLevel()
                || item.level() != level || item.getTarget() != null || item.hasPickUpDelay()
                 || !reachable(body, item.position())) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != expectedHandRevision)
            return Result.DENIED;
        ItemStack source = item.getItem().copy();
        if (!valid(source)) return Result.DENIED;
        Vec3 sourcePosition = item.position();
        ItemToken token = new ItemToken(UUID.randomUUID().toString());
        LiveHands.WholeResult transition = before.putWhole(expectedHandRevision, hand, token, source);
        if (!transition.succeeded() || !authorityValid.getAsBoolean()
                || !CharacterControlSystem.canAct(body) || body.level() != level
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || !sourcePosition.equals(item.position()) || !reachable(body, item.position())
                || level.getEntity(itemEntityUuid) != item || item.level() != level
                || item.isRemoved() || !item.isAddedToLevel()
                || item.getTarget() != null || item.hasPickUpDelay() || !same(source, item.getItem())
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                || BodyItemTargetProbe.probe(level, body, item, BodyItemTargetProbe.MAX_REACH) == null
                // Shape callbacks are not assumed pure: the checked source and attachment must still
                // be the exact live owners immediately after the probe, before clearing the source.
                || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || item.level() != level || item.isRemoved()
                || !item.isAddedToLevel() || level.getEntity(itemEntityUuid) != item
                || !sourcePosition.equals(item.position()) || !reachable(body, item.position())
                || item.getTarget() != null || item.hasPickUpDelay() || !same(source, item.getItem()))
            return Result.DENIED;

        // The entity must stop owning the stack before the attachment starts owning it.
        try {
            item.setItem(ItemStack.EMPTY);
        } catch (RuntimeException failure) {
            return item.level() == level && !item.isRemoved() && item.isAddedToLevel()
                    && level.getEntity(itemEntityUuid) == item && sourcePosition.equals(item.position())
                    && body.level() == level && !body.isRemoved() && body.isAddedToLevel()
                    && level.getEntity(body.getUUID()) == body
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    && same(source, item.getItem()) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        if (!item.getItem().isEmpty() || item.level() != level || !sourcePosition.equals(item.position())
                || item.getTarget() != null || item.hasPickUpDelay()
                || level.getEntity(itemEntityUuid) != item || item.isRemoved() || !item.isAddedToLevel()
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before)
            return Result.RECOVERY_REQUIRED;
        try {
            body.setData(ModDataAttachments.LIVE_HANDS.get(), transition.state());
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
        return item.isRemoved() && level.getEntity(itemEntityUuid) != item
                ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    public static Result drop(ServerPlayer actor, String hand, String expectedToken, long expectedHandRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || expectedToken == null) return Result.DENIED;
        LivingEntity body = authority.orElseThrow().body();
        return dropOwned(body, hand, expectedToken, expectedHandRevision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Fixture seam: the public route alone resolves the actor. Tests exercise the same world/attachment transfer
    // on a real spawned body, without synthesizing a controller or changing the production authorization route.
    static Result dropOwned(LivingEntity body, String hand, String expectedToken, long expectedHandRevision,
                            BooleanSupplier authorityValid) {
        if (expectedToken == null || body == null || body.isRemoved() || !body.isAddedToLevel()) return Result.DENIED;
        if (!(body.level() instanceof ServerLevel level) || !CharacterControlSystem.canAct(body)
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
                || !CharacterControlSystem.canAct(body) || !finite(body.position())
                || !level.hasChunkAt(body.blockPosition())
                || body.level() != level || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body))
            return Result.DENIED;
        Vec3 forward = BodyItemTargetProbe.forwardDropSpot(level, body);
        Vec3 position = forward == null ? body.position() : forward;
        ItemEntity dropped;
        try {
            dropped = new ItemEntity(level, position.x, position.y, position.z, stack.copy());
            dropped.setDeltaMovement(Vec3.ZERO);
        } catch (RuntimeException failure) {
            return Result.DENIED;
        }
        if (!same(stack, dropped.getItem())) return Result.DENIED;
        try {
            body.setData(ModDataAttachments.LIVE_HANDS.get(), transition.state());
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
        return level.getEntity(dropped.getUUID()) == dropped && dropped.isAddedToLevel()
                && !dropped.isRemoved() && same(stack, dropped.getItem())
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == transition.state()
                ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
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
            body.setData(ModDataAttachments.LIVE_HANDS.get(), restored.state());
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
