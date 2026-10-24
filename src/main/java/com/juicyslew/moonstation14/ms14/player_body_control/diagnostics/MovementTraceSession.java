package com.juicyslew.moonstation14.ms14.player_body_control.diagnostics;

import java.util.Map;

/** Shared opt-in recording handle; client/server call sites exchange only primitive diagnostic values. */
public final class MovementTraceSession {
    private static volatile MovementTraceRecorder active;
    private MovementTraceSession() { }
    public static synchronized boolean start(MovementTraceRecorder.Context context, long now, int seconds) {
        if (active != null) return false;
        active = new MovementTraceRecorder(context, now, seconds, MovementTraceRecorder.MAX_SAMPLES);
        return true;
    }
    public static MovementTraceRecorder.Context activeContext() {
        var recorder = active;
        return recorder == null ? null : recorder.context();
    }
    public static boolean expired(long now) { var r=active; return r != null && r.expired(now); }
    public static boolean record(MovementTraceRecorder.Context context, String kind, long now,
                                 long logicalTick, long sequence, Map<String, ?> values) {
        var recorder = active;
        return recorder != null && recorder.record(context, kind, now, logicalTick, sequence, values);
    }
    public static synchronized MovementTraceRecorder.Trace stop(String reason, String serverStream) {
        var recorder = active;
        if (recorder == null) return null;
        active = null;
        return recorder.stop(reason, serverStream);
    }
}
