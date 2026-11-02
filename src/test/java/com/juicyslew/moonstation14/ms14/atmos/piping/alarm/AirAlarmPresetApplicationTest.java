package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosVentilationReducer;
import static org.junit.jupiter.api.Assertions.*;

class AirAlarmPresetApplicationTest {
    private AirAlarmPresetApplication.Member member() { return new AirAlarmPresetApplication.Member(UUID.randomUUID(), "device-address"); }
    @Test void syncThenSuccessCompletesWithoutSecondCommand() {
        var app = new AirAlarmPresetApplication(); var member = member(); app.begin(List.of(member));
        assertTrue(app.pending(member));
        long id = app.claim(member, 3); assertNotEquals(0, id); assertEquals(0, app.claim(member, 3));
        assertEquals(AirAlarmPresetApplication.Result.COMPLETE, app.result(member, id, true, 4));
        assertFalse(app.pending(member));
    }
    @Test void queuedReducerApplicationAcknowledgesAndDoesNotQueueAnotherCommand() {
        var app = new AirAlarmPresetApplication(); var member = member(); app.begin(List.of(member));
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        var before = config.settings(); long request = app.claim(member, before.revision());
        var command = new AtmosDevicePayload.SetState(before.revision(), false, 100, 0, 100, 0,
                VentilationDeviceConfig.ScrubberMode.FILTER, Set.of(), VentilationDeviceConfig.Layer.PRIMARY,
                false, false, request);
        assertTrue(AtmosVentilationReducer.apply(config, command));
        assertEquals(AirAlarmPresetApplication.Result.COMPLETE,
                app.result(member, command.requestId(), true, config.settings().revision()));
        assertEquals(0, app.claim(member, config.settings().revision()), "acknowledgement must not enqueue another command");
    }
    @Test void onlyStrictlyNewRevisionAllowsOneRetry() {
        var app = new AirAlarmPresetApplication(); var member = member(); app.begin(List.of(member));
        long same = app.claim(member, 4);
        assertEquals(AirAlarmPresetApplication.Result.REJECTED, app.result(member, same, false, 4));
        app.begin(List.of(member)); long old = app.claim(member, 4);
        assertEquals(AirAlarmPresetApplication.Result.RETRY_NEWER_STATE, app.result(member, old, false, 5));
        long retry = app.claim(member, 5);
        assertEquals(AirAlarmPresetApplication.Result.REJECTED, app.result(member, retry, false, 6));
    }
    @Test void generationAndRemovalRejectOldRepliesAndExplicitRepeatIsFresh() {
        var app = new AirAlarmPresetApplication(); var member = member(); app.begin(List.of(member));
        long old = app.claim(member, 1); app.begin(List.of(member)); long current = app.claim(member, 1);
        assertEquals(AirAlarmPresetApplication.Result.IGNORED, app.result(member, old, true, 2));
        app.cancel(member);
        assertEquals(AirAlarmPresetApplication.Result.IGNORED, app.result(member, current, true, 2));
        app.begin(List.of(member)); long fresh = app.claim(member, 1);
        assertNotEquals(current, fresh);
    }
}
