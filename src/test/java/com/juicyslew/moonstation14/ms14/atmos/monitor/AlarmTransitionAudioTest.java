package com.juicyslew.moonstation14.ms14.atmos.monitor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlarmTransitionAudioTest {
    @Test void onlyPoweredServerEntryIntoDangerPlays() {
        assertTrue(AlarmTransitionAudio.shouldPlay(AtmosAlarmSeverity.WARNING, AtmosAlarmSeverity.DANGER, true, true));
        assertTrue(AlarmTransitionAudio.shouldPlay(AtmosAlarmSeverity.NORMAL, AtmosAlarmSeverity.DANGER, true, true));
        assertFalse(AlarmTransitionAudio.shouldPlay(AtmosAlarmSeverity.DANGER, AtmosAlarmSeverity.DANGER, true, true));
        assertFalse(AlarmTransitionAudio.shouldPlay(AtmosAlarmSeverity.NORMAL, AtmosAlarmSeverity.WARNING, true, true));
        assertFalse(AlarmTransitionAudio.shouldPlay(AtmosAlarmSeverity.NORMAL, AtmosAlarmSeverity.DANGER, false, true));
        assertFalse(AlarmTransitionAudio.shouldPlay(AtmosAlarmSeverity.NORMAL, AtmosAlarmSeverity.DANGER, true, false));
    }
}
