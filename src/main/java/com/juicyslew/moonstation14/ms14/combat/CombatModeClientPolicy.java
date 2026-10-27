package com.juicyslew.moonstation14.ms14.combat;

import java.util.UUID;

/** Pure client-side prediction and acknowledgement correlation; never grants authority. */
public final class CombatModeClientPolicy {
    private UUID body;
    private long epoch, sequence;
    private boolean authoritative, predicted, pending;
    private long sentAt;

    public void bind(UUID body, long epoch, boolean replicatedMode) {
        if (body == null || epoch <= 0) { clear(); return; }
        if (!body.equals(this.body) || epoch != this.epoch) {
            this.body = body; this.epoch = epoch; pending = false;
        }
        authoritative = replicatedMode;
        if (!pending) predicted = replicatedMode;
    }

    public CombatModeRequest toggle(long now) {
        if (body == null || pending || sequence == Long.MAX_VALUE) return null;
        sequence++;
        predicted = !predicted;
        pending = true; sentAt = now;
        return new CombatModeRequest(epoch, sequence, predicted);
    }

    public boolean acknowledge(CombatModeRequestResult result) {
        if (result == null || !pending || body == null || result.bodyUUID() == null
                || !body.equals(result.bodyUUID()) || epoch != result.epoch() || sequence != result.sequence()) return false;
        authoritative = result.actualMode(); predicted = authoritative; pending = false;
        return true;
    }

    public void attachmentUpdate(UUID body, long epoch, boolean mode) { bind(body, epoch, mode); }
    public boolean timeout(long now, long timeoutTicks) {
        if (!pending || now - sentAt < timeoutTicks) return false;
        pending = false; predicted = authoritative; return true;
    }
    /** Drop authority/prediction but keep sequence monotonic so a late ACK cannot alias a later context. */
    public void clear() { body = null; epoch = 0; pending = false; authoritative = predicted = false; }
    public UUID body() { return body; }
    public long epoch() { return epoch; }
    public long sequence() { return sequence; }
    public boolean mode() { return predicted; }
    public boolean pending() { return pending; }
}
