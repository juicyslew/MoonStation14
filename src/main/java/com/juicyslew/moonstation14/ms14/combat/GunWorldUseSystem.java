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

    /** Insert an exact held registered source into a loose resolved gun, hand first. */
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
                || !java.util.Objects.equals(expected.token(hand).orElse(null), token.value())
                || looseGun == null || looseGun.level() != level || looseGun.isRemoved() || !looseGun.isAddedToLevel()
                || level.getEntity(looseGun.getUUID()) != looseGun || looseGun.getItem().isEmpty()
                || looseGun.getItem().getCount() != 1
                || BodyItemTargetProbe.probe(level, authority.body(), looseGun, BodyItemTargetProbe.MAX_REACH) == null)
            return Result.DENIED;
        ItemStack originalGun = looseGun.getItem(), gunSnapshot = originalGun.copy(), heldSnapshot = held.copy();
        var entityId = looseGun.getUUID(); var position = looseGun.position();
        var gunId = BuiltInRegistries.ITEM.getKey(originalGun.getItem());
        var gunProfile = gunId == null ? null : ModGunProfiles.serverCatalog().get(gunId);
        var sourceId = BuiltInRegistries.ITEM.getKey(held.getItem());
        boolean recognized = sourceId != null && (ModCartridgeProfiles.serverCatalog().get(sourceId) != null
                || ModAmmoContainerProfiles.serverCatalog().get(sourceId) != null
                || ModBatteryProfiles.serverCatalog().get(sourceId) != null);
        boolean containerTarget = gunProfile == null && gunId != null
                && ModAmmoContainerProfiles.serverCatalog().get(gunId) != null
                && sourceId != null && ModCartridgeProfiles.serverCatalog().get(sourceId) != null;
        if (!recognized || gunProfile == null && !containerTarget) return Result.DENIED;
        var plan = GunWorldUsePolicy.prepareInsert(gunProfile, gunSnapshot, heldSnapshot);
        if (!plan.accepted()) {
            if (plan.failure() == RevolverProviderKernel.InsertFailure.FULL && gunProfile.revolverAmmoProvider().isPresent())
                actor.displayClientMessage(Component.translatable("moonstation14.gun-revolver-full"), true);
            return Result.DENIED;
        }
        ItemStack stagedGun = plan.gun();
        LiveHands stagedHands = com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.preview(
                actor, authority, expected, hand, token.value(), revision, heldSnapshot, plan.sourceRemainder()).orElse(null);
        if (stagedHands == null) return Result.DENIED;
        if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(authority.body())
                || authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != expected
                || !expected.compatible(authority.body()) || !expected.stackCopy(hand).filter(s -> matches(s, heldSnapshot)).isPresent()
                 || !java.util.Objects.equals(expected.token(hand).orElse(null), token.value())
                || looseGun.level() != level || looseGun.isRemoved() || level.getEntity(entityId) != looseGun
                 || !looseGun.getUUID().equals(entityId) || looseGun.getItem() != originalGun || !matches(originalGun, gunSnapshot)
                || !looseGun.position().equals(position)
                || BodyItemTargetProbe.probe(level, authority.body(), looseGun, BodyItemTargetProbe.MAX_REACH) == null)
            return Result.DENIED;
        if (!com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway.commit(actor, authority, stagedHands))
            return Result.DENIED;
        // The source is now irreversibly consumed. Any failure past this point is recovery, never retryable denial.
        try {
            if (looseGun.isRemoved() || level.getEntity(entityId) != looseGun || looseGun.getItem() != originalGun
                    || !matches(originalGun, gunSnapshot) || !looseGun.position().equals(position)) return Result.RECOVERY;
            looseGun.setItem(stagedGun);
            if (looseGun.isRemoved() || level.getEntity(entityId) != looseGun || looseGun.getItem() != stagedGun) return Result.RECOVERY;
            var cue = plan.cue();
            if (cue != null) PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS, position.x, position.y, position.z,
                    cue.volume(), (float) level.random.triangle(1.0, cue.pitchVariation()));
            if (gunProfile.revolverAmmoProvider().isPresent())
                actor.displayClientMessage(Component.translatable("moonstation14.gun-revolver-inserted"), true);
            return Result.SUCCESS;
        } catch (RuntimeException failure) { return Result.RECOVERY; }
    }

    public static Result spin(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands expected,
                              LooseItemEntity looseGun) {
        return tryControl(actor,authority,expected,looseGun,
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN);
    }

    public static Result empty(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands expected,
                               LooseItemEntity looseGun) {
        return tryControl(actor,authority,expected,looseGun,
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EMPTY);
    }

    /** World-source counterpart to held controls; staging is shared with GunUseSystem. */
    public static Result tryControl(ServerPlayer actor,HandActorAuthority.Snapshot authority,LiveHands expected,
            LooseItemEntity source,com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action action) {
        ServerLevel level=actor!=null&&actor.level() instanceof ServerLevel server?server:null;
        if(!valid(actor,authority,expected,source,level))return Result.DENIED;
        ItemStack original=source.getItem(),snapshot=original.copy();var uuid=source.getUUID();var position=source.position();
        var id=BuiltInRegistries.ITEM.getKey(snapshot.getItem());var profile=id==null?null:ModGunProfiles.serverCatalog().get(id);
        if(profile==null)return Result.DENIED;
        int spin=profile.revolverAmmoProvider().map(p->level.random.nextInt(p.capacity())).orElse(0);
        var plan=GunWorldUsePolicy.prepareControl(action,profile,snapshot,spin,level.getGameTime());
        if(plan==null)return Result.DENIED;
        ItemStack staged=plan.gun();var outputs=plan.outputs();
        if(!valid(actor,authority,expected,source,level)||source.getItem()!=original||!matches(original,snapshot)
                ||!source.position().equals(position))return Result.DENIED;
        source.setItem(staged);
        if(source.isRemoved()||level.getEntity(uuid)!=source||source.getItem()!=staged||!matches(staged,plan.gun()))return Result.RECOVERY;
        var cues=GunManipulationAudioPolicy.select(profile,plan.audioOperation(),plan.beforeBoltClosed(),false);
        for(var cue:cues)try{PositionalSoundRouter.play(level,cue.event(),SoundSource.PLAYERS,position.x,position.y,position.z,cue.volume(),(float)level.random.triangle(1.0,cue.pitchVariation()));}catch(RuntimeException ignored){}
        if(outputs.isEmpty())return Result.SUCCESS;
        var published=FreshWorldOutputBatch.publishAfterSourceCommitAtPosition(level,position,outputs,
                ()->!source.isRemoved()&&level.getEntity(uuid)==source&&source.getItem()==staged&&matches(source.getItem(),staged));
        return published==BodyHandItemTransfer.Result.SUCCESS?Result.SUCCESS:Result.RECOVERY;
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
