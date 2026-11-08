package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import com.juicyslew.moonstation14.ms14.hands.live.LiveWieldSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/** Ordinary USE_IN_HAND revolver cylinder action; does not fire or consume ammunition. */
public final class GunUseSystem {
    private GunUseSystem() { }

    /** Server-held-gun controls share the exact held binding and component commit owner. */
    public static com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason tryControl(
            ServerPlayer actor, HandActorAuthority.Snapshot authority,
            com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest request,
            LiveHands hands, net.minecraft.world.item.ItemStack selected) {
        var action = request == null ? null : request.action();
        if (actor == null || authority == null || request == null || hands == null || selected == null || selected.isEmpty()
                || request.expectedToken() == null
                || !request.expectedToken().equals(hands.token(request.handId()).orElse(null)))
            return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        var id = BuiltInRegistries.ITEM.getKey(selected.getItem());
        var profile = id == null ? null : ModGunProfiles.serverCatalog().get(id);
        if (profile == null) return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        if (action == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_TOGGLE_WIELD) {
            if (profile.wieldable().isEmpty()) return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
            boolean wasWielded = LiveWieldSystem.isWielded(actor, request.handId(), request.expectedToken());
            var result = LiveWieldSystem.toggle(actor, request.handId(), request.expectedToken());
            if (result && !wasWielded && LiveWieldSystem.isWielded(actor, request.handId(), request.expectedToken()))
                playCues(actor, authority, GunManipulationAudioPolicy.select(profile, GunManipulationAudioPolicy.Operation.WIELD, false, true));
            return result ? com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK
                    : com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        }
        if (action == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_FIRE_MODE) {
            var binding = new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD,
                    request.handId(), hands.revision(), request.expectedToken());
            var owner = LiveOwnedItemAccess.read(authority.body(), binding).orElse(null);
            if (owner == null) return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
            var plan=GunWorldUsePolicy.prepareControl(action,profile,owner.stack(),0);
            if(plan==null)return denied();
            var staged = plan.gun();
            var committedMode = commit(actor, authority, hands, request, owner, staged).isPresent();
            if (committedMode) playCues(actor, authority, GunManipulationAudioPolicy.select(profile, plan.audioOperation(), false, false));
            return committedMode
                    ? com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK
                    : com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        }
        var binding = new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD,
                request.handId(), hands.revision(), request.expectedToken());
        var owner = LiveOwnedItemAccess.read(authority.body(), binding).orElse(null);
        if (owner == null) return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        int spinIndex=profile.revolverAmmoProvider().map(p->actor.getRandom().nextInt(p.capacity())).orElse(0);
        var plan=GunWorldUsePolicy.prepareControl(action,profile,owner.stack(),spinIndex);
        if(plan==null)return denied();
        var staged=plan.gun();var outputs=new java.util.ArrayList<>(plan.outputs());
        var ejectAudio=plan.audioOperation();boolean beforeBoltClosed=plan.beforeBoltClosed();
        boolean handEjection = action == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EJECT_MAGAZINE
                || action == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EJECT_CELL;
        if (handEjection) {
            var sourceCandidate = LiveOwnedItemAccess.stageComponents(owner, staged,
                    () -> valid(actor, authority, hands, request)).orElse(null);
            if (sourceCandidate == null) return denied();
            var grant = sourceCandidate.grant(sourceCandidate.revision(), outputs.get(0));
            var finalHands = grant.changed() ? grant.state() : sourceCandidate;
            if (!valid(actor, authority, hands, request)) return denied();
            try { com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway.commitOwned(authority.body(), hands, finalHands); }
            catch (IllegalStateException rejected) { return denied(); }
            playCues(actor, authority, GunManipulationAudioPolicy.select(profile, ejectAudio, false, false));
            outputs.clear();
            if (!grant.remainder().isEmpty()) outputs.add(grant.remainder());
            if (outputs.isEmpty()) return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK;
            var committedOwner = new LiveOwnedItemAccess.Committed(finalHands, finalHands.revision());
            if (!(actor.level() instanceof net.minecraft.server.level.ServerLevel level)) return denied();
            var committedGun = staged;
            var published = CartridgeOutputPublisher.publish(level,actor,authority.body(),authority.body().position(),authority.body().getLookAngle(),
                    request.handId().toLowerCase(java.util.Locale.ROOT).contains("left"),outputs,
                    () -> committedOwnerStillValid(actor, authority, committedOwner, request, committedGun));
            return published == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS
                    ? com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK
                    : com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        }
        var committedOwner = commit(actor, authority, hands, request, owner, staged);
        if (committedOwner.isEmpty()) return denied();
        if (ejectAudio != null) playCues(actor, authority, GunManipulationAudioPolicy.select(profile, ejectAudio, beforeBoltClosed, false));
        if (!outputs.isEmpty()) {
            if (!(actor.level() instanceof net.minecraft.server.level.ServerLevel level)) return denied();
            var committed = staged;
            var published = CartridgeOutputPublisher.publish(level,actor,authority.body(),authority.body().position(),authority.body().getLookAngle(),
                    request.handId().toLowerCase(java.util.Locale.ROOT).contains("left"),outputs,
                    () -> committedOwnerStillValid(actor, authority, committedOwner.orElseThrow(), request, committed));
            if (published != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        }
        return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK;
    }

    private static void playCues(ServerPlayer actor, HandActorAuthority.Snapshot authority, java.util.List<PositionalSoundCue> cues) {
        if (authority == null || !(authority.body().level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var position = authority.body().position();
        for (var cue : cues) try {
            com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter.play(level, cue.event(), net.minecraft.sounds.SoundSource.PLAYERS,
                    position.x, position.y, position.z, cue.volume(), (float) level.random.triangle(1.0, cue.pitchVariation()));
        } catch (RuntimeException ignored) { /* Best-effort audio cannot change committed ownership. */ }
    }

    private static com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason denied() {
        return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
    }

    public static com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason tryUseInHand(
            ServerPlayer actor, HandActorAuthority.Snapshot authority,
            com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest request,
            LiveHands hands, net.minecraft.world.item.ItemStack selected) {
        var id = BuiltInRegistries.ITEM.getKey(selected.getItem());
        var profile = id == null ? null : ModGunProfiles.serverCatalog().get(id);
        if (profile == null) return null;
        var deny = com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED;
        if (!(actor.level() instanceof net.minecraft.server.level.ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !HandActorAuthority.revalidate(actor, authority)
                || !CharacterControlSystem.canAct(authority.body()) || !hands.compatible(authority.body())
                || hands.revision() != request.expectedRevision() || !hands.handIds().contains(request.handId())
                || !authority.handIds().contains(request.handId())
                || !request.expectedToken().equals(hands.token(request.handId()).orElse(null))) return deny;
        long now = level.getGameTime();
        var gun = profile.gun();
        var token = request.expectedToken();
        if (profile.wieldable().isPresent()) {
            var wieldResult = LiveWieldSystem.useInHand(actor, request.handId(), token,
                    profile.wieldable().orElseThrow().unwieldOnUse());
            if (wieldResult == LiveWieldSystem.UseResult.HANDLED) {
                boolean nowWielded = LiveWieldSystem.isWielded(actor, request.handId(), token);
                if (nowWielded) playCues(actor, authority, GunManipulationAudioPolicy.select(profile, GunManipulationAudioPolicy.Operation.WIELD, false, true));
                return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK;
            }
            if (wieldResult == LiveWieldSystem.UseResult.DENIED) return deny;
        }
        var operation = GunUsePolicy.operation(profile);
        if (operation == GunUsePolicy.Operation.NONE) return null;
        var current = hands.stackCopy(request.handId()).orElseThrow();
        var currentRuntime = current.getOrDefault(ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL);
        var binding = new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD, request.handId(), hands.revision(), request.expectedToken());
        var owner = LiveOwnedItemAccess.read(authority.body(), binding).orElse(null);
        if (owner == null) return deny;
        if (profile.ballisticProvider().isPresent()) {
            var plan=GunWorldUsePolicy.prepareControl(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_ACTION,profile,owner.stack(),0,now);
            if(plan==null)return deny;var staged=plan.gun();
            var committed = commit(actor, authority, hands, request, owner, staged);
            if (committed.isEmpty()) return deny;
            playCues(actor, authority, GunManipulationAudioPolicy.select(profile, plan.audioOperation(), false, false));
            if (!plan.outputs().isEmpty()) {
                var published = CartridgeOutputPublisher.publish(level,actor,authority.body(),authority.body().position(),authority.body().getLookAngle(),
                        request.handId().toLowerCase(java.util.Locale.ROOT).contains("left"),plan.outputs(),
                         () -> committedOwnerStillValid(actor, authority, committed.orElseThrow(), request, staged));
                if (published != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                    return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.RECOVERY_REQUIRED;
            }
            return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK;
        }
        if (profile.batteryFireModes().filter(modes -> modes.size() > 1).isPresent()) {
            var plan=GunWorldUsePolicy.prepareControl(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_BATTERY_MODE,profile,owner.stack(),0,now);
            if(plan==null)return deny;var staged=plan.gun();
            var committedMode = commit(actor, authority, hands, request, owner, staged).isPresent();
            if (committedMode) playCues(actor, authority, GunManipulationAudioPolicy.select(profile, plan.audioOperation(), false, false));
            return committedMode ? com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK : deny;
        }
        if (profile.magazineChamberProvider().isPresent()) {
            var staged = GunItemFactory.initializeCopy(owner.stack());
            var state = staged.getOrDefault(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),
                    MagazineChamberProviderKernel.initialize(profile, itemId -> {
                        var item = BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow(() -> new IllegalArgumentException("Unknown item " + itemId));
                        return new net.minecraft.world.item.ItemStack(item);
                    }));
            var plan = MagazineChamberProviderKernel.rack(state, profile);
            staged.set(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(), plan.state());
            var committed = commit(actor, authority, hands, request, owner, staged);
            if (committed.isEmpty()) return deny;
            playCues(actor, authority, GunManipulationAudioPolicy.select(profile, GunManipulationAudioPolicy.Operation.MAGAZINE_RACK,
                    state.boltClosed(), false));
            if (plan.ejected().isPresent()) {
                var published = CartridgeOutputPublisher.publish(level,actor,authority.body(),authority.body().position(),authority.body().getLookAngle(),
                        request.handId().toLowerCase(java.util.Locale.ROOT).contains("left"),java.util.List.of(plan.ejected().orElseThrow()),
                         () -> committedOwnerStillValid(actor, authority, committed.orElseThrow(), request, staged));
                if (published != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                    return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.RECOVERY_REQUIRED;
            }
            return com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK;
        }
        if (profile.revolverAmmoProvider().isEmpty()) return null;
        // Only revolvers require UseDelay; missing is not an implicit zero.
        if (!GunUsePolicy.canCycle(gun.useDelaySeconds().isPresent(), now, currentRuntime.nextUseTick())) return deny;
        var staged = GunItemFactory.initializeCopy(owner.stack());
        var rounds = staged.get(ModDataComponents.REVOLVER_STATE.get());
        if (rounds == null) rounds = RevolverProviderKernel.initialize(profile);
        staged.set(ModDataComponents.REVOLVER_STATE.get(), RevolverProviderKernel.cycle(rounds));
        var runtime = staged.getOrDefault(ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL);
        staged.set(ModDataComponents.GUN_RUNTIME_STATE.get(), runtime.withUseDeadline(GunUsePolicy.deadline(now, gun.useDelaySeconds().orElseThrow())));
        if (!HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(authority.body())
                || authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != hands
                || hands.revision() != request.expectedRevision() || !request.expectedToken().equals(hands.token(request.handId()).orElse(null))) return deny;
        return commit(actor, authority, hands, request, owner, staged).isPresent()
                ? com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK : deny;
    }

    private static java.util.Optional<LiveOwnedItemAccess.Committed> commit(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands hands,
            com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest request,
            LiveOwnedItemAccess.OwnerView owner, net.minecraft.world.item.ItemStack staged) {
        if (!valid(actor, authority, hands, request)) return java.util.Optional.empty();
        return LiveOwnedItemAccess.commitComponents(owner, staged, () -> valid(actor, authority, hands, request));
    }
    private static boolean committedOwnerStillValid(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveOwnedItemAccess.Committed committed, com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest request, net.minecraft.world.item.ItemStack staged) {
        LiveHands hands = committed.state();
        return postCommitMatches(authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()),
                hands, hands.revision(), committed.revision(), hands.stackCopy(request.handId()).orElse(null), staged)
                && HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(authority.body());
    }
    static boolean postCommitMatches(LiveHands attached, LiveHands expected, long revision, long expectedRevision,
            net.minecraft.world.item.ItemStack live, net.minecraft.world.item.ItemStack staged) {
        return attached != null && expected != null && attached == expected && revision == expectedRevision && live != null && staged != null
                && net.minecraft.world.item.ItemStack.isSameItemSameComponents(live, staged) && live.getCount() == staged.getCount();
    }
    private static boolean valid(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands hands,
            com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest request) {
        return HandActorAuthority.revalidate(actor, authority) && CharacterControlSystem.canAct(authority.body())
                && authority.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == hands
                && hands.revision() == request.expectedRevision()
                && request.expectedToken().equals(hands.token(request.handId()).orElse(null));
    }
}
