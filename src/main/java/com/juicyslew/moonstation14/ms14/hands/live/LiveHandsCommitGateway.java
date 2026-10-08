package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Server-thread commit point for live hands. Scoped batches publish only their final committed value. */
public final class LiveHandsCommitGateway {
    private static final ThreadLocal<java.util.IdentityHashMap<LivingEntity, Pending>> BATCH = new ThreadLocal<>();
    private record Pending(ServerPlayer actor, HandActorAuthority.Snapshot authority) { }
    private LiveHandsCommitGateway() { }

    public static boolean commit(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands value) {
        if (actor == null || authority == null || value == null || authority.actor() != actor
                || !HandActorAuthority.revalidate(actor, authority)) return false;
        LivingEntity body = authority.body();
        if (body.level().isClientSide || body.level().getServer() == null || !body.level().getServer().isSameThread()
                || !value.compatible(body) || !BodyHandStateService.representable(value, body)) return false;
        body.setData(ModDataAttachments.LIVE_HANDS.get(), value);
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != value
                || !HandActorAuthority.revalidate(actor, authority)) return false;
        deferOrPublish(actor, authority);
        return true;
    }

    /** Resolve the authenticated current owner for legacy owner-only mutation paths. */
    public static boolean commitOwned(LivingEntity body, LiveHands value) {
        LiveHands expected = body == null ? null : body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return commitOwned(body, expected, value);
    }

    /** Compare-and-commit trusted path for staged transfer and compensation callers. */
    public static boolean commitOwned(LivingEntity body, LiveHands expected, LiveHands value) {
        if (!commitTrusted(body, expected, value))
            throw new IllegalStateException("Live hand commit rejected");
        return true;
    }

    /** Trusted server-system path; lack of a player owner never prevents a valid body mutation. */
    public static boolean commitTrusted(LivingEntity body, LiveHands expected, LiveHands value) {
        if (body == null || value == null || body.level().isClientSide || body.level().getServer() == null
                || !body.level().getServer().isSameThread() || expected != null
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != expected
                || !value.compatible(body) || !BodyHandStateService.representable(value, body)) return false;
        try { body.setData(ModDataAttachments.LIVE_HANDS.get(), value); }
        catch (RuntimeException failure) { return false; }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != value) return false;
        var server = body.level().getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var authority = HandActorAuthority.resolve(player).orElse(null);
            if (authority != null && authority.body() == body) { deferOrPublish(player, authority); break; }
        }
        return true;
    }

    private static void deferOrPublish(ServerPlayer actor, HandActorAuthority.Snapshot authority) {
        var batch = BATCH.get();
        if (batch == null) BodyHandStateService.publishCommitted(actor, authority);
        else batch.put(authority.body(), new Pending(actor, authority));
    }

    /** Run a short synchronous mutation batch; callers invoke commit once with the final state. */
    public static <T> T batch(java.util.function.Supplier<T> mutation) {
        if (BATCH.get() != null) return mutation.get();
        var pending = new java.util.IdentityHashMap<LivingEntity, Pending>();
        BATCH.set(pending);
        try { return mutation.get(); }
        finally {
            BATCH.remove();
            for (var value : pending.values()) BodyHandStateService.publishCommitted(value.actor(), value.authority());
        }
    }
}
