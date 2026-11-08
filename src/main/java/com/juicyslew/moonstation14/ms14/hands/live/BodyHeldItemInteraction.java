package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.combat.GunWorldUsePolicy;
import com.juicyslew.moonstation14.ms14.combat.ModAmmoContainerProfiles;
import com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles;
import com.juicyslew.moonstation14.ms14.combat.ModCartridgeProfiles;
import com.juicyslew.moonstation14.ms14.combat.ModGunProfiles;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Atomic body-hand USE_ON_HAND insertion; all mutations are staged before the single publish. */
public final class BodyHeldItemInteraction {
    private BodyHeldItemInteraction() { }

    static java.util.Optional<LiveHands> stage(LiveHands hands, BodyHandActionRequest request,
            GunWorldUsePolicy.InsertPlan plan) {
        if (hands == null || request == null || plan == null || !plan.accepted()
                || request.action() != BodyHandActionRequest.Action.USE_ON_HAND
                || request.handId() == null || request.destinationHostId() == null
                || request.handId().equals(request.destinationHostId())
                || hands.revision() != request.expectedRevision()) return java.util.Optional.empty();
        try {
            var source = hands.stackCopy(request.handId());
            var target = hands.stackCopy(request.destinationHostId());
            if (source.isEmpty() || target.isEmpty()
                    || hands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty()
                    || hands.token(request.destinationHostId()).filter(request.expectedChildOrSourceToken()::equals).isEmpty()
                    || hands.reservation(request.handId()).isPresent()
                    || hands.reservation(request.destinationHostId()).isPresent()) return java.util.Optional.empty();
            var afterSource = hands.replaceHandUseStack(hands.revision(), request.handId(), request.expectedToken(),
                    source.orElseThrow(), plan.sourceRemainder());
            if (afterSource.isEmpty()) return java.util.Optional.empty();
            return afterSource.orElseThrow().replaceOwnedComponents(afterSource.orElseThrow().revision(),
                    request.destinationHostId(), new ItemToken(request.expectedChildOrSourceToken()), plan.gun());
        } catch (RuntimeException failure) { return java.util.Optional.empty(); }
    }

    public static BodyHandActionResult.Reason use(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            BodyHandActionRequest request, LiveHands expectedHands) {
        if (actor == null || authority == null || authority.actor() != actor || request == null
                || expectedHands == null || request.action() != BodyHandActionRequest.Action.USE_ON_HAND
                || request.handId() == null || request.destinationHostId() == null
                || request.handId().equals(request.destinationHostId())
                || expectedHands.revision() != request.expectedRevision()
                || !HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != expectedHands)
            return BodyHandActionResult.Reason.DENIED;
        try {
            var sourceCopy = expectedHands.stackCopy(request.handId());
            var targetCopy = expectedHands.stackCopy(request.destinationHostId());
            if (sourceCopy.isEmpty() || targetCopy.isEmpty()
                    || expectedHands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty()
                    || expectedHands.token(request.destinationHostId()).filter(request.expectedChildOrSourceToken()::equals).isEmpty()
                    || expectedHands.reservation(request.handId()).isPresent()
                    || expectedHands.reservation(request.destinationHostId()).isPresent())
                return BodyHandActionResult.Reason.DENIED;
            var gunId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetCopy.orElseThrow().getItem());
            var profile = ModGunProfiles.serverCatalog().get(gunId);
            var targetItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetCopy.orElseThrow().getItem());
            if (profile == null && (targetItemId == null || ModAmmoContainerProfiles.serverCatalog().get(targetItemId) == null))
                return BodyHandActionResult.Reason.DENIED;
            var plan = GunWorldUsePolicy.prepareInsert(profile, targetCopy.orElseThrow(), sourceCopy.orElseThrow());
            if (!plan.accepted()) return BodyHandActionResult.Reason.DENIED;
            var targetStaged = stage(expectedHands, request, plan).orElse(null);
            if (targetStaged == null || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != expectedHands
                    || !HandActorAuthority.revalidate(actor, authority)) return BodyHandActionResult.Reason.DENIED;
            if (!LiveHandsCommitGateway.commit(actor, authority, targetStaged)) return BodyHandActionResult.Reason.DENIED;
            var cue = plan.cue();
            if (cue != null && actor.level() instanceof net.minecraft.server.level.ServerLevel level)
                com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter.play(level, cue.event(), net.minecraft.sounds.SoundSource.PLAYERS,
                        authority.body().getX(), authority.body().getY(), authority.body().getZ(), cue.volume(), (float) level.random.triangle(1.0, cue.pitchVariation()));
            return BodyHandActionResult.Reason.OK;
        } catch (RuntimeException failure) {
            return BodyHandActionResult.Reason.DENIED;
        }
    }
}
