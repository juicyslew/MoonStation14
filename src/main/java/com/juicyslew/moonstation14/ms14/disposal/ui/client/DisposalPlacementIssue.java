package com.juicyslew.moonstation14.ms14.disposal.ui.client;

import java.util.UUID;

/** Bounded local-preview feedback; deliberately does not claim a server rejection. */
public final class DisposalPlacementIssue {
    private String reason;
    private long expiresAt;
    private UUID body;
    private long epoch;
    public void invalid(String reason, long tick, UUID body, long epoch) {
        if (reason == null || reason.isBlank()) return;
        this.reason=reason; this.expiresAt=tick+80; this.body=body; this.epoch=epoch;
    }
    public String visible(long tick, UUID currentBody, long currentEpoch) {
        if (reason == null) return null;
        if (!java.util.Objects.equals(body,currentBody) || epoch!=currentEpoch || tick>=expiresAt) { clear(); return null; }
        return reason;
    }
    public void clear() { reason=null; body=null; epoch=0; expiresAt=0; }
}

