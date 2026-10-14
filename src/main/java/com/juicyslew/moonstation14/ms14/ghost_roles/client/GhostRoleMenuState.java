package com.juicyslew.moonstation14.ms14.ghost_roles.client;

import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import java.util.List;
import java.util.UUID;

/** Small deterministic client state machine; snapshots/results are accepted only for the active context. */
public final class GhostRoleMenuState {
    public static final long REFRESH_TIMEOUT_TICKS = 100;
    private GhostRolePayloads.Context context;
    private List<GhostRolePayloads.Entry> entries = List.of();
    private UUID pending;
    private String status = "";
    private boolean refreshing;
    private long refreshStarted;
    public void open(GhostRolePayloads.Context value) { open(value,0); }
    public void open(GhostRolePayloads.Context value,long tick) {
        context=value; entries=List.of(); pending=null; status=""; refreshing=true; refreshStarted=tick;
    }
    public boolean snapshot(GhostRolePayloads.Snapshot value) {
        if (context == null || !context.equals(value.context())) return false;
        entries=value.entries(); refreshing=false; status=""; return true;
    }
    public UUID beginRequest() {
        if (context==null || pending!=null) return null;
        pending=UUID.randomUUID(); return pending;
    }
    public boolean result(GhostRolePayloads.Result value) {
        if (context==null || !context.equals(value.context()) || pending==null || !pending.equals(value.correlation())) return false;
        pending=null; status=value.message(); return true;
    }
    public void refresh(long tick) { if(!refreshing){refreshing=true;refreshStarted=tick;} }
    public void tick(long tick) {
        if(refreshing && tick-refreshStarted>=REFRESH_TIMEOUT_TICKS){refreshing=false;status="No response; retrying…";}
    }
    public void close() { context=null; entries=List.of(); pending=null; status=""; refreshing=false; }
    public boolean activeFor(GhostRolePayloads.Context value) { return context!=null && context.equals(value); }
    public GhostRolePayloads.Context context() { return context; }
    public List<GhostRolePayloads.Entry> entries() { return entries; }
    public UUID pending() { return pending; }
    public String status() { return status; }
    public boolean refreshing() { return refreshing; }
}
