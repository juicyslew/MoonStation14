package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/** Ordinary USE_IN_HAND revolver cylinder action; does not fire or consume ammunition. */
public final class GunUseSystem {
    private GunUseSystem() { }
    public static com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason tryUseInHand(
            ServerPlayer actor, HandActorAuthority.Snapshot authority,
            com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest request,
            LiveHands hands, net.minecraft.world.item.ItemStack selected) {
        var id = BuiltInRegistries.ITEM.getKey(selected.getItem());
        var profile = id == null ? null : ModGunProfiles.serverCatalog().get(id);
        if (profile == null || profile.revolverAmmoProvider().isEmpty()) return null;
        var deny = com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        if (!(actor.level() instanceof net.minecraft.server.level.ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !HandActorAuthority.revalidate(actor, authority)
                || !CharacterControlSystem.canAct(authority.body()) || !hands.compatible(authority.body())
                || hands.revision() != request.expectedRevision() || !hands.handIds().contains(request.handId())
                || !authority.handIds().contains(request.handId())
                || !request.expectedToken().equals(hands.token(request.handId()).orElse(null))) return deny;
        long now = level.getGameTime();
        var gun = profile.gun();
        // SS14 TryResetDelay returns false when the entity lacks UseDelay; missing is not zero.
        if (!GunUsePolicy.canCycle(gun.useDelaySeconds().isPresent(), now,
                hands.stackCopy(request.handId()).orElseThrow().getOrDefault(ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL).nextUseTick())) return deny;
        var binding = new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD, request.handId(), hands.revision(), request.expectedToken());
        var owner = LiveOwnedItemAccess.read(authority.body(), binding).orElse(null);
        if (owner == null) return deny;
        var staged = owner.stack();
        var rounds = staged.get(ModDataComponents.REVOLVER_STATE.get());
        if (rounds == null) rounds = RevolverProviderKernel.initialize(profile);
        staged.set(ModDataComponents.REVOLVER_STATE.get(), RevolverProviderKernel.cycle(rounds));
        var runtime = staged.getOrDefault(ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL);
        staged.set(ModDataComponents.GUN_RUNTIME_STATE.get(), runtime.withUseDeadline(GunUsePolicy.deadline(now, gun.useDelaySeconds().orElseThrow())));
        if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(authority.body())
                || authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != hands
                || hands.revision() != request.expectedRevision() || !request.expectedToken().equals(hands.token(request.handId()).orElse(null))) return deny;
        return LiveOwnedItemAccess.commitComponents(owner, staged,
                () -> HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(authority.body()))
                .isPresent() ? com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK : deny;
    }
}
