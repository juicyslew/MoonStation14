package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Read-only, proof-checked target action availability. This never authorizes the later action. */
public final class BodyTargetActionService {
    private static final Component SHARP_REQUIRED = Component.translatable("moonstation14.target_action.need_sharp");
    private BodyTargetActionService() { }

    /** Invalid, stale, or non-visible nominations return no snapshot and reveal no target information. */
    public static BodyTargetActionSnapshot query(ServerPlayer actor, BodyTargetActionQuery query) {
        if (actor == null || query == null) return null;
        var authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || authority.epoch() != query.bodyEpoch()
                || !HandActorAuthority.revalidate(actor, authority)
                || !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(authority.body())
                || com.juicyslew.moonstation14.ms14.containment.DisposalContainedActionPolicy.isContained(actor)) return null;
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body())
                || !hands.handIds().contains(query.handId())
                || !proofMatches(query, authority.epoch(),
                hands.revision(), hands.activeHand(), hands.handIds(), hands.token(query.handId()).orElse(null))) return null;
        if (!(authority.body().level() instanceof ServerLevel level)) return null;
        var selected = BodyItemTargetProbe.probeSelected(level, authority.body(), query.targetId(), BodyItemTargetProbe.MAX_REACH);
        var entity = selected == null ? null : level.getEntity(selected.itemId());
        if (!(entity instanceof LooseItemEntity source) || source.getItem().getCount() != 1) return null;

        boolean hasButcher = com.juicyslew.moonstation14.ms14.item_processing.LooseButcheringService
                .butcherable(source.getItem()) != null;
        var held = hands.stackCopy(query.handId()).orElse(null);
        var tool = com.juicyslew.moonstation14.ms14.tools.ToolQualities.profile(held);
        boolean sharp = held != null && held.getCount() == 1 && tool != null && tool.sharp() != null;
        Availability availability = availability(hasButcher, sharp);
        if (!HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || hands.revision() != query.expectedRevision()) return null;
        var entries = new java.util.ArrayList<BodyTargetActionSnapshot.ActionEntry>();
        if (hasButcher) entries.add(new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.BUTCHER,
                Component.translatable("moonstation14.target_action.butcher"), availability.enabled(), availability.disabledReason()));
        var gunId = BuiltInRegistries.ITEM.getKey(source.getItem().getItem());
        var gun = gunId == null ? null : com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.serverCatalog().get(gunId);
        if (gun != null && gun.revolverAmmoProvider().isPresent()) {
            var state = source.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
            entries.addAll(gunAvailability(gun, state));
        }
        return new BodyTargetActionSnapshot(query.bodyEpoch(), query.sequence(), query.expectedRevision(),
                query.handId(), query.expectedToken(), query.targetId(), source.getItem().getHoverName(), entries);
    }

    /** Pure policy used after source/tool catalogs have been resolved. */
    static Availability availability(boolean hasButcher, boolean sharp) {
        return new Availability(hasButcher && sharp, hasButcher && !sharp ? SHARP_REQUIRED : null);
    }
    static boolean proofMatches(BodyTargetActionQuery query, long epoch, long revision,
                                String activeHand, java.util.List<String> handIds, String token) {
        return query != null && epoch == query.bodyEpoch() && revision == query.expectedRevision()
                && query.handId().equals(activeHand) && handIds != null && handIds.contains(query.handId())
                && java.util.Objects.equals(query.expectedToken(), token);
    }
    record Availability(boolean enabled, Component disabledReason) { }
    static java.util.List<BodyTargetActionSnapshot.ActionEntry> gunAvailability(
            com.juicyslew.moonstation14.ms14.combat.GunProfile profile,
            com.juicyslew.moonstation14.ms14.combat.RevolverState state) {
        if (profile == null || profile.revolverAmmoProvider().isEmpty()) return java.util.List.of();
        if (state == null) state = com.juicyslew.moonstation14.ms14.combat.RevolverProviderKernel.initialize(profile);
        if (state.chambers().size() != profile.revolverAmmoProvider().orElseThrow().capacity()) return java.util.List.of();
        boolean occupied = com.juicyslew.moonstation14.ms14.combat.RevolverProviderKernel.occupiedCount(state) > 0;
        return java.util.List.of(
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.EMPTY_REVOLVER,
                        Component.translatable("moonstation14.gun-revolver-empty"), occupied,
                        occupied ? null : Component.translatable("moonstation14.target_action.revolver_empty")),
                new BodyTargetActionSnapshot.ActionEntry(BodyTargetActionSnapshot.ActionId.SPIN_REVOLVER,
                        Component.translatable("moonstation14.gun-revolver-spin"), true, null));
    }
}
