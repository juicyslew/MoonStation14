package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload;
import java.util.Set;
import java.util.UUID;

/** Receiver-side source-tag filter and per-source cache update, used by the alarm endpoint. */
public final class AtmosAlarmReportPolicy {
    private AtmosAlarmReportPolicy() { }
    public static boolean apply(AtmosAlarmReceiverState state, UUID source, Set<String> syncWith,
                                Set<String> resolvedSourceTags, AtmosAlarmPayload payload) {
        if (state == null || source == null || syncWith == null || resolvedSourceTags == null || payload == null) return false;
        if (java.util.Collections.disjoint(syncWith,resolvedSourceTags)) return false;
        if (payload instanceof AtmosAlarmPayload.Reset) { state.remove(source); return true; }
        if (!(payload instanceof AtmosAlarmPayload.Measurement measurement)
                || !measurement.sourceTags().equals(resolvedSourceTags)) return false;
        if (measurement.fireEvidence()) return state.report(source, AtmosAlarmSeverity.DANGER);
        if (measurement.valid()) return state.report(source, measurement.severity());
        // Unavailable/provisional observations are not Normal and do not erase last valid truth.
        return true;
    }

    /** Validates relayed alarm state against resolved source composition without treating it as sensor evidence. */
    public static boolean applyRelay(AtmosAlarmReceiverState state, UUID source, Set<String> syncWith,
                                     Set<String> resolvedSourceTags, AtmosAlarmPayload payload) {
        if(state==null||state.ignoresAlarms()||source==null||syncWith==null||resolvedSourceTags==null||payload==null
                ||resolvedSourceTags.isEmpty()||java.util.Collections.disjoint(syncWith,resolvedSourceTags))return false;
        if(payload instanceof AtmosAlarmPayload.Relay relay) {
            if(!source.equals(relay.source())||!resolvedSourceTags.equals(relay.sourceTags()))return false;
            return state.mergeReports(relay.reports());
        }
        if(payload instanceof AtmosAlarmPayload.RelayRemove remove) {
            if(!source.equals(remove.source())||!resolvedSourceTags.equals(remove.sourceTags()))return false;
            return state.remove(remove.removedSource());
        }
        if(payload instanceof AtmosAlarmPayload.RelayReset reset&&source.equals(reset.source())) { state.resetReports(); return true; }
        return false;
    }
}
