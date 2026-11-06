package com.juicyslew.moonstation14.ms14.player_body_control;

import java.util.UUID;

/** Small pure budget shared by opt-in connected-jump observations on each side. */
public final class ConnectedJumpCaptureBudget {
    public static final int WINDOW_TICKS = 16;
    public static final int MAX_ROWS = 64;
    public static final int MAX_RENDER_ROWS = 12;
    public static final int MAX_PRELAUNCH_ROWS = 2;
    public static final int MAX_SERVER_EPISODES = 2;

    private long epoch;
    private UUID body;
    private int entityId = -1;
    private int episodes;
    private int rows;
    private int renderRows;
    private int prelaunchRows;
    private long startTick = Long.MIN_VALUE;
    private long lastTick = Long.MIN_VALUE;
    private long lastRenderTick = Long.MIN_VALUE;
    private boolean active;
    private boolean endWritten;

    /** Binds/reset on identity changes; a new identity may start a fresh server episode allowance. */
    public synchronized void bind(long newEpoch, UUID newBody, int newEntityId) {
        if (newEpoch <= 0 || newBody == null || newEntityId < 0) { reset(); return; }
        if (epoch == newEpoch && entityId == newEntityId && newBody.equals(body)) return;
        reset(); epoch = newEpoch; body = newBody; entityId = newEntityId;
    }

    /** Starts one qualifying episode, with server's lifetime cap optionally enforced. */
    public synchronized boolean begin(long currentEpoch, UUID currentBody, int currentEntityId,
                                      long tick, boolean server) {
        bind(currentEpoch, currentBody, currentEntityId);
        if (epoch == 0 || active || (server && episodes >= MAX_SERVER_EPISODES)) return false;
        episodes++; rows = renderRows = prelaunchRows = 0; lastTick = lastRenderTick = Long.MIN_VALUE; endWritten = false;
        startTick = lastTick = tick; active = true;
        return addRow();
    }

    public synchronized boolean sample(long currentEpoch, UUID currentBody, int currentEntityId,
                                       long tick, boolean render) {
        if (!matches(currentEpoch, currentBody, currentEntityId)) { active = false; return false; }
        if (!active || tick < lastTick || tick < startTick || tick >= startTick + WINDOW_TICKS) {
            active = false; return false;
        }
        if (render && (tick == lastRenderTick || renderRows >= MAX_RENDER_ROWS)) return false;
        lastTick = tick;
        if (!addRow()) { active = false; return false; }
        if (render) { renderRows++; lastRenderTick = tick; }
        if (rows >= MAX_ROWS) active = false;
        return true;
    }

    /** Account for a saved prelaunch ring entry when flushing it at BEGIN (maximum two). */
    public synchronized boolean prelaunchRow(long currentEpoch, UUID currentBody, int currentEntityId, long tick) {
        if (!matches(currentEpoch, currentBody, currentEntityId) || !active
                || tick > startTick || (lastRenderTick != Long.MIN_VALUE && tick <= lastRenderTick)
                || prelaunchRows >= MAX_PRELAUNCH_ROWS || renderRows >= MAX_RENDER_ROWS || rows >= MAX_ROWS) return false;
        prelaunchRows++; renderRows++; rows++;
        lastRenderTick=tick;
        if (rows >= MAX_ROWS) active = false;
        return true;
    }
    /** Reserves one bounded final END row, including after elapsed-window expiry. */
    public synchronized boolean finishRow(long currentEpoch, UUID currentBody, int currentEntityId) {
        if (!matches(currentEpoch, currentBody, currentEntityId) || endWritten || rows >= MAX_ROWS) {
            active = false; return false;
        }
        rows++; active = false; endWritten = true; return true;
    }
    private boolean addRow() { if (rows >= MAX_ROWS) return false; rows++; return true; }
    private boolean matches(long e, UUID b, int id) { return epoch == e && entityId == id && body != null && body.equals(b); }
    public synchronized boolean active() { return active; }
    public synchronized int rows() { return rows; }
    public synchronized int episodes() { return episodes; }
    public synchronized int remainingTicks(long tick) {
        if (!active || tick < startTick || tick >= startTick + WINDOW_TICKS) return 0;
        return (int)(startTick + WINDOW_TICKS - tick);
    }
    public synchronized void stop() { active = false; }
    public synchronized void reset() {
        epoch = 0; body = null; entityId = -1; episodes = rows = renderRows = prelaunchRows = 0;
        startTick = lastTick = lastRenderTick = Long.MIN_VALUE; active = endWritten = false;
    }
}
