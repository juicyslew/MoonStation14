package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.CableCutterItem;
import com.juicyslew.moonstation14.item.custom.CableSpoolItem;
import com.juicyslew.moonstation14.item.custom.CrowbarItem;
import com.juicyslew.moonstation14.item.custom.AtmosphereAnalyzerItem;
import com.juicyslew.moonstation14.item.custom.SteelItem;
import com.juicyslew.moonstation14.item.custom.StationFloorTileItem;
import com.juicyslew.moonstation14.item.custom.StationFloorTilePryItem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyBlockItemPlacement;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGrenadePrime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Extensible authoritative dispatcher for world item interactions. */
public final class BodyWorldItemActions {
    private BodyWorldItemActions() { }

    public static BodyBlockUseResult.Outcome use(ServerPlayer actor, BodyBlockUseRequest request,
                                                   BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null
                || request.intent() != BodyBlockUseRequest.Intent.USE) return BodyBlockUseResult.Outcome.DENIED;
        var authority = admission.authority();
        var body = authority.body();
        var hands = admission.hands();
        if (!HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(body) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand())
                || !expectedTokenMatches(request.expectedToken(), hands.token(request.handId()))
                || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.hasChunkAt(admission.hit().pos())
                || !level.getWorldBorder().isWithinBounds(admission.hit().pos())
                || !level.mayInteract(actor, admission.hit().pos())) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack stack = hands.stackCopy(request.handId()).orElse(null);
        if (stack == null || stack.isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        if (stack.is(ModItems.GRENADE.get())) {
            BodyGrenadePrime.Result primed = BodyGrenadePrime.prime(actor, request.handId(),
                    request.expectedToken(), request.expectedRevision());
            if (primed == BodyGrenadePrime.Result.SUCCESS) {
                BodyHandStateService.requireFreshQuery(actor);
                return BodyBlockUseResult.Outcome.HAND_CHANGED;
            }
            return primed == BodyGrenadePrime.Result.RECOVERY_REQUIRED
                    ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
        }
        // The specialized adapters are dispatched only after the authenticated body-hand admission.
        // Mutation is intentionally delegated to body-safe item cores, never a fabricated UseOnContext.
        if (stack.getItem() instanceof CableSpoolItem || stack.getItem() instanceof CableCutterItem
                || stack.getItem() instanceof CrowbarItem || stack.getItem() instanceof SteelItem
                || stack.getItem() instanceof StationFloorTileItem || stack.getItem() instanceof StationFloorTilePryItem
                || stack.is(ModItems.PLASTIC) || stack.is(ModItems.SCREWDRIVER) || stack.is(ModItems.WELDER)
                || stack.is(ModItems.WRENCH))
            return BodyConstructionItemUse.use(new BodyUseItemContext(actor, authority, hands, request.handId(),
                    request.expectedToken(), request.expectedRevision(), stack, admission.hit().pos(), admission.hit().face()));
        if (BodyBlockItemPlacement.supports(stack)) return BodyBlockItemPlacement.place(actor, request, admission);
        if (stack.getItem() instanceof AtmosphereAnalyzerItem) {
            AtmosphereAnalyzerItem.reportForBody(level, actor, body, admission.hit().pos(), admission.hit().face());
            return BodyBlockUseResult.Outcome.NO_CHANGE;
        }
        return BodyBlockUseResult.Outcome.DENIED;
    }

    public static BodyBlockUseResult.Outcome activate(ServerPlayer actor, BodyBlockUseRequest request,
                                                       BodyHandRequestService.BlockUseAdmission admission) {
        if (actor != null && request != null && admission != null
                && request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD) {
            if (com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmMenuService
                    .open(actor, request, admission)) return BodyBlockUseResult.Outcome.NO_CHANGE;
            return com.juicyslew.moonstation14.ms14.power.ui.ApcMenuService.openForBody(actor, request, admission)
                    ? BodyBlockUseResult.Outcome.NO_CHANGE : BodyBlockUseResult.Outcome.DENIED;
        }
        return BodyBlockUseResult.Outcome.DENIED;
    }

    static boolean expectedTokenMatches(String expectedToken, java.util.Optional<String> actualToken) {
        return expectedToken != null && actualToken.filter(expectedToken::equals).isPresent();
    }
}
