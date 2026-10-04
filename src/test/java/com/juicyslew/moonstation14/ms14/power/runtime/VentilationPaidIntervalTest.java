package com.juicyslew.moonstation14.ms14.power.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class VentilationPaidIntervalTest {
    private final Object vent = new Object();
    private final Object apc = new Object();

    private VentilationPaidInterval.ReceiverSignature signature(long tick) {
        return new VentilationPaidInterval.ReceiverSignature(vent, apc, 4, 9, true, true, true, tick);
    }

    @Test void stableIntervalGrantsOnlyElapsedPaidWorkOnce() {
        var gate = new VentilationPaidInterval();
        var budget = gate.authorizeCompletedInterval(signature(0), signature(20), 5, 20, 10, 2).orElseThrow();
        assertEquals(1, budget.elapsedSeconds());
        assertEquals(5, budget.paidJoules());
        var accounting = budget.consume(1).orElseThrow();
        assertEquals(10, accounting.sourceMeter());
        assertEquals(2, accounting.batteryMeter());
        assertTrue(budget.consume(1).isEmpty());
        assertTrue(budget.consume(0).isEmpty());
        assertTrue(budget.consume(.5).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(signature(0), signature(20), 5, 20).isEmpty());

        var shortBudget = gate.authorizeCompletedInterval(signature(20), signature(22), 5, 2).orElseThrow();
        assertEquals(.1, shortBudget.elapsedSeconds(), 1e-12);
        assertEquals(.5, shortBudget.paidJoules(), 1e-12);
        assertTrue(shortBudget.consume(.11).isEmpty());
        assertTrue(shortBudget.consume(0).isEmpty());
        assertTrue(shortBudget.consume(.1).isPresent());
    }

    @Test void partialDeliveryAndChangedOrUnreadySignaturesFailClosed() {
        var gate = new VentilationPaidInterval();
        var start = signature(0);
        assertTrue(gate.authorizeCompletedInterval(start, signature(20), 4.999, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start,
                new VentilationPaidInterval.ReceiverSignature(new Object(), apc, 4, 9, true, true, true, 20), 5, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start,
                new VentilationPaidInterval.ReceiverSignature(vent, new Object(), 4, 9, true, true, true, 20), 5, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start,
                new VentilationPaidInterval.ReceiverSignature(vent, apc, 5, 9, true, true, true, 20), 5, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start,
                new VentilationPaidInterval.ReceiverSignature(vent, apc, 4, 10, true, true, true, 20), 5, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start,
                new VentilationPaidInterval.ReceiverSignature(vent, apc, 4, 9, true, false, true, 20), 5, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start,
                new VentilationPaidInterval.ReceiverSignature(vent, apc, 4, 9, true, true, false, 20), 5, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start, signature(21), 5, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start, signature(21), 5, 21).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start, signature(20), 5, 0).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(start, signature(21), 5, 21).isEmpty());
    }

    @Test void gasEnablementIsNotPartOfElectricalPaymentSignature() {
        // The pure gate has no gas-enabled flag: a paid receiver remains electrically paid;
        // the caller separately decides whether to perform the gas operation.
        assertTrue(new VentilationPaidInterval().authorizeCompletedInterval(signature(0), signature(20), 5, 20).isPresent());
    }

    @Test void tracksOnlyLatestIntervalPerReceiverAndRejectsReplay() {
        var gate = new VentilationPaidInterval();
        for (long tick = 0; tick < 2000; tick += 20) {
            assertTrue(gate.authorizeCompletedInterval(signature(tick), signature(tick + 20), 5, 20).isPresent());
            assertEquals(1, gate.trackedReceiverCount());
        }
        assertTrue(gate.authorizeCompletedInterval(signature(0), signature(20), 5, 20).isEmpty());
        assertTrue(gate.authorizeCompletedInterval(signature(1980), signature(2000), 5, 20).isEmpty());
    }

    @Test void identityStateIsIndependentAndForgetIsScoped() {
        var gate = new VentilationPaidInterval();
        var otherVent = new Object();
        var otherApc = new Object();
        var otherStart = new VentilationPaidInterval.ReceiverSignature(otherVent, otherApc, 4, 9, true, true, true, 0);
        var otherEnd = new VentilationPaidInterval.ReceiverSignature(otherVent, otherApc, 4, 9, true, true, true, 20);
        assertTrue(gate.authorizeCompletedInterval(signature(0), signature(20), 5, 20).isPresent());
        assertTrue(gate.authorizeCompletedInterval(otherStart, otherEnd, 5, 20).isPresent());
        gate.forget(vent);
        assertEquals(1, gate.trackedReceiverCount());
        assertTrue(gate.authorizeCompletedInterval(signature(0), signature(20), 5, 20).isPresent());
        gate.clear();
        assertEquals(0, gate.trackedReceiverCount());
    }

    @Test void receiverStateLimitFailsClosed() {
        var gate = new VentilationPaidInterval();
        for (int i = 0; i < com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.MAX_INPUT_NODES; i++) {
            Object receiver = new Object();
            var start = new VentilationPaidInterval.ReceiverSignature(receiver, new Object(), 1, 1, true, true, true, 0);
            var end = new VentilationPaidInterval.ReceiverSignature(receiver, start.apcBE(), 1, 1, true, true, true, 2);
            assertTrue(gate.authorizeCompletedInterval(start, end, 5, 2).isPresent());
        }
        var receiver = new Object();
        var start = new VentilationPaidInterval.ReceiverSignature(receiver, new Object(), 1, 1, true, true, true, 0);
        var end = new VentilationPaidInterval.ReceiverSignature(receiver, start.apcBE(), 1, 1, true, true, true, 2);
        assertTrue(gate.authorizeCompletedInterval(start, end, 5, 2).isEmpty());
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.MAX_INPUT_NODES, gate.trackedReceiverCount());
    }
}
