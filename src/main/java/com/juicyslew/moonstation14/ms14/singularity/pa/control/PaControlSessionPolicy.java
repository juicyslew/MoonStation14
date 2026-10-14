package com.juicyslew.moonstation14.ms14.singularity.pa.control;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.UUID;

/** Pure policy for matching a live PA session and consuming request IDs. Server code supplies facts. */
final class PaControlSessionPolicy {
    private PaControlSessionPolicy() { }

    record Pin(UUID id, Object player, UUID machine, BlockPos position, UUID body, long epoch,
               ResourceLocation dimension, ResourceLocation profile) {
        Pin {
            Objects.requireNonNull(id); Objects.requireNonNull(player); Objects.requireNonNull(machine);
            position = Objects.requireNonNull(position).immutable(); Objects.requireNonNull(body);
            Objects.requireNonNull(dimension); Objects.requireNonNull(profile);
        }
    }

    record Facts(Object player, UUID session, UUID body, long epoch, ResourceLocation dimension,
                 UUID machine, BlockPos position, ResourceLocation profile,
                 boolean actorValid, boolean targetValid, boolean reachable, boolean powered) { }

    enum Match { INVALID, VALID }
    enum ActionDecision { REPLAY, STALE_REVISION, ACCEPTED }
    record ActionAdmission(long lastRequestId, ActionDecision decision) { }

    static Match match(Pin pin, Facts facts) {
        if (pin == null || facts == null || pin.player() != facts.player()
                || !pin.id().equals(facts.session()) || !pin.body().equals(facts.body())
                || pin.epoch() != facts.epoch() || !pin.dimension().equals(facts.dimension())
                || !pin.machine().equals(facts.machine()) || !pin.position().equals(facts.position())
                || !pin.profile().equals(facts.profile()) || !facts.actorValid()
                || !facts.targetValid() || !facts.reachable() || !facts.powered())
            return Match.INVALID;
        return Match.VALID;
    }

    static ActionAdmission actionAdmission(long lastRequestId, long incomingId,
                                           long expectedRevision, long currentRevision) {
        if (incomingId <= lastRequestId)
            return new ActionAdmission(lastRequestId, ActionDecision.REPLAY);
        return new ActionAdmission(incomingId, expectedRevision == currentRevision
                ? ActionDecision.ACCEPTED : ActionDecision.STALE_REVISION);
    }
}
