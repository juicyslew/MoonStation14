package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirAlarmValidityReasonTest {
    @Test void reasonsRemainDistinctAndConcise() {
        assertEquals("actor/body authority mismatch", AirAlarmMenuService.reason(AirAlarmMenuService.Validity.AUTHORITY));
        assertEquals("alarm unavailable", AirAlarmMenuService.reason(AirAlarmMenuService.Validity.ALARM));
        assertEquals("power reading unknown", AirAlarmMenuService.reason(AirAlarmMenuService.Validity.POWER_UNKNOWN));
        assertEquals("power incomplete", AirAlarmMenuService.reason(AirAlarmMenuService.Validity.POWER_INCOMPLETE));
        assertEquals("body out of reach", AirAlarmMenuService.reason(AirAlarmMenuService.Validity.OUT_OF_REACH));
        assertEquals("body path unloaded", AirAlarmMenuService.reason(AirAlarmMenuService.Validity.PATH_UNLOADED));
        assertEquals("line of sight blocked", AirAlarmMenuService.reason(AirAlarmMenuService.Validity.LOS_BLOCKED));
    }
}
