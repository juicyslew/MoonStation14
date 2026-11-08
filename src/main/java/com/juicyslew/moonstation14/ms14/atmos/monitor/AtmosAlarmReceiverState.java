package com.juicyslew.moonstation14.ms14.atmos.monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-receiver alarm state: network and forced reports share the source-keyed aggregation cache. */
public final class AtmosAlarmReceiverState {
    private final Map<UUID, AtmosAlarmSeverity> reports = new HashMap<>();
    private boolean ignoreAlarms;
    private boolean synchronizationPending = true;
    public void setIgnoreAlarms(boolean ignore) { ignoreAlarms = ignore; }
    public boolean ignoresAlarms() { return ignoreAlarms; }

    public boolean report(UUID source, AtmosAlarmSeverity severity) {
        Objects.requireNonNull(source); Objects.requireNonNull(severity);
        if (ignoreAlarms) return false;
        reports.put(source, severity);
        return true;
    }
    /** Merge an upstream SS14 alarm-state map keyed by its original source identities. */
    public boolean mergeReports(Map<UUID,AtmosAlarmSeverity> incoming) {
        Objects.requireNonNull(incoming);
        if(ignoreAlarms)return false;
        boolean changed=false;
        for(var entry:incoming.entrySet()) {
            if(entry.getKey()==null||entry.getValue()==null)continue;
            changed|=!entry.getValue().equals(reports.put(entry.getKey(),entry.getValue()));
        }
        return changed;
    }
    public boolean remove(UUID source) { return reports.remove(Objects.requireNonNull(source)) != null; }
    public void resetReports() { reports.clear(); }
    public void markPowerUncertain() { synchronizationPending = true; }
    public boolean synchronizationPending() { return synchronizationPending; }
    public void markSyncAccepted() { synchronizationPending = false; }
    public void actualPowerDown() { reports.clear(); synchronizationPending = true; }
    /** @deprecated use actualPowerDown for a confirmed power loss. */
    @Deprecated public void powerDown() { actualPowerDown(); }
    /** Forced reports are keyed to their owning receiver, not an anonymous network source. */
    public void force(UUID owner, AtmosAlarmSeverity severity) {
        reports.put(Objects.requireNonNull(owner), Objects.requireNonNull(severity));
    }
    public void resetForced(UUID owner) { reports.remove(Objects.requireNonNull(owner)); }
    public AtmosAlarmSeverity severity() {
        AtmosAlarmSeverity result = AtmosAlarmSeverity.NORMAL;
        for (AtmosAlarmSeverity report : reports.values()) result = AtmosAlarmSeverity.max(result, report);
        return result;
    }
    public Map<UUID, AtmosAlarmSeverity> reports() { return Map.copyOf(reports); }
}
