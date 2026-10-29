package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionHandler;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Authoritative, read-only query of the actions composed on a controlled body. */
public final class BodyActionService {
    private BodyActionService() { }

    public static Optional<BodyActionSnapshot> query(ServerPlayer player) {
        var authority = BodyActionAuthority.resolve(player).orElse(null);
        if (authority == null) return Optional.empty();
        return query(player, authority);
    }

    private static Optional<BodyActionSnapshot> query(ServerPlayer player, BodyActionAuthority.Snapshot authority) {
        var body = authority.body();
        var identity = authority.identity();
        var runtime = body.getData(ModDataAttachments.ACTION_RUNTIME.get());
        var character = identity.prototype();
        if (runtime == null || character == null) return Optional.empty();

        long now = body.level().getGameTime();
         List<BodyActionSnapshot.Entry> entries = BodyActionDiscovery.discover(body, identity.bodyUuid(), character).stream()
                .map(grant -> {
                    ActionPrototype profile = grant.profile();
                     String providerId = grant.providerKey();
                    var admission = BodyActionAdmission.evaluate(profile, body);
                    long cooldownEnd = runtime.cooldownEnd(grant.actionId().toString(), providerId);
                    boolean enabled = admission.enabled() && now >= cooldownEnd;
                    boolean active = profile.kind() == ActionPrototype.Kind.TOGGLE
                            && profile.handler() == ActionHandler.COMBAT_TOGGLE
                            && CombatModeSystem.isInCombatMode(body);
                    return new BodyActionSnapshot.Entry(grant.actionId(), providerId, profile.displayName(),
                            profile.icon(), profile.iconOn(), profile.kind(), enabled, active, cooldownEnd,
                            profile.useDelayTicks(), enabled ? "" : admission.enabled() ? "COOLDOWN" : admission.reason().name(),
                            profile.priority());
                })
                 .sorted(Comparator.comparingInt(BodyActionSnapshot.Entry::priority)
                        .thenComparing(entry -> entry.actionId().toString()))
                .toList();

         long revision = runtime.updateSnapshotFingerprint(List.of(identity.bodyUuid(), identity.epoch(), entries,
                 com.juicyslew.moonstation14.ms14.hands.live.LiveHands.equipmentSnapshot(body).map(s -> s.slots().stream().map(x -> List.of(x.id(), x.token().orElse(""))).toList()).orElse(List.of())));
        if (!BodyActionAuthority.revalidate(player, authority)) return Optional.empty();
        return Optional.of(new BodyActionSnapshot(identity.bodyUuid(), identity.epoch(), revision, now, entries));
    }

    /** Executes one authenticated action intent; client profile data is never trusted. */
    public static BodyActionResult activate(ServerPlayer player, BodyActionRequest request) {
        long sequence = request == null ? 1 : request.sequence();
        BodyActionAuthority.Snapshot authority = BodyActionAuthority.resolve(player).orElse(null);
        if (authority == null) return denied(sequence, "NO_AUTHORITY", Optional.empty());
         BodyActionSnapshot snapshot = query(player, authority).orElse(null);
         if (snapshot == null) return denied(sequence, "NO_SNAPSHOT", Optional.empty());
         if (!snapshot.bodyId().equals(authority.identity().bodyUuid())
                 || snapshot.bodyEpoch() != authority.identity().epoch())
             return denied(sequence, "NO_AUTHORITY", Optional.empty());
        if (request == null || !request.bodyId().equals(snapshot.bodyId())
                || request.bodyEpoch() != snapshot.bodyEpoch() || request.revision() != snapshot.revision())
            return denied(sequence, "STALE_SNAPSHOT", Optional.of(snapshot));
        var entry = snapshot.actions().stream().filter(e -> e.actionId().equals(request.actionId())
                && e.providerId().equals(request.providerId())).findFirst().orElse(null);
        if (entry == null) return denied(sequence, "UNAVAILABLE", Optional.of(snapshot));
        if (!entry.enabled()) return denied(sequence, entry.disabledReason(), Optional.of(snapshot));

        var body = authority.body();
        var runtime = body.getData(ModDataAttachments.ACTION_RUNTIME.get());
        UUID controller = player.getUUID();
         if (runtime == null || !runtime.isRequestSequenceNew(controller, snapshot.bodyEpoch(), request.sequence()))
            return denied(sequence, "INVALID_SEQUENCE", Optional.of(snapshot));
         var grant = BodyActionDiscovery.discover(body, authority.identity().bodyUuid(), authority.identity().prototype()).stream()
                .filter(g -> g.actionId().equals(request.actionId())
                         && g.providerKey().equals(request.providerId())).findFirst().orElse(null);
        if (grant == null) return denied(sequence, "UNAVAILABLE", Optional.of(snapshot));
        if (!BodyActionAdmission.evaluate(grant.profile(), body).enabled())
            return denied(sequence, "NOT_ADMITTED", query(player));
        long now = body.level().getGameTime();
        if (runtime.isOnCooldown(request.actionId().toString(), request.providerId(), now))
            return denied(sequence, "COOLDOWN", query(player));
         var effect = BodyActionEffects.prepare(player, body, grant.profile());
         if (effect.isEmpty()) return denied(sequence, "UNSUPPORTED", query(player));
         if (!BodyActionAuthority.revalidate(player, authority)) return denied(sequence, "NO_AUTHORITY", query(player));
         // Accepted dispatches are at-most-once even if applying the prepared effect fails; this is not a cooldown.
         if (!runtime.acceptRequestSequence(controller, snapshot.bodyEpoch(), request.sequence()))
             return denied(sequence, "INVALID_SEQUENCE", Optional.of(snapshot));
         if (!effect.orElseThrow().apply()) return denied(sequence, "EFFECT_FAILED", query(player));
        runtime.setCooldownEnd(request.actionId().toString(), request.providerId(), now + grant.profile().useDelayTicks());
        return new BodyActionResult(BodyActionResult.Status.ACCEPTED, "", sequence, query(player));
    }

    private static BodyActionResult denied(long sequence, String reason, Optional<BodyActionSnapshot> snapshot) {
        return new BodyActionResult(BodyActionResult.Status.DENIED, reason, sequence, snapshot);
    }
}
