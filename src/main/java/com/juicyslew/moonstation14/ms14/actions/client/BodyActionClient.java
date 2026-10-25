package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionResult;
import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import com.juicyslew.moonstation14.ms14.actions.network.BodyActionNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Optional;

/** Client presentation state and authoritative action requests. */
public final class BodyActionClient {
    private static final BodyActionClientState STATE = new BodyActionClientState();
    private static boolean queryRequested;
    private static long sequence;

    private BodyActionClient() { }

    public static void onSnapshot(Optional<BodyActionSnapshot> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            STATE.worldChanged();
            queryRequested = false;
            return;
        }
        STATE.accept(snapshot.get(), currentGameTime());
    }

    public static void onResult(BodyActionResult result) { STATE.accept(result, currentGameTime()); }
    private static long currentGameTime() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime(); }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Mob actor = GhostControlClient.ownedCharacterForHud();
        long epoch = GhostControlClient.committedCharacterEpoch();
        if (mc.level == null || actor == null || epoch <= 0 || actor.level() != mc.level) {
            STATE.worldChanged(); queryRequested = false;
            return;
        }
        var before = STATE.snapshot();
        boolean same = before != null && before.bodyId().equals(actor.getUUID()) && before.bodyEpoch() == epoch;
        STATE.control(actor.getUUID(), epoch);
        if (!same) queryRequested = false;
        if (!queryRequested) { BodyActionNetworking.requestQuery(); queryRequested = true; }
    }

    public static BodyActionSnapshot snapshot() { return STATE.snapshot(); }
    public static long remainingCooldown(BodyActionSnapshot.Entry entry) {
        return STATE.remainingCooldown(entry, currentGameTime());
    }
    public static boolean pending() { return STATE.pending(); }
    public static String feedback() { return STATE.feedback(); }

    public static boolean activate(BodyActionSnapshot.Entry entry) {
        BodyActionSnapshot snapshot = STATE.snapshot();
        if (entry == null || snapshot == null || STATE.pending() || !entry.enabled()
                || remainingCooldown(entry) > 0 || sequence == Long.MAX_VALUE) return false;
        long next = ++sequence;
        long begun = STATE.begin(entry.actionId(), entry.providerId());
        if (begun == 0) return false;
        BodyActionNetworking.requestAction(new BodyActionRequest(snapshot.bodyId(), snapshot.bodyEpoch(), snapshot.revision(),
                entry.actionId(), entry.providerId(), next));
        return true;
    }
}
