package com.juicyslew.moonstation14.ms14.combat;

import java.util.Objects;
import java.util.UUID;

/** Pure client-side held-trigger state; request cadence/pending remains in SourceRequestTracker. */
public final class GunTriggerClientPolicy {
    public record Context(UUID body, long epoch, String dimension, String hand, String token, String mode) {
        public Context {
            if (body == null || epoch <= 0 || dimension == null || hand == null || token == null
                    || !("SEMI_AUTO".equals(mode) || "FULL_AUTO".equals(mode)))
                throw new IllegalArgumentException("invalid trigger context");
        }
    }
    public record Shot(long id, boolean start) { }
    public record Stop(long epoch, long id, String hand, String token) { }
    private UUID counterBody;
    private long counterEpoch, counter;
    private Context context;
    private long id;
    private boolean held, firstSubmitted;

    public long begin(Context next) {
        if (next == null) throw new IllegalArgumentException("context");
        if (!Objects.equals(counterBody, next.body()) || counterEpoch != next.epoch()) {
            counterBody = next.body(); counterEpoch = next.epoch(); counter = 0;
        }
        end();
        context = next; id = ++counter; held = true; firstSubmitted = false;
        return id;
    }
    /** Return a nomination only when the caller has independently established cadence and no pending shot. */
    public Shot nextShot(Context current, boolean pending, boolean cadenceReady) {
        if (!held || !Objects.equals(context, current)) { end(); return null; }
        if (pending || !cadenceReady || (!"FULL_AUTO".equals(context.mode()) && firstSubmitted)) return null;
        return new Shot(id, !firstSubmitted);
    }
    public boolean submitted(long triggerId) {
        if (!held || id != triggerId || firstSubmitted) return false;
        firstSubmitted = true; return true;
    }
    public Stop release() { return end(); }
    public Stop releaseIfMatches(long triggerId) { return held && id == triggerId ? end() : null; }
    public Stop contextLost(Context current) { return Objects.equals(context, current) ? null : end(); }
    public Stop deniedOrTimedOut() { return end(); }
    private Stop end() {
        Stop stop = held ? new Stop(context.epoch(), id, context.hand(), context.token()) : null;
        held = false; firstSubmitted = false; context = null; id = 0;
        return stop;
    }
    public boolean held() { return held; }
}
