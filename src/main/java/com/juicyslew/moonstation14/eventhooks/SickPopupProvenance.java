package com.juicyslew.moonstation14.eventhooks;

import com.juicyslew.moonstation14.ms14.metabolism.MetabolismEffectInvocation;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolizerProfile;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismSystem;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, rate-limited provenance for the unexpected metabolism sickness popup. */
final class SickPopupProvenance {
    private static final long INTERVAL_NANOS = 10_000_000_000L;
    private static final int MAX_PLAYERS = 64;
    private static final Limiter LIMITER = new Limiter(MAX_PLAYERS, INTERVAL_NANOS);

    private SickPopupProvenance() {}

    static boolean shouldTrace(EffectResult result, boolean serverPlayer, EffectData effect) {
        return result == EffectResult.APPLIED && serverPlayer
                && effect instanceof EffectData.PopupMessage popup
                && popup.subType() == EffectData.PopupRecipients.Local
                && popup.messages().contains("generic-reagent-effect-sick");
    }

    static void log(ServerPlayer player, MetabolismEffectInvocation invocation,
                    MetabolizerProfile profile) {
        long now = System.nanoTime();
        UUID id = player.getUUID();
        if (!LIMITER.allow(id, now)) return;
        var snapshot = invocation.sourceSnapshot();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "Metabolism sickness popup provenance: player='{}' uuid={} source={} stage={} profile={} sourceCompartment={} processed={} sourceAmountBefore={} sourceUncookedProtein={} sourceBlood={} creative={}",
                player.getName().getString(), id, invocation.reagent().location(), invocation.stage(), profile,
                profile == MetabolizerProfile.STOMACH ? "stomach" : "body",
                invocation.result().actualRemoved(), snapshot.amount(), amount(snapshot, "uncookedanimalproteins"),
                amount(snapshot, "blood"),
                player.isCreative());
    }

    private static float amount(MetabolismSystem.SourceSnapshot snapshot, String path) {
        return snapshot.quantities().entrySet().stream()
                .filter(entry -> entry.getKey().location().getPath().equals(path))
                .map(Map.Entry::getValue).findFirst().orElse(0f);
    }

    static final class Limiter {
        private final int capacity;
        private final long interval;
        private final Map<UUID, Long> last = new LinkedHashMap<>();

        Limiter(int capacity, long interval) {
            this.capacity = capacity;
            this.interval = interval;
        }

        synchronized boolean allow(UUID id, long now) {
            Long previous = last.get(id);
            if (previous != null && now >= previous && now - previous < interval) return false;
            last.remove(id);
            if (last.size() >= capacity) last.remove(last.keySet().iterator().next());
            last.put(id, now);
            return true;
        }

        synchronized int size() { return last.size(); }
    }
}

