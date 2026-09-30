package com.juicyslew.moonstation14.ms14.power.ui.body;

import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure comparison of a pinned body/APC session with server-established facts. This policy cannot
 * authenticate a connection or packet, open a session, mutate a world, or prove the supplied reach
 * fact. The caller must establish all facts on the server and retain packet and mutation authority.
 * Revision and desired-state decisions belong to ApcBreakerIntentPolicy.
 */
public final class ApcBodySessionPolicy {
    private ApcBodySessionPolicy() { }

    public static final long MAX_LIFETIME_TICKS = 200;

    public enum Decision { DENY, MATCH }

    /** Opaque references are pinned by identity; the caller must supply actual server objects. */
    public record Session(Object player, BodyActionPolicy.Identity body, Object level,
                          ResourceKey<Level> dimension, BlockPos target, Object apc,
                          UUID token, long openedTick, long expiryTick) {
        public Session {
            Objects.requireNonNull(player);
            Objects.requireNonNull(body);
            Objects.requireNonNull(level);
            Objects.requireNonNull(dimension);
            target = Objects.requireNonNull(target).immutable();
            Objects.requireNonNull(apc);
            Objects.requireNonNull(token);
        }
    }

    /** All flags are live, server-established observations, never claims from a request. */
    public record Facts(Object connectedPlayer, boolean exactConnected, BodyActionPolicy.Identity body,
                        Object level, ResourceKey<Level> dimension, BlockPos target, Object apc,
                        boolean targetLoaded, boolean targetNotRemoved, boolean targetIsApc,
                        boolean bodyUsable, boolean stunned, boolean geometryInBounds,
                        UUID token, long epoch, long currentTick) { }

    public static Decision decide(Session session, Facts facts) {
        if (session == null || facts == null || session.openedTick() < 0
                || session.expiryTick() < session.openedTick()
                || session.expiryTick() - session.openedTick() > MAX_LIFETIME_TICKS
                || facts.currentTick() < session.openedTick()
                || facts.currentTick() > session.expiryTick())
            return Decision.DENY;
        if (session.player() != facts.connectedPlayer() || !facts.exactConnected()
                || session.body().player() != session.player()
                || !BodyActionPolicy.same(session.body(), facts.body())
                || !BodyActionPolicy.eligibleForComplexInteraction(facts.body(), facts.stunned())
                || !facts.bodyUsable() || !facts.geometryInBounds()
                || session.body().epoch() != facts.epoch()
                || !session.token().equals(facts.token())
                || session.level() != facts.level()
                || !session.dimension().equals(facts.dimension())
                || !session.target().equals(facts.target())
                || session.apc() != facts.apc()
                || !facts.targetLoaded() || !facts.targetNotRemoved() || !facts.targetIsApc())
            return Decision.DENY;
        return Decision.MATCH;
    }
}
