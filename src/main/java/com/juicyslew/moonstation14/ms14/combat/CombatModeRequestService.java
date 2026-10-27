package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionSequenceGate;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.Map;
import java.util.WeakHashMap;

/** Main-thread authenticated combat-mode request path. */
public final class CombatModeRequestService {
    private static final Map<ServerPlayer, BodyActionSequenceGate> GATES = new WeakHashMap<>();
    private CombatModeRequestService() { }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) GATES.remove(p); }
    public static CombatModeRequestResult handle(ServerPlayer player, CombatModeRequest request) {
        if (request == null) return result(CombatModeRequestResult.Status.NO_AUTHORITY, null, 0, 0, false);
        var authority = BodyActionAuthority.resolve(player).orElse(null);
        if (authority == null) return result(CombatModeRequestResult.Status.NO_AUTHORITY, null, request.epoch(), request.sequence(), false);
        var gate = GATES.computeIfAbsent(player, ignored -> new BodyActionSequenceGate());
        var admitted = gate.admit(authority.identity().epoch(), request.epoch(), request.sequence(), player.level().getGameTime());
        if (admitted != BodyActionSequenceGate.Result.ADMITTED) {
            var status = switch (admitted) { case WRONG_EPOCH -> CombatModeRequestResult.Status.WRONG_EPOCH; case REPLAY -> CombatModeRequestResult.Status.REPLAY; case RATE_LIMITED -> CombatModeRequestResult.Status.RATE_LIMITED; default -> throw new IllegalStateException(); };
            return result(status, authority.body().getUUID(), authority.identity().epoch(), request.sequence(), CombatModeSystem.isInCombatMode(authority.body()));
        }
        if (CombatModeSystem.capability(authority.body()).isEmpty()) return result(CombatModeRequestResult.Status.NO_CAPABILITY, authority.body().getUUID(), authority.identity().epoch(), request.sequence(), CombatModeSystem.isInCombatMode(authority.body()));
        if (!BodyActionAuthority.revalidate(player, authority)) return result(CombatModeRequestResult.Status.NO_AUTHORITY, authority.body().getUUID(), authority.identity().epoch(), request.sequence(), CombatModeSystem.isInCombatMode(authority.body()));
        boolean changed = CombatModeSystem.setMode(authority.body(), request.desiredMode());
        return result(changed ? CombatModeRequestResult.Status.ACCEPTED : CombatModeRequestResult.Status.SET_FAILED,
                authority.body().getUUID(), authority.identity().epoch(), request.sequence(), CombatModeSystem.isInCombatMode(authority.body()));
    }
    private static CombatModeRequestResult result(CombatModeRequestResult.Status s, java.util.UUID id, long epoch, long sequence, boolean actual) { return new CombatModeRequestResult(s,id,epoch,sequence,actual); }
}
