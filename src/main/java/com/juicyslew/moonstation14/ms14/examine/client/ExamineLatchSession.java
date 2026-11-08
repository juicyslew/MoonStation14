package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;
import java.util.UUID;

/** Small production guard separating clicked examination identity from changing visual preview. */
public final class ExamineLatchSession {
    private ExamineTarget target;
    private UUID actor;
    private long epoch, requestId;
    private boolean dismissed;

    public void begin(ExamineTarget target, UUID actor, long epoch, long requestId) {
        this.target = target; this.actor = actor; this.epoch = epoch; this.requestId = requestId; dismissed = false;
    }
    public void refreshed(long requestId) { if (active()) this.requestId = requestId; }
    public boolean accepts(ExamineTarget target, UUID actor, long epoch, long requestId) {
        return active() && this.target.equals(target) && this.actor.equals(actor) && this.epoch == epoch && this.requestId == requestId;
    }
    public ExamineTarget target() { return active() ? target : null; }
    public boolean active() { return target != null && !dismissed; }
    public void dismiss() { dismissed = true; target = null; actor = null; }
    public void clear() { target = null; actor = null; dismissed = false; }
}

