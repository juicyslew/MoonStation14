package com.juicyslew.moonstation14.ms14.atmos.monitor;

/** Ordered atmospheric alarm severity; larger values dominate aggregation. */
public enum AtmosAlarmSeverity {
    NORMAL, WARNING, DANGER;

    public static AtmosAlarmSeverity max(AtmosAlarmSeverity left, AtmosAlarmSeverity right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
