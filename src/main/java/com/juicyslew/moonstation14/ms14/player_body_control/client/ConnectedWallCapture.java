package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.CharacterCadenceDiagnostics;
import com.juicyslew.moonstation14.ms14.player_body_control.ConnectedJumpCaptureBudget;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/** Facade for the legacy wall window and the opt-in, bounded connected-jump observation. */
final class ConnectedWallCapture {
    static final int MAX_TICKS = 100;
    static final int WAIT_TICKS = 200;
    enum Mode { OFF, WALL, WAITING_JUMP, JUMP }
    private final CharacterCadenceDiagnostics wallBudget = new CharacterCadenceDiagnostics();
    private final ConnectedJumpCaptureBudget jumpBudget = new ConnectedJumpCaptureBudget();
    private final ArrayDeque<Prelaunch> prelaunch = new ArrayDeque<>(2);
    record Prelaunch(long tick, String row) {}
    private long epoch, lastTick = Long.MIN_VALUE, waitStart = Long.MIN_VALUE, triggerSequence = -1;
    private int entityId = -1, ticks;
    private UUID bodyUuid;
    private UUID departureFrame;
    private UUID observationReferenceFrame;
    private Mode mode = Mode.OFF;
    private boolean fullRejectionTraceUsed;

    synchronized boolean arm(long sessionEpoch, int ownedEntityId, UUID ownedBodyUuid) {
        return arm(sessionEpoch,ownedEntityId,ownedBodyUuid,null);
    }
    synchronized boolean arm(long sessionEpoch, int ownedEntityId, UUID ownedBodyUuid, UUID observationFrame) {
        reset();
        if (!bind(sessionEpoch, ownedEntityId, ownedBodyUuid)) return false;
        observationReferenceFrame=observationFrame;
        mode = Mode.WALL; return true;
    }
    synchronized boolean armJump(long sessionEpoch, int ownedEntityId, UUID ownedBodyUuid, long tick) {
        reset();
        if (!bind(sessionEpoch, ownedEntityId, ownedBodyUuid)) return false;
        mode = Mode.WAITING_JUMP; waitStart = lastTick = tick; return true;
    }
    private boolean bind(long e, int id, UUID uuid) {
        if (e <= 0 || id < 0 || uuid == null) return false;
        epoch=e; entityId=id; bodyUuid=uuid; return true;
    }
    synchronized boolean sample(long e, int id, UUID uuid, long tick) {
        return sample(e, id, uuid, tick, System.nanoTime());
    }
    synchronized boolean sample(long e, int id, UUID uuid, long tick, long nowNanos) {
        if (mode != Mode.WALL || !matches(e,id,uuid)) { if (mode == Mode.WALL) reset(); return false; }
        if (ticks >= MAX_TICKS || tick == lastTick) return false;
        if (!wallBudget.allow(e, nowNanos, true)) { reset(); return false; }
        lastTick=tick; ticks++; if (ticks>=MAX_TICKS) mode=Mode.OFF; return true;
    }
    synchronized boolean waiting(long e, int id, UUID uuid, long tick) {
        if (mode != Mode.WAITING_JUMP) return false;
        if (!matches(e,id,uuid) || tick < waitStart || tick < lastTick || tick >= waitStart + WAIT_TICKS) { reset(); return false; }
        lastTick=tick; return true;
    }
    synchronized boolean waitingRender(long e, int id, UUID uuid, long tick, String immutableRow) {
        if (!waiting(e,id,uuid,tick) || immutableRow == null || tick <= lastRenderTick) return false;
        if (prelaunch.size() == ConnectedJumpCaptureBudget.MAX_PRELAUNCH_ROWS) prelaunch.removeFirst();
        prelaunch.addLast(new Prelaunch(tick, "prelaunchClientTick="+tick+" "+immutableRow)); lastRenderTick=tick; return true;
    }
    private long lastRenderTick = Long.MIN_VALUE;
    synchronized boolean qualify(long e, int id, UUID uuid, long tick, long seq,
                                 boolean live, boolean grounded, boolean matchingFrame,
                                 boolean motorJumpInitiated, UUID frameId) {
        if (mode != Mode.WAITING_JUMP || !waiting(e,id,uuid,tick) || !live || !grounded
                || !matchingFrame || !motorJumpInitiated) return false;
        if (!jumpBudget.begin(e,uuid,id,tick,false)) return false;
        mode=Mode.JUMP; triggerSequence=seq; departureFrame=frameId; lastTick=tick; return true;
    }
    synchronized boolean record(long e, int id, UUID uuid, long tick, boolean render) {
        if (mode != Mode.JUMP || !matches(e,id,uuid)) { if (mode == Mode.JUMP) reset(); return false; }
        lastTick=tick; return jumpBudget.sample(e,uuid,id,tick,render);
    }
    synchronized List<Prelaunch> takePrelaunchRows() {
        if (mode != Mode.JUMP) return List.of();
        var rows = new java.util.ArrayList<Prelaunch>(prelaunch.size());
        while (!prelaunch.isEmpty()) {
            Prelaunch sample=prelaunch.removeFirst();
            if (jumpBudget.prelaunchRow(epoch,bodyUuid,entityId,sample.tick())) rows.add(sample);
        }
        return List.copyOf(rows);
    }
    synchronized boolean finish() {
        if (mode != Mode.JUMP) return false;
        boolean row=jumpBudget.finishRow(epoch,bodyUuid,entityId); mode=Mode.OFF; prelaunch.clear(); return row;
    }
    private boolean matches(long e,int id,UUID uuid) { return epoch==e && entityId==id && bodyUuid!=null && bodyUuid.equals(uuid); }
    synchronized Mode mode() { return mode; }
    synchronized boolean jumpActive() { return mode==Mode.JUMP && jumpBudget.active(); }
    /** Optional geometry capture is scoped to the exact armed body and only live WALL/JUMP windows. */
    synchronized boolean observesGeometry(long e, int id, UUID uuid) {
        return matches(e, id, uuid) && (mode == Mode.WALL || (mode == Mode.JUMP && jumpBudget.active()));
    }
    /** One complete, non-empty native trace may be retained for the first eligible post-move rejection. */
    synchronized boolean takeFullRejectionTrace(long e, int id, UUID uuid, long cadenceTick, long nowNanos,
                                               boolean traceComplete, int traceRows) {
        if (fullRejectionTraceUsed || !traceComplete || traceRows <= 0
                || !matches(e, id, uuid) || !(mode == Mode.WALL || (mode == Mode.JUMP && jumpBudget.active())))
            return false;
        if (mode == Mode.JUMP && jumpBudget.remainingTicks(cadenceTick) <= 0) return false;
        if (mode == Mode.WALL && !wallBudget.allow(e, nowNanos, true)) { reset(); return false; }
        fullRejectionTraceUsed = true;
        return true;
    }
    synchronized long triggerSequence() { return triggerSequence; }
    synchronized long epoch() { return epoch; }
    synchronized int entityId() { return entityId; }
    synchronized UUID bodyUuid() { return bodyUuid; }
    synchronized int rows() { return jumpBudget.rows(); }
    synchronized UUID departureFrame() { return departureFrame; }
    synchronized UUID observationReferenceFrame(){return observationReferenceFrame;}
    synchronized int remaining() {
        return switch(mode) {
            case WALL -> Math.max(0,MAX_TICKS-ticks);
            case WAITING_JUMP -> Math.max(0,WAIT_TICKS-(int)Math.max(0,lastTick-waitStart));
            case JUMP -> jumpBudget.remainingTicks(lastTick);
            default -> 0;
        };
    }
    synchronized void stop() { reset(); }
    synchronized void reset() {
        mode=Mode.OFF; epoch=0; entityId=-1; bodyUuid=null; lastTick=waitStart=lastRenderTick=Long.MIN_VALUE;
        triggerSequence=-1; departureFrame=null; observationReferenceFrame=null; ticks=0; fullRejectionTraceUsed=false; prelaunch.clear(); wallBudget.reset(); jumpBudget.reset();
    }
}
