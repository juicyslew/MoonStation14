package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ThrownGrenadeEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative transfer of the active body's armed grenade into a projectile. */
public final class BodyGrenadeThrow {
    private BodyGrenadeThrow() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result throwGrenade(ServerPlayer actor, String activeHand, String expectedToken,
                                      long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty() || expectedToken == null) return Result.DENIED;
        var resolved = authority.orElseThrow();
        return throwOwned(resolved.body(), activeHand, expectedToken, expectedRevision,
                () -> HandActorAuthority.revalidate(actor, resolved));
    }

    static Result throwOwned(LivingEntity body, String hand, String expectedToken, long revision,
                                      java.util.function.BooleanSupplier authorityValid) {
        return LiveHandsCommitGateway.batch(() -> throwTransaction(body, hand, expectedToken, revision, authorityValid));
    }

    private static Result throwTransaction(LivingEntity body, String hand, String expectedToken, long revision,
                                      java.util.function.BooleanSupplier authorityValid) {
        if (body == null || hand == null || expectedToken == null || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !CharacterControlSystem.canAct(body) || !live(body, level)
                || !level.hasChunkAt(body.blockPosition())) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || !before.activeHand().equals(hand)) return Result.DENIED;
        ItemToken token;
        try { token = new ItemToken(expectedToken); }
        catch (IllegalArgumentException failure) { return Result.DENIED; }
        if (!before.token(hand).filter(expectedToken::equals).isPresent()) return Result.DENIED;
        LiveHands.WholeResult transition = before.takeWhole(revision, hand, token);
        if (!transition.succeeded()) return Result.DENIED;
        ItemStack stack = transition.takenStack().orElseThrow();
        GrenadeFuseData fuse = stack.getOrDefault(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.SAFE);
        long now = level.getGameTime();
        if (!stack.is(ModItems.GRENADE) || stack.getCount() != 1 || fuse.phase() != GrenadeFuse.Phase.ARMED
                || fuse.deadline() <= now || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                || !live(body, level) || !level.hasChunkAt(body.blockPosition())
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body))
            return Result.DENIED;

        // Start just in front of the controlled body's eyes; validate the target chunk before constructing/spawning.
        Vec3 launch = body.getEyePosition().add(body.getLookAngle().scale(0.5));
        if (!finite(launch) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(launch))) return Result.DENIED;
        ThrownGrenadeEntity thrown;
        try {
            thrown = new ThrownGrenadeEntity(level, body, stack.copy());
            thrown.setPos(launch.x, launch.y, launch.z);
            thrown.shootFromRotation(body, body.getXRot(), body.getYRot(), 0.0F, 1.5F, 1.0F);
        } catch (RuntimeException failure) { return Result.DENIED; }
        if (!same(stack, thrown.getItem()) || !fuse.equals(thrown.getItem().get(ModDataComponents.GRENADE_FUSE.get())))
            return Result.DENIED;

        try { LiveHandsCommitGateway.commitOwned(body, transition.state()); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != transition.state()
                || !transition.state().compatible(body)) return Result.RECOVERY_REQUIRED;
        try {
            if (!level.addFreshEntity(thrown))
                return compensate(body, level, thrown, before, transition.state(), hand, token, stack);
        } catch (RuntimeException failure) {
            return compensate(body, level, thrown, before, transition.state(), hand, token, stack);
        }
        boolean spawned = level.getEntity(thrown.getUUID()) == thrown && thrown.isAddedToLevel() && !thrown.isRemoved();
        boolean cleared = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == transition.state();
        if (!spawned || !cleared || !same(stack, thrown.getItem())) return Result.RECOVERY_REQUIRED;
        level.playSound(null, body.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        return Result.SUCCESS;
    }

    private static Result compensate(LivingEntity body, ServerLevel level, ThrownGrenadeEntity entity,
                                     LiveHands before, LiveHands cleared, String hand, ItemToken token, ItemStack stack) {
        if (level.getEntity(entity.getUUID()) != null || entity.isAddedToLevel() || entity.isRemoved()
                || !live(body, level) || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != cleared
                || !cleared.compatible(body)) return Result.RECOVERY_REQUIRED;
        LiveHands.WholeResult restored = cleared.putWhole(cleared.revision(), hand, token, stack);
        if (!restored.succeeded() || restored.state().revision() <= before.revision()) return Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, restored.state()); }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        return live(body, level) && level.getEntity(entity.getUUID()) == null && !entity.isAddedToLevel()
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == restored.state()
                && restored.state().compatible(body) && restored.state().token(hand).filter(token.value()::equals).isPresent()
                && restored.state().stackCopy(hand).filter(owned -> same(stack, owned)).isPresent()
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
    private static boolean finite(Vec3 pos) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z);
    }
    private static boolean same(ItemStack a, ItemStack b) {
        return b != null && a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }
}
