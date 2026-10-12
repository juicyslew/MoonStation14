package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AirlockStateTest {
    @Test void deviceSignalAutoCloseHoldKeepsArmedDeadlineUntilClosedThenResets() {
        var open=AirlockState.initial(UUID.randomUUID(),true,true).withPower(true)
                .open(0,false,.4).tick(8,.2,.2).tick(12,.2,.2,5);
        long armed=open.autoCloseAt();
        var held=open.receiveSignal("AutoClose",com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.LOW);
        assertFalse(held.autoClose());assertEquals(armed,held.autoCloseAt(),"signal does not cancel an armed upstream timer");
        var stillHeld=held.receiveSignal("AutoClose",com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.UNKNOWN);
        assertFalse(stillHeld.autoClose());
        var closed=stillHeld.close(armed,false,.4).tick(armed+8,.2,.2).tick(armed+12,.2,.2,5);
        assertEquals(AirlockState.DoorPhase.CLOSED,closed.phase());assertTrue(closed.autoClose());
    }
    @Test void deviceSignalDoorBoltUsesPowerGateAndHighLowMomentarySemantics() {
        var unpowered=AirlockState.initial(UUID.randomUUID(),true,true);
        assertSame(unpowered,unpowered.receiveSignal("DoorBolt",com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.HIGH));
        var state=unpowered.withPower(true).receiveSignal("DoorBolt",com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.HIGH);
        assertTrue(state.boltsDown());assertFalse(state.canOpenOrClose());
        assertFalse(state.canOpenOrClose());
        state=state.receiveSignal("DoorBolt",com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.MOMENTARY);
        assertFalse(state.boltsDown());
        state=state.receiveSignal("DoorBolt",com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.LOW);
        assertFalse(state.boltsDown());
        state=state.receiveSignal("DoorBolt",com.juicyslew.moonstation14.ms14.devicenet.DeviceSignal.MOMENTARY);
        assertTrue(state.boltsDown());
    }
    @Test void logPulseTimesOutWithoutExtensionAndPowerLossCancelsIntoDisabledLatch() {
        var state=AirlockState.initial(UUID.randomUUID(),true,true).withPower(true);
        state=state.applyWire(AirlockState.Wire.LOG,AirlockState.WireAction.PULSE,10,30,15,.01,.5,30,30,30);
        assertTrue(state.logCircuitDisabled()); assertEquals(610,state.logPulseUntil());
        assertTrue(state.nextDeadline()<=state.logPulseUntil());
        var repeated=state.applyWire(AirlockState.Wire.LOG,AirlockState.WireAction.PULSE,100,30,15,.01,.5,30,30,30);
        assertEquals(610,repeated.logPulseUntil());
        var mendedWhileTimerActive=repeated.applyWire(AirlockState.Wire.LOG,AirlockState.WireAction.MEND,120);
        assertFalse(mendedWhileTimerActive.logCircuitDisabled());
        assertEquals(610,mendedWhileTimerActive.logPulseUntil(),"Mend enables but does not cancel upstream timeout");
        var expired=repeated.tick(610,.2,.2);
        assertFalse(expired.logCircuitDisabled());
        var cut=state.applyWire(AirlockState.Wire.LOG,AirlockState.WireAction.CUT,11);
        assertTrue(cut.logWireCut()); assertTrue(cut.logCircuitDisabled()); assertEquals(0,cut.logPulseUntil());
        var mend=cut.applyWire(AirlockState.Wire.LOG,AirlockState.WireAction.MEND,12);
        assertFalse(mend.logWireCut());assertFalse(mend.logCircuitDisabled());
        var loss=state.withPower(false);
        assertTrue(loss.logCircuitDisabled()); assertEquals(0,loss.logPulseUntil());
        assertTrue(loss.withPower(true).logCircuitDisabled());
        var copied=state.open(20,false,.4).bumpRevision();
        assertTrue(copied.logCircuitDisabled());assertEquals(610,copied.logPulseUntil());
    }
    @Test void powerBoltsAndDoorProgressAreAuthoritative() {
        var state = AirlockState.initial(UUID.randomUUID(), true, true).withPower(true)
                .applyWire(AirlockState.Wire.BOLT, AirlockState.WireAction.PULSE, 0);
        state = state.applyWire(AirlockState.Wire.BOLT, AirlockState.WireAction.PULSE, 1);
        assertTrue(state.powerAvailable());
        var opening = state.open(100, false, .4);
        assertEquals(AirlockState.DoorPhase.OPENING, opening.phase());
        var passable=opening.tick(108,.2,.2);
        assertTrue(passable.doorPassable());
        assertEquals(AirlockState.DoorPhase.OPENING_PASSABLE,passable.phase());
        var open = passable.tick(112, .2, .2);
        assertEquals(AirlockState.DoorPhase.OPEN, open.phase());
        assertEquals(212, open.autoCloseAt());
        var bolted = open.applyWire(AirlockState.Wire.BOLT, AirlockState.WireAction.CUT, 113);
        assertTrue(bolted.boltsDown());
        assertFalse(bolted.canOpenOrClose());
        assertTrue(bolted.revision() > open.revision());
    }
    @Test void safetyDoesNotBlockOpeningAndClosingBecomesAirtightAtFirstPhase() {
        var state=AirlockState.initial(UUID.randomUUID(),true,false).withPower(true)
                .applyWire(AirlockState.Wire.BOLT,AirlockState.WireAction.PULSE,0)
                .applyWire(AirlockState.Wire.BOLT,AirlockState.WireAction.PULSE,1);
        assertEquals(AirlockState.DoorPhase.OPENING,state.open(10,true,.4).phase(),"safety does not deny opening around an obstruction");
        var opening=state.open(10,false,.4).tick(18,.2,.2).tick(22,.2,.2);
        var closing=opening.close(30,false,.4);
        assertTrue(closing.doorPassable());
        var sealed=closing.tick(38,.2,.2);
        assertFalse(sealed.doorPassable());
        assertEquals(AirlockState.DoorPhase.CLOSING_PASSABLE,sealed.phase());
    }
    @Test void powerWiresDisableOnlyWhenBothAreCutAndMendingOneRestores() {
        var state=AirlockState.initial(UUID.randomUUID(),true,true).withPower(true);
        var one=state.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT,0);
        assertTrue(one.powerAvailable());
        var both=one.applyWire(AirlockState.Wire.POWER_TWO,AirlockState.WireAction.CUT,1);
        assertFalse(both.powerAvailable());
        var mend=both.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.MEND,2);
        assertTrue(mend.powerAvailable());
    }
    @Test void powerPulsesShareOneTimerAndMendingDuringPulseLeavesUpstreamLatch() {
        var powered=AirlockState.initial(UUID.randomUUID(),true,true).withPower(true);
        var first=powered.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.PULSE,10);
        assertEquals(610,first.powerPulseUntil());
        var overlapping=first.applyWire(AirlockState.Wire.POWER_TWO,AirlockState.WireAction.PULSE,100);
        assertEquals(610,overlapping.powerPulseUntil());
        assertTrue(overlapping.tick(610,.2,.2).powerAvailable());
        var second=powered.applyWire(AirlockState.Wire.POWER_TWO,AirlockState.WireAction.PULSE,10);
        assertEquals(310,second.powerPulseUntil());
        assertTrue(second.tick(310,.2,.2).powerAvailable());

        var pulsed=powered.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.PULSE,10)
                .applyWire(AirlockState.Wire.POWER_TWO,AirlockState.WireAction.CUT,11)
                .applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.MEND,12);
        assertEquals(0,pulsed.powerPulseUntil());
        assertTrue(pulsed.powerPulseLatched());
        assertFalse(pulsed.powerAvailable());
        assertFalse(pulsed.tick(2000,.2,.2).powerAvailable());
        assertFalse(pulsed.withPower(true).powerAvailable());
    }
    @Test void boltWireRestoresBoltsOnPowerAndOnlyAfterReceiverIsEnabled() {
        var cut=AirlockState.initial(UUID.randomUUID(),true,true)
                .applyWire(AirlockState.Wire.BOLT,AirlockState.WireAction.CUT,0);
        assertFalse(cut.boltsDown());
        assertTrue(cut.withPower(true).boltsDown());
        var powered=cut.withPower(true);
        var powerCut=powered.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT,1)
                .applyWire(AirlockState.Wire.POWER_TWO,AirlockState.WireAction.CUT,2);
        assertTrue(powerCut.boltsDown());
        assertFalse(powerCut.withPower(true).canOpenOrClose());
    }
    @Test void safetyWireDisableIsSeparateFromApcSupply() {
        var state=AirlockState.initial(UUID.randomUUID(),true,true).withPower(true);
        var unsafe=state.applyWire(AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT,3);
        assertEquals(0,unsafe.safetyModifier());
    }
    @Test void resolvedWireParametersDrivePulseAndPowerLossCancelsModifierExpiry() {
        var base=AirlockState.initial(UUID.randomUUID(),true,true).withPower(true);
        var custom=base.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.PULSE,10,7.5,2,0.02,0.25,9,11);
        assertEquals(160,custom.powerPulseUntil());
        var timing=base.applyWire(AirlockState.Wire.TIMING,AirlockState.WireAction.PULSE,10,7.5,2,0.02,0.25,9,11);
        assertEquals(0.25,timing.autoCloseModifier());
        assertEquals(190,timing.timingModifierUntil());
        var lost=timing.withPower(false);
        assertEquals(0,lost.timingModifierUntil());
        assertEquals(0.25,lost.autoCloseModifier());
    }
    @Test void crowbarPryTogglesClosedAndOpenOnlyWithoutPowerOrBolts() {
        var unpowered=AirlockState.initial(UUID.randomUUID(),true,true);
        var opening=unpowered.pry(10,.4);
        assertEquals(AirlockState.DoorPhase.OPENING,opening.phase());
        assertSame(opening,opening.pry(11,.4));
        var open=opening.tick(18,.2,.2).tick(22,.2,.2);
        assertEquals(AirlockState.DoorPhase.OPEN,open.phase());
        var closing=open.pry(30,.4);
        assertEquals(AirlockState.DoorPhase.CLOSING,closing.phase());
        assertTrue(closing.doorPassable());
        assertSame(closing,closing.pry(31,.4));
        assertEquals(AirlockState.DoorPhase.CLOSING_PASSABLE,closing.tick(38,.2,.2).phase());

        var powered=unpowered.withPower(true);
        assertSame(powered,powered.pry(40,.4));
        var bolted=powered.applyWire(AirlockState.Wire.BOLT,AirlockState.WireAction.CUT,41);
        assertTrue(bolted.boltsDown());
        var powerLost=bolted.withPower(false);
        assertSame(powerLost,powerLost.pry(42,.4));
    }
    @Test void partialCloseCanAbortBackOpenAndScheduleSourceOpenTimeTwoRetry() {
        var closing=AirlockState.initial(UUID.randomUUID(),true,false).pry(100,.4).tick(108,.2,.2).tick(112,.2,.2).pry(120,.4);
        var atPartial=closing.tick(128,.2,.2);
        assertEquals(AirlockState.DoorPhase.CLOSING_PASSABLE,atPartial.phase());
        assertFalse(atPartial.doorPassable());
        var aborted=atPartial.afterPartialCloseBlocked(128,.2);
        assertEquals(AirlockState.DoorPhase.OPEN,aborted.phase());
        assertTrue(aborted.doorPassable());
        assertEquals(132,aborted.autoCloseAt());
    }
    @Test void safetyWireMendEnablesInitiallyDisabledSafetyAndPulseExpiryRestoresIt() {
        var initial=AirlockState.initial(UUID.randomUUID(),false,true).withPower(true);
        assertFalse(initial.safetyActive(0));
        var cut=initial.applyWire(AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT,1);
        assertFalse(cut.safetyActive(1));
        var mended=cut.applyWire(AirlockState.Wire.SAFETY,AirlockState.WireAction.MEND,2);
        assertTrue(mended.safety());
        assertTrue(mended.safetyActive(2));

        var pulsed=mended.applyWire(AirlockState.Wire.SAFETY,AirlockState.WireAction.PULSE,10);
        assertFalse(pulsed.safetyActive(10));
        assertTrue(pulsed.tick(610,.2,.2).safetyActive(610));
        var canceled=pulsed.withPower(false);
        assertFalse(canceled.safetyActive(1000),"power loss cancels the restore timer without inventing a callback");
    }
    @Test void effectivePowerLossCancelsAutoCloseAndReceiverRestoreStartsFreshDelay() {
        var open=AirlockState.initial(UUID.randomUUID(),true,true).withPower(true)
                .open(0,false,.4).tick(8,.2,.2).tick(12,.2,.2,5);
        assertEquals(112,open.autoCloseAt());
        var cut=open.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT,13)
                .applyWire(AirlockState.Wire.POWER_TWO,AirlockState.WireAction.CUT,14);
        assertFalse(cut.powerAvailable());
        var disabled=cut.powerAvailabilityChanged(true,false,14,5);
        assertEquals(0,disabled.autoCloseAt());
        assertEquals(0,disabled.autoCloseAt(),"there is no overdue auto-close retry while receiver-disabled");
        var mended=disabled.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.MEND,20);
        assertTrue(mended.powerAvailable());
        var restored=mended.powerAvailabilityChanged(false,false,20,5);
        assertEquals(120,restored.autoCloseAt(),"receiver restoration schedules a fresh configured delay");
    }
    @Test void actualPowerReturnUsesTimingModifierAndDoesNotErasePowerPulseLatch() {
        var open=AirlockState.initial(UUID.randomUUID(),true,true).withPower(true)
                .open(0,false,.4).tick(8,.2,.2).tick(12,.2,.2,5);
        var timing=open.applyWire(AirlockState.Wire.TIMING,AirlockState.WireAction.PULSE,13);
        var lost=timing.withPower(false);
        assertEquals(0,lost.autoCloseAt());
        var returned=lost.withPower(true).powerAvailabilityChanged(false,false,30,5);
        assertEquals(80,returned.autoCloseAt(),"five seconds times .5 timing modifier begins fresh at power return");

        var powerPulse=timing.applyWire(AirlockState.Wire.POWER_ONE,AirlockState.WireAction.PULSE,14);
        long pulseUntil=powerPulse.powerPulseUntil();
        var pulseLoss=powerPulse.withPower(false);
        assertEquals(pulseUntil,pulseLoss.powerPulseUntil());
        assertTrue(pulseLoss.powerPulseLatched());
    }
    @Test void crushReopenRunsBothOpeningPhasesBeforeStartingModifiedAutoClose() {
        var closed=AirlockState.initial(UUID.randomUUID(),false,true).withPower(true)
                .applyWire(AirlockState.Wire.TIMING,AirlockState.WireAction.CUT,1);
        var opening=closed.reopenAfterCrush(100,.4);
        assertEquals(AirlockState.DoorPhase.OPENING,opening.phase());
        assertEquals(108,opening.transitionAt());
        assertEquals(0,opening.autoCloseAt());
        assertFalse(opening.doorPassable());
        var passable=opening.tick(108,.2,.2,5);
        assertEquals(AirlockState.DoorPhase.OPENING_PASSABLE,passable.phase());
        assertEquals(0,passable.autoCloseAt());
        var open=passable.tick(112,.2,.2,5);
        assertEquals(AirlockState.DoorPhase.OPEN,open.phase());
        assertEquals(113,open.autoCloseAt());
        assertEquals(113,open.autoCloseAt(),"the shortened auto-close begins only after full opening");
    }
}
