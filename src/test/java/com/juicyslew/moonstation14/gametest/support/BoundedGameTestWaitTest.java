package com.juicyslew.moonstation14.gametest.support;

import static org.mockito.Mockito.*;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import org.junit.jupiter.api.Test;

class BoundedGameTestWaitTest {
    @Test void safeOnEachTickRegistersFromSequencePhase() {
        GameTestHelper helper = mock(GameTestHelper.class);
        GameTestSequence sequence = mock(GameTestSequence.class);
        Runnable[] sequenceTask = new Runnable[1];
        Runnable observer = mock(Runnable.class);
        when(helper.startSequence()).thenReturn(sequence);
        when(sequence.thenExecute(any(Runnable.class))).thenAnswer(invocation -> {
            sequenceTask[0] = invocation.getArgument(0);
            return sequence;
        });

        BoundedGameTestWait.safeOnEachTick(helper, observer);

        verify(helper, never()).onEachTick(any(Runnable.class));
        sequenceTask[0].run();
        verify(helper, times(1)).onEachTick(observer);
    }
}
