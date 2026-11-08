package com.juicyslew.moonstation14.ms14.combat;

import java.util.UUID;

/** Small value-only correlation state shared by client request policies. */
public final class SourceRequestTracker {
    public record SourceKey(String kind, String token) { }
    private UUID body;
    private long epoch, sequence, sentAt, projectedDeadline;
    private boolean pending;
    private SourceKey source;

    public void bind(UUID body, long epoch) {
        if (body == null || epoch <= 0) { clear(); return; }
        if (!body.equals(this.body) || epoch != this.epoch) {
            this.body=body; this.epoch=epoch; pending=false; source=null; projectedDeadline=0;
        }
    }
    public Long begin(SourceKey key, long now, long duration) {
        if (body==null || pending || key==null || duration<0 || sequence==Long.MAX_VALUE) return null;
        if (source != null && source.equals(key) && now < projectedDeadline) return null;
        source=key; sequence++; sentAt=now; projectedDeadline=saturatingAdd(now,duration); pending=true;
        return sequence;
    }
    public boolean acknowledge(UUID body, long epoch, long sequence, long now, long remaining) {
        if (body==null || !pending || !this.body.equals(body) || this.epoch!=epoch || this.sequence!=sequence) return false;
        pending=false; projectedDeadline=saturatingAdd(now,Math.max(0,remaining)); return true;
    }
    public boolean timeout(long now,long ticks) { if(!pending || now-sentAt<ticks)return false; pending=false; return true; }
    public void clear() { body=null; epoch=0; pending=false; source=null; projectedDeadline=0; }
    private static long saturatingAdd(long a,long b) { return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b; }
    public UUID body(){return body;} public long epoch(){return epoch;} public long sequence(){return sequence;}
    public boolean pending(){return pending;} public SourceKey source(){return source;} public long sentAt(){return sentAt;}
    public long projectedDeadline(){return projectedDeadline;}
}
