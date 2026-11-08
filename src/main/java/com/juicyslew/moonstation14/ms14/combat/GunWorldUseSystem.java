package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative manipulation actions for a loose revolver. */
public final class GunWorldUseSystem {
    private GunWorldUseSystem() { }
    public enum Result { SUCCESS, DENIED, RECOVERY }

    /** Consume one exact held registered cartridge into a loose revolver, hand first. */
    public static Result insertHeldCartridge(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands expected, String hand, com.juicyslew.moonstation14.ms14.hands.ItemToken token,
            long revision, LooseItemEntity looseGun) {
        if (actor == null || authority == null || expected == null || hand == null || token == null
                || revision != expected.revision() || !(actor.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || authority.actor() != actor
                || !HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(authority.body())
                || !expected.compatible(authority.body())
                || authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != expected)
            return Result.DENIED;
        var held = expected.stackCopy(hand).orElse(null);
        if (held == null || held.isEmpty() || held.getCount() != 1
                || !java.util.Objects.equals(expected.token(hand).orElse(null), token)
                || looseGun == null || looseGun.level() != level || looseGun.isRemoved() || !looseGun.isAddedToLevel()
                || level.getEntity(looseGun.getUUID()) != looseGun || looseGun.getItem().isEmpty()
                || looseGun.getItem().getCount() != 1
                || BodyItemTargetProbe.probe(level, authority.body(), looseGun, BodyItemTargetProbe.MAX_REACH) == null)
            return Result.DENIED;
        ItemStack originalGun = looseGun.getItem(), gunSnapshot = originalGun.copy(), heldSnapshot = held.copy();
        var entityId = looseGun.getUUID(); var position = looseGun.position();
        var cartridgeId = BuiltInRegistries.ITEM.getKey(held.getItem());
        var cartridgeProfile = cartridgeId == null ? null : ModCartridgeProfiles.serverCatalog().get(cartridgeId);
        var gunId = BuiltInRegistries.ITEM.getKey(originalGun.getItem());
        var gunProfile = gunId == null ? null : ModGunProfiles.serverCatalog().get(gunId);
        if (cartridgeProfile == null || gunProfile == null || gunProfile.revolverAmmoProvider().isEmpty()) return Result.DENIED;
        var plan = GunWorldUsePolicy.prepareInsert(gunProfile, gunSnapshot, heldSnapshot);
        if (!plan.accepted()) {
            if (plan.failure() == RevolverProviderKernel.InsertFailure.FULL)
                actor.displayClientMessage(Component.translatable("moonstation14.gun-revolver-full"), true);
            return Result.DENIED;
        }
        ItemStack stagedGun = plan.gun();
        LiveHands cleared = expected.clearHeldWhole(revision, hand, token).orElse(null);
        if (cleared == null) return Result.DENIED;
        if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(authority.body())
                || authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != expected
                || !expected.compatible(authority.body()) || !expected.stackCopy(hand).filter(s -> matches(s, heldSnapshot)).isPresent()
                || !java.util.Objects.equals(expected.token(hand).orElse(null), token)
                || looseGun.level() != level || looseGun.isRemoved() || level.getEntity(entityId) != looseGun
                || looseGun.getUUID() != entityId || looseGun.getItem() != originalGun || !matches(originalGun, gunSnapshot)
                || !looseGun.position().equals(position)
                || BodyItemTargetProbe.probe(level, authority.body(), looseGun, BodyItemTargetProbe.MAX_REACH) == null)
            return Result.DENIED;
        if (!com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway.commit(actor, authority, cleared))
            return Result.DENIED;
        // The source is now irreversibly consumed. Any failure past this point is recovery, never retryable denial.
        try {
            if (looseGun.isRemoved() || level.getEntity(entityId) != looseGun || looseGun.getItem() != originalGun
                    || !matches(originalGun, gunSnapshot) || !looseGun.position().equals(position)) return Result.RECOVERY;
            looseGun.setItem(stagedGun);
            if (looseGun.isRemoved() || level.getEntity(entityId) != looseGun || looseGun.getItem() != stagedGun) return Result.RECOVERY;
            var cue = gunProfile.revolverAmmoProvider().orElseThrow().insertSound().orElse(null);
            if (cue != null) PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS, position.x, position.y, position.z,
                    cue.volume(), (float) level.random.triangle(1.0, cue.pitchVariation()));
            actor.displayClientMessage(Component.translatable("moonstation14.gun-revolver-inserted"), true);
            return Result.SUCCESS;
        } catch (RuntimeException failure) { return Result.RECOVERY; }
    }

    public static Result spin(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands expected,
                              LooseItemEntity looseGun) {
        if (actor == null || authority == null || expected == null || looseGun == null
                || !(actor.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || authority.actor() != actor
                || !HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(authority.body())
                || !expected.compatible(authority.body())
                || authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != expected
                || looseGun.level() != level || looseGun.isRemoved() || !looseGun.isAddedToLevel()
                || level.getEntity(looseGun.getUUID()) != looseGun || looseGun.getItem().isEmpty()
                || looseGun.getItem().getCount() != 1)
            return Result.DENIED;
        ItemStack original = looseGun.getItem();
        ItemStack snapshot = original.copy();
        var entityId = looseGun.getUUID();
        var position = looseGun.position();
        if (BodyItemTargetProbe.probe(level, authority.body(), looseGun, BodyItemTargetProbe.MAX_REACH) == null
                || looseGun.getItem() != original || !matches(original, snapshot)) return Result.DENIED;
        var itemId = BuiltInRegistries.ITEM.getKey(original.getItem());
        var profile = itemId == null ? null : ModGunProfiles.serverCatalog().get(itemId);
        if (profile == null || profile.revolverAmmoProvider().isEmpty()) return Result.DENIED;
        ItemStack staged = GunWorldUsePolicy.prepareSpin(profile, original, level.random.nextInt(profile.revolverAmmoProvider().orElseThrow().capacity()));
        if (staged.isEmpty()) return Result.DENIED;
        if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(authority.body())
                || authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != expected
                || !expected.compatible(authority.body()) || looseGun.level() != level || looseGun.isRemoved()
                || level.getEntity(entityId) != looseGun || looseGun.getUUID() != entityId || looseGun.getItem() != original
                || !matches(original, snapshot) || !looseGun.position().equals(position)
                || BodyItemTargetProbe.probe(level, authority.body(), looseGun, BodyItemTargetProbe.MAX_REACH) == null)
            return Result.DENIED;
        if (looseGun.getItem() != original || !matches(original, snapshot) || !looseGun.position().equals(position)) return Result.DENIED;
        looseGun.setItem(staged);
        if (looseGun.isRemoved() || level.getEntity(looseGun.getUUID()) != looseGun || looseGun.getItem() != staged) return Result.RECOVERY;
        var cue = profile.revolverAmmoProvider().orElseThrow().spinSound().orElse(null);
        if (cue != null) PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS, position.x, position.y, position.z, cue.volume(), (float) level.random.triangle(1.0, cue.pitchVariation()));
        actor.displayClientMessage(Component.translatable("moonstation14.gun-revolver-spun"), true);
        return Result.SUCCESS;
    }

    public static Result empty(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands expected,
                               LooseItemEntity looseGun) {
        ServerLevel level = actor != null && actor.level() instanceof ServerLevel server ? server : null;
        if (!valid(actor, authority, expected, looseGun, level)) return Result.DENIED;
        ItemStack original = looseGun.getItem(), snapshot = original.copy();
        var entityId = looseGun.getUUID();
        var position = looseGun.position();
        var itemId = BuiltInRegistries.ITEM.getKey(original.getItem());
        var profile = itemId == null ? null : ModGunProfiles.serverCatalog().get(itemId);
        if (profile == null || profile.revolverAmmoProvider().isEmpty()) return Result.DENIED;
        var plan = GunWorldUsePolicy.prepareEmpty(profile, snapshot);
        if (plan == null || plan.outputs().isEmpty()) return Result.DENIED;
        if (!valid(actor, authority, expected, looseGun, level) || looseGun.getItem() != original
                || !matches(original, snapshot) || !looseGun.position().equals(position)) return Result.DENIED;
        looseGun.setItem(plan.gun());
        if (looseGun.isRemoved() || level.getEntity(entityId) != looseGun || looseGun.getItem() != plan.gun()
                || !matches(looseGun.getItem(), plan.gun())) return Result.RECOVERY;
        var cue = profile.revolverAmmoProvider().orElseThrow().ejectSound().orElse(null);
        if (cue != null) PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS, position.x, position.y, position.z, cue.volume(), (float) level.random.triangle(1.0, cue.pitchVariation()));
        var published = FreshWorldOutputBatch.publishAfterSourceCommitAtPosition(level, position, plan.outputs(),
                () -> !looseGun.isRemoved() && level.getEntity(entityId) == looseGun
                        && looseGun.getItem() == plan.gun() && matches(looseGun.getItem(), plan.gun()));
        return published == BodyHandItemTransfer.Result.SUCCESS ? Result.SUCCESS : Result.RECOVERY;
    }

    private static boolean valid(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands expected,
                                 LooseItemEntity item, ServerLevel level) {
        return actor != null && level != null && level.getServer() != null && level.getServer().isSameThread()
                && authority != null && authority.actor() == actor && expected != null && item != null
                && HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(authority.body())
                && expected.compatible(authority.body())
                && authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == expected
                && item.level() == level && !item.isRemoved() && item.isAddedToLevel()
                && level.getEntity(item.getUUID()) == item && !item.getItem().isEmpty() && item.getItem().getCount() == 1
                && BodyItemTargetProbe.probe(level, authority.body(), item, BodyItemTargetProbe.MAX_REACH) != null;
    }

    private static boolean matches(ItemStack live, ItemStack expected) {
        return live != null && expected != null && live.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(live, expected);
    }
}
