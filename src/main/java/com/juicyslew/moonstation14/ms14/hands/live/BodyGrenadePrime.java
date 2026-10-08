package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative priming of the exact grenade owned by the active body hand. */
public final class BodyGrenadePrime {
    private BodyGrenadePrime() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result prime(ServerPlayer actor, String hand, String expectedToken, long revision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return primeOwned(authority.orElseThrow().body(), hand, expectedToken, revision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    static Result primeOwned(LivingEntity body, String hand, String expectedToken, long revision,
                             java.util.function.BooleanSupplier authorityValid) {
        if (body == null || hand == null || expectedToken == null || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(body, level) || !level.hasChunkAt(body.blockPosition())
                || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision
                || !before.activeHand().equals(hand)) return Result.DENIED;
        ItemToken token;
        try { token = new ItemToken(expectedToken); }
        catch (IllegalArgumentException failure) { return Result.DENIED; }
        ItemStack source = before.stackCopy(hand).orElse(null);
        if (!before.token(hand).filter(expectedToken::equals).isPresent() || source == null
                || !source.has(ModDataComponents.GRENADE_FUSE.get()) || source.getCount() != 1
                || !source.getOrDefault(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.SAFE).equals(GrenadeFuseData.SAFE))
            return Result.DENIED;
        GrenadeFuse fuse;
        try { fuse = GrenadeFuse.safe().prime(level.getGameTime()).evaluate(level.getGameTime()).state(); }
        catch (IllegalArgumentException | IllegalStateException failure) { return Result.DENIED; }
        ItemStack replacement = source.copy();
        replacement.set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(fuse));
        var next = before.replaceOwnedStack(revision, hand, token, replacement);
        if (next.isEmpty() || !authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                || !live(body, level) || !level.hasChunkAt(body.blockPosition())
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || before.revision() != revision
                || !before.token(hand).filter(expectedToken::equals).isPresent()
                || before.stackCopy(hand).filter(current -> ItemStack.isSameItemSameComponents(source, current)
                && current.getCount() == source.getCount()).isEmpty()) return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(body, next.orElseThrow()); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        LiveHands published = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (!live(body, level) || published != next.orElseThrow() || !published.compatible(body)
                || !published.token(hand).filter(expectedToken::equals).isPresent()
                || published.stackCopy(hand).filter(stack -> ItemStack.isSameItemSameComponents(replacement, stack)
                && stack.getCount() == 1).isEmpty()) return Result.RECOVERY_REQUIRED;
        level.playSound(null, body.blockPosition(), ModSounds.GRENADE_BEEP.get(),
                SoundSource.PLAYERS, 0.35F, 1.0F);
        return Result.SUCCESS;
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}
