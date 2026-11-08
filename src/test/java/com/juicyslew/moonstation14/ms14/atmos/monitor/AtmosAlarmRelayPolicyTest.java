package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AtmosAlarmRelayPolicyTest {
    @Test void powerUncertaintyRetainsReportsUntilAcceptedSyncAndPowerDownClearsThem() {
        var state = new AtmosAlarmReceiverState(); var source = UUID.randomUUID();
        assertTrue(state.synchronizationPending());
        state.markSyncAccepted();
        assertFalse(state.synchronizationPending());
        state.report(source, AtmosAlarmSeverity.DANGER);
        state.markPowerUncertain();
        assertEquals(AtmosAlarmSeverity.DANGER, state.severity());
        assertTrue(state.reports().containsKey(source));
        assertTrue(state.synchronizationPending());
        state.markSyncAccepted();
        assertFalse(state.synchronizationPending(), "accepted sync clears intent, not reports");
        state.markPowerUncertain();
        assertTrue(state.synchronizationPending(), "only a new uncertainty creates another sync intent");
        state.actualPowerDown();
        assertEquals(AtmosAlarmSeverity.NORMAL, state.severity());
        assertTrue(state.synchronizationPending(), "power restoration still requires synchronization");
    }
    @Test void relaysAggregateBySourceAndRequireExactResolvedTagsAndReceiverFilter() {
        var state=new AtmosAlarmReceiverState(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var tags=Set.of("AirAlarm"); var filter=Set.of("AirAlarm","FireAlarm");
        assertTrue(AtmosAlarmReportPolicy.applyRelay(state,a,filter,tags,new AtmosAlarmPayload.Relay(a,java.util.Map.of(a,AtmosAlarmSeverity.WARNING),tags)));
        assertTrue(AtmosAlarmReportPolicy.applyRelay(state,b,filter,tags,new AtmosAlarmPayload.Relay(b,java.util.Map.of(a,AtmosAlarmSeverity.WARNING,b,AtmosAlarmSeverity.DANGER),tags)));
        assertEquals(AtmosAlarmSeverity.DANGER,state.severity());
        assertFalse(AtmosAlarmReportPolicy.applyRelay(state,a,filter,tags,new AtmosAlarmPayload.Relay(a,java.util.Map.of(a,AtmosAlarmSeverity.NORMAL),Set.of("GasVent"))));
        assertFalse(AtmosAlarmReportPolicy.applyRelay(state,a,Set.of("GasVent"),tags,new AtmosAlarmPayload.Relay(a,java.util.Map.of(a,AtmosAlarmSeverity.NORMAL),tags)));
        assertFalse(AtmosAlarmReportPolicy.applyRelay(state,a,filter,tags,new AtmosAlarmPayload.Relay(b,java.util.Map.of(b,AtmosAlarmSeverity.NORMAL),tags)));
        assertTrue(AtmosAlarmReportPolicy.applyRelay(state,b,filter,tags,new AtmosAlarmPayload.Relay(b,java.util.Map.of(a,AtmosAlarmSeverity.WARNING,b,AtmosAlarmSeverity.NORMAL),tags)));
        assertEquals(AtmosAlarmSeverity.WARNING,state.severity());
        assertTrue(state.report(UUID.randomUUID(),AtmosAlarmSeverity.DANGER));var unrelatedDanger=state.reports().keySet().stream().filter(id->!id.equals(a)&&!id.equals(b)).findFirst().orElseThrow();
        assertTrue(AtmosAlarmReportPolicy.applyRelay(state,b,filter,tags,new AtmosAlarmPayload.RelayRemove(b,a,tags)));
        assertEquals(AtmosAlarmSeverity.DANGER,state.severity(),"source reset must not erase another active report");
        assertTrue(state.reports().containsKey(unrelatedDanger));
        assertTrue(AtmosAlarmReportPolicy.applyRelay(state,b,filter,tags,new AtmosAlarmPayload.RelayReset(b)));
        assertEquals(AtmosAlarmSeverity.NORMAL,state.severity());
        assertFalse(AtmosAlarmReportPolicy.applyRelay(state,b,filter,tags,new AtmosAlarmPayload.Measurement(
                AtmosAlarmSeverity.DANGER,0,0,0,tags,true,false)));
    }
}
