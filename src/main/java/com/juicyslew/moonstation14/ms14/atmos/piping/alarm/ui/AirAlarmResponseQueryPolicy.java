package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

/** Decides whether a response should trigger the immediate post-command refresh. */
public final class AirAlarmResponseQueryPolicy {
    private AirAlarmResponseQueryPolicy() { }

    public static boolean shouldQuery(boolean currentQuery, boolean matchedPendingCommand) {
        return !currentQuery && matchedPendingCommand;
    }
}

