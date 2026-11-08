package com.juicyslew.moonstation14.ms14.radiation.control;

import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.radiation.ModRadiationCollectors;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorInteractionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Authenticated context-verb handling for radiation collectors. */
public final class RadiationCollectorControlService {
    private RadiationCollectorControlService() { }

    public static boolean correlated(RadiationCollectorControlRequest request) {
        return request != null && request.requestSequence() == request.proof().sequence()
                && request.proof().intent() == BodyBlockUseRequest.Intent.USE;
    }

    public static boolean expectationMatches(RadiationCollectorControlRequest.EjectExpectation expected,
            java.util.UUID placement, net.minecraft.resources.ResourceLocation profile, long slot, long ledger) {
        return expected != null && expected.placementId().equals(placement) && expected.profileId().equals(profile)
                && expected.slotRevision() == slot && expected.ledgerRevision() == ledger;
    }

    public static Optional<RadiationCollectorControlSnapshot> handle(ServerPlayer actor, RadiationCollectorControlRequest request) {
        if (!correlated(request)) return Optional.empty();
        var admission = BodyHandRequestService.admitBlockUse(actor, request.proof());
        if (admission == null || !(admission.authority().body().level() instanceof ServerLevel level)) return Optional.empty();
        var pos = admission.hit().pos();
        if (!(level.getBlockEntity(pos) instanceof RadiationCollectorBlockEntity collector)
                || collector.isRemoved() || collector.getLevel() != level || !collector.getBlockPos().equals(pos)
                || !collector.controlStateReady() || ModRadiationCollectors.catalog(level).get(collector.profileId()) == null)
            return Optional.empty();
        var slot = collector.tankSlot().snapshot();
        var ledger = LevelItemLocationLedgers.forLevel(level).snapshot().revision();
        boolean accepted = false;
        if (request.operation() == RadiationCollectorControlRequest.Operation.QUERY) accepted = true;
        else if (expectationMatches(request.ejectExpectation(), collector.identity(), collector.profileId(), slot.revision(), ledger)) {
            var outcome = RadiationCollectorInteractionService.eject(admission, collector, collector.identity(), slot.revision(), ledger);
            accepted = outcome == BodyBlockUseResult.Outcome.HAND_CHANGED;
        }
        slot = collector.tankSlot().snapshot();
        ledger = LevelItemLocationLedgers.forLevel(level).snapshot().revision();
        var authority = admission.authority();
        return Optional.of(new RadiationCollectorControlSnapshot(request.requestSequence(), authority.body().getUUID(),
                authority.epoch(), level.dimension().location(), pos, collector.identity(), collector.profileId(), slot.revision(),
                ledger, slot.token() != null, accepted));
    }
}
