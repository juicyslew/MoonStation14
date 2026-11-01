package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import java.util.*;

/** Bounded, transient CAS application ownership for one controller generation. */
public final class AirAlarmPresetApplication {
    public record Member(UUID id, String address) { public Member { Objects.requireNonNull(id); Objects.requireNonNull(address); } }
    public enum Result { IGNORED, RETRY_NEWER_STATE, COMPLETE, REJECTED }
    private static final class Work { long request; long expected; boolean flight; boolean retried; }
    private final LinkedHashMap<Member, Work> pending = new LinkedHashMap<>();
    private long generation;
    private long sequence;

    public long begin(Collection<Member> members) {
        generation++; pending.clear();
        for (Member member : members) if (pending.size() < 32) pending.put(member, new Work());
        return generation;
    }
    public void request(Member member) { if (pending.size() < 32) pending.computeIfAbsent(member, ignored -> new Work()); }
    public boolean pending(Member member) { return pending.containsKey(member); }
    /** Claim exactly one command until its correlated result arrives. */
    public long claim(Member member, long expectedRevision) {
        Work work = pending.get(member);
        if (work == null || work.flight) return 0;
        work.flight = true; work.expected = expectedRevision; work.request = ++sequence;
        return work.request;
    }
    public Result result(Member member, long requestId, boolean applied, long currentRevision) {
        Work work = pending.get(member);
        if (work == null || !work.flight || work.request != requestId) return Result.IGNORED;
        work.flight = false;
        if (applied) { pending.remove(member); return Result.COMPLETE; }
        if (!work.retried && currentRevision > work.expected) {
            work.retried = true; return Result.RETRY_NEWER_STATE;
        }
        pending.remove(member); return Result.REJECTED;
    }
    public void cancel(Member member) { pending.remove(member); }
    public void cancel(UUID id) { pending.keySet().removeIf(member -> member.id().equals(id)); }
    public long generation() { return generation; }
    public int pendingCount() { return pending.size(); }
}
