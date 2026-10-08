package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.custom.AtmosphereAnalyzerItem;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.tool.ModTools;
import com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelService;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.server.level.ServerPlayer;

/** Extensible authoritative dispatcher for held-item interactions. */
public final class BodyHeldItemActions {
    private BodyHeldItemActions() { }

    public static BodyHandActionResult.Reason use(ServerPlayer actor, BodyHandActionRequest request) {
        if (actor == null || request == null || request.action() != BodyHandActionRequest.Action.USE_HELD)
            return BodyHandActionResult.Reason.DENIED;
        var resolved = HandActorAuthority.resolve(actor);
        if (resolved.isEmpty()) return BodyHandActionResult.Reason.DENIED;
        var authority = resolved.orElseThrow();
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body()) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand())
                || !hands.handIds().contains(request.handId()) || !authority.handIds().contains(request.handId())
                || !BodyWorldItemActions.expectedTokenMatches(request.expectedToken(), hands.token(request.handId()))
                || !HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || hands.revision() != request.expectedRevision()) return BodyHandActionResult.Reason.DENIED;
        var stack = hands.stackCopy(request.handId()).orElse(null);
        if (stack == null || !(stack.getItem() instanceof AtmosphereAnalyzerItem)
                || !(authority.body().level() instanceof net.minecraft.server.level.ServerLevel level))
            return BodyHandActionResult.Reason.DENIED;
        AtmosphereAnalyzerItem.reportForBody(level, actor, authority.body(),
                AtmosphereAnalyzerItem.eyeTarget(authority.body().getEyePosition()), null);
        return BodyHandActionResult.Reason.OK;
    }

    public static BodyHandActionResult.Reason activate(ServerPlayer actor, BodyHandActionRequest request) {
        // Welder activation is the held item's normal in-hand use capability. Do not
        // revive the removed block-use-only ACTIVATE_HELD wire action.
        if (actor == null || request == null || request.action() != BodyHandActionRequest.Action.USE_IN_HAND)
            return BodyHandActionResult.Reason.DENIED;
        var resolved = HandActorAuthority.resolve(actor);
        if (resolved.isEmpty()) return BodyHandActionResult.Reason.DENIED;
        var authority = resolved.orElseThrow();
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body()) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand()) || !hands.handIds().contains(request.handId())
                || !authority.handIds().contains(request.handId())
                || !BodyWorldItemActions.expectedTokenMatches(request.expectedToken(), hands.token(request.handId()))
                || !HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands)
            return BodyHandActionResult.Reason.DENIED;
        var before = hands.stackCopy(request.handId()).orElse(null);
        if (before != null) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(before.getItem());
            try {
                var medical = id == null ? null : com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                        com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(authority.body().level()), id);
                if (medical != null && medical.healthAnalyzer().isPresent())
                    return com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerServer.activate(actor, request.handId(),
                            request.expectedToken(), request.expectedRevision(), request.sequence());
            } catch (RuntimeException ignored) { }
        }
        if (before == null || !before.is(ModItems.WELDER.get())
                || !(authority.body().level() instanceof net.minecraft.server.level.ServerLevel level))
            return BodyHandActionResult.Reason.DENIED;
        var prototypes = PrototypeRuntime.serverManager();
        if (!prototypes.registeredTypes().contains(ModTools.TOOL_TYPE)) return BodyHandActionResult.Reason.DENIED;
        var tool = prototypes.snapshot(ModTools.TOOL_TYPE).get(ModTools.WELDER);
        if (tool == null) return BodyHandActionResult.Reason.DENIED;
        var after = before.copy();
        long tick = level.getServer().overworld().getGameTime();
        if (!WelderFuelService.toggle(after, tool, tick)) return BodyHandActionResult.Reason.DENIED;
        var committed = BodyHeldItemMutation.commit(actor, authority, hands, request.handId(), request.expectedToken(),
                request.expectedRevision(), before, after);
        return committed == BodyHeldItemMutation.Result.SUCCESS
                ? BodyHandActionResult.Reason.OK : BodyHandActionResult.Reason.DENIED;
    }
}
