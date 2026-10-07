package com.juicyslew.moonstation14.ms14.timed_interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimedInteractionEngineTest {
    private TimedOperationEngineFixture fixture() { return new TimedOperationEngineFixture(); }
    private static class TimedOperationEngineFixture {
        TimedOperationState state = TimedOperationState.EMPTY;
        TimedOperation start(String kind, String src, long at, int duration, boolean damage, boolean move) {
            var started = TimedInteractionEngine.start(state, kind, src, at, duration, damage, move);
            assertEquals(TimedInteractionEngine.StartResult.STARTED, started.result()); state = started.state(); return started.operation();
        }
    }

    @Test void startsAreImmutableAndDuplicateCancelsWithoutStarting() {
        var f = fixture(); var old = f.start("open", "hand", 4, 8, true, false);
        var duplicate = TimedInteractionEngine.start(f.state, "open", "hand", 6, 8, true, false);
        assertEquals(TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED, duplicate.result()); assertNull(duplicate.operation());
        assertEquals(TimedOperation.Phase.ACTIVE, f.state.operations().get(0).phase());
        var canceled = duplicate.state().operations().get(0);
        assertEquals(TimedOperation.Phase.CANCELLED, canceled.phase()); assertEquals(6, canceled.terminalTick());
        assertEquals(1, duplicate.state().operations().size()); assertEquals(old.id(), canceled.id());
    }

    @Test void advanceCompletesOnceAndRepeatGetsFullPeriod() {
        var f=fixture(); var op=f.start("x","s",3,5,false,false);
        var due=TimedInteractionEngine.advance(f.state,8); assertEquals(1,due.completed().size());
        assertTrue(TimedInteractionEngine.advance(due.state(),100).completed().isEmpty());
        var repeated=TimedInteractionEngine.repeat(due.state(),op.id(),100,5);
        assertEquals(105,repeated.operations().get(0).dueTick());
        assertTrue(TimedInteractionEngine.advance(repeated,104).completed().isEmpty());
        assertEquals(1,TimedInteractionEngine.advance(repeated,105).completed().size());
    }

    @Test void cancellationFlagsAreIndependentAndTerminalCannotBeRevived() {
        var f=fixture(); var damage=f.start("d","a",2,20,true,false); var movement=f.start("m","b",2,20,false,true);
        var afterDamage=TimedInteractionEngine.cancelForDamage(f.state,3);
        assertEquals(TimedOperation.Phase.CANCELLED,afterDamage.operations().get(0).phase());
        assertEquals(TimedOperation.Phase.ACTIVE,afterDamage.operations().get(1).phase());
        var afterMove=TimedInteractionEngine.cancelForMovement(afterDamage,4);
        assertEquals(TimedOperation.Phase.CANCELLED,afterMove.operations().get(1).phase());
        assertEquals(afterMove,TimedInteractionEngine.repeat(afterMove,damage.id(),8,4));
        assertEquals(afterMove,TimedInteractionEngine.cancel(afterMove,movement.id(),9));
    }

    @Test void damageThresholdSelectionIsPerOperationAndInclusive() {
        var f=fixture();
        var drink=f.start("drink","a",2,20,true,false);
        var getup=f.start("getup","b",2,20,true,false);
        var unaffected=f.start("other","c",2,20,false,false);
        var threshold=java.util.Map.of(drink.id(),1.0,getup.id(),5.0,unaffected.id(),1.0);
        java.util.function.ToDoubleFunction<TimedOperation> thresholds=op -> threshold.get(op.id());

        assertEquals(java.util.List.of(drink.id()),TimedInteractionDamageSelector.select(f.state,1,thresholds));
        assertEquals(java.util.List.of(drink.id()),TimedInteractionDamageSelector.select(f.state,4.99,thresholds));
        assertEquals(java.util.List.of(getup.id(),drink.id()).stream().sorted().toList(),
                TimedInteractionDamageSelector.select(f.state,5,thresholds).stream().sorted().toList());
        assertEquals(TimedOperation.Phase.ACTIVE, f.state.operations().get(2).phase());
    }

    @Test void terminalTimePruningAndIndependentKeys() {
        var f=fixture(); var a=f.start("x","a",5,2,false,false); var b=f.start("x","b",5,4,false,false);
        var advanced=TimedInteractionEngine.advance(f.state,7);
        assertEquals(a.id(),advanced.completed().get(0).id()); assertEquals(TimedOperation.Phase.ACTIVE,advanced.state().operations().get(1).phase());
        assertEquals(7,advanced.state().operations().get(0).terminalTick());
        assertEquals(2,TimedInteractionEngine.prune(advanced.state(),16).operations().size());
        assertEquals(1,TimedInteractionEngine.prune(advanced.state(),17).operations().size());
    }

    @Test void invalidTimingAndMalformedTerminalRecordsAreRejected() {
        assertEquals(TimedInteractionEngine.StartResult.DENIED,TimedInteractionEngine.start(TimedOperationState.EMPTY,"x","s",-1,2,false,false).result());
        assertThrows(IllegalArgumentException.class,()->new TimedOperation(1,"x","s",1,2,TimedOperation.Phase.ACTIVE,1,false,false));
        assertThrows(IllegalArgumentException.class,()->TimedInteractionEngine.cancelForDamage(
                TimedInteractionEngine.start(TimedOperationState.EMPTY,"x","s",5,2,true,false).state(),4));
        assertThrows(ArithmeticException.class,()->TimedInteractionEngine.start(TimedOperationState.EMPTY,"x","s",Long.MAX_VALUE,1,false,false));
    }
}
